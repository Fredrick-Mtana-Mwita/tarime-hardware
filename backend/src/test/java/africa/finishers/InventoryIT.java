package africa.finishers;

import africa.finishers.identity.application.*;
import africa.finishers.identity.domain.StaffRole;
import africa.finishers.inventory.application.BranchInventory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryIT {
    @LocalServerPort int port;
    @Autowired BranchInventory inventory;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager tx;
    static final String PASSWORD="Inventory integration password 2026!";
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        FoundationIT.isolatedDatabase(registry);
        registry.add("FPA_STAFF_MFA_KEY",()->Base64.getEncoder().encodeToString(new byte[32]));
    }
    StaffPrincipal actor(StaffRole role){
        UUID id=UUID.randomUUID();String name="stock-"+id;
        jdbc.update("INSERT INTO app.staff_accounts(id,username,name,role,password_hash,password_change_required,mfa_secret_encrypted) VALUES(?,?,?,?,?,false,?)",id,name,"Stock tester",role.name(),passwords.encode(PASSWORD),role==StaffRole.OWNER?"test-encrypted-placeholder":null);
        return new StaffPrincipal(id,name,"Stock tester",role,0,false);
    }
    UUID variant(boolean fractional){
        UUID category=UUID.randomUUID(),product=UUID.randomUUID(),variant=UUID.randomUUID();String unit="u-"+variant.toString().substring(0,15);
        jdbc.update("INSERT INTO app.categories(id,slug,name) VALUES(?,?,?)",category,"stock-"+category,"Inventory test");
        jdbc.update("INSERT INTO app.units(code,label,fractional) VALUES(?,?,?)",unit,"Test units",fractional);
        jdbc.update("INSERT INTO app.products(id,sku,slug,category_id,name,description) VALUES(?,?,?,?,?,?)",product,"stock-"+product,"stock-"+product,category,"Receipt material "+product,"Test-only material");
        jdbc.update("INSERT INTO app.product_variants(id,product_id,sku,label,unit_code,unit_price,currency) VALUES(?,?,?,?,?,10,'TZS')",variant,product,"stock-"+variant,"Standard",unit);
        jdbc.update("INSERT INTO app.stock_balances(variant_id,branch_code,on_hand,reserved) VALUES(?,'TARIME',10,4)",variant);
        return variant;
    }
    BranchInventory.Receive request(UUID id,UUID variant,String branch,String quantity){return new BranchInventory.Receive(id,variant,branch,new BigDecimal(quantity),"Supplier delivery 123","New materials checked and accepted");}
    BigDecimal balance(UUID variant,String branch,String column){return jdbc.queryForObject("SELECT "+column+" FROM app.stock_balances WHERE variant_id=? AND branch_code=?",BigDecimal.class,variant,branch);}
    @Test void receiptPreservesReservationsAndDoesNotChangeOtherBranch(){
        var actor=actor(StaffRole.INVENTORY);var variant=variant(false);var request=request(UUID.randomUUID(),variant,"TARIME","7");
        var receipt=inventory.receive(actor,request);
        assertThat(receipt.onHandAfter()).isEqualByComparingTo("17");assertThat(receipt.reservedAfter()).isEqualByComparingTo("4");
        assertThat(inventory.receive(actor,request).id()).isEqualTo(receipt.id());
        assertThat(balance(variant,"TARIME","on_hand")).isEqualByComparingTo("17");
        inventory.receive(actor,request(UUID.randomUUID(),variant,"MWANZA","3"));
        assertThat(balance(variant,"MWANZA","on_hand")).isEqualByComparingTo("3");assertThat(balance(variant,"MWANZA","reserved")).isZero();
        assertThat(balance(variant,"TARIME","on_hand")).isEqualByComparingTo("17");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.audit_events WHERE target_reference=?",Long.class,receipt.id().toString())).isEqualTo(1);
        assertThat(inventory.history(actor,"TARIME",0,48).items()).anyMatch(r->r.id().equals(receipt.id()));
    }
    @Test void duplicateConcurrentReceiptsAddStockOnceAndRejectChangedPayload() throws Exception {
        var actor=actor(StaffRole.INVENTORY);var variant=variant(false);var request=request(UUID.randomUUID(),variant,"MWANZA","5");
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{start.await();return inventory.receive(actor,request);});
            var b=pool.submit(()->{start.await();return inventory.receive(actor,request);});start.countDown();
            assertThat(a.get(30,TimeUnit.SECONDS).id()).isEqualTo(b.get(30,TimeUnit.SECONDS).id());
        }
        assertThat(balance(variant,"MWANZA","on_hand")).isEqualByComparingTo("5");
        assertThatThrownBy(()->inventory.receive(actor,request(request.id(),variant,"MWANZA","6"))).isInstanceOf(AuthProblem.class).hasMessageContaining("different details");
    }
    @Test void concurrentDistinctReceiptsAccumulateWithoutLostUpdates() throws Exception {
        var actor=actor(StaffRole.INVENTORY);var variant=variant(false);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{start.await();return inventory.receive(actor,request(UUID.randomUUID(),variant,"MWANZA","5"));});
            var b=pool.submit(()->{start.await();return inventory.receive(actor,request(UUID.randomUUID(),variant,"MWANZA","7"));});start.countDown();a.get(30,TimeUnit.SECONDS);b.get(30,TimeUnit.SECONDS);
        }
        assertThat(balance(variant,"MWANZA","on_hand")).isEqualByComparingTo("12");
    }
    @Test void unitBranchAndRoleValidationPreventsAnyMovement(){
        var actor=actor(StaffRole.INVENTORY);var variant=variant(false);
        for(String qty:List.of("0","-1","1.5","0.0001","1000000000"))assertThatThrownBy(()->inventory.receive(actor,request(UUID.randomUUID(),variant,"TARIME",qty))).isInstanceOf(AuthProblem.class);
        assertThatThrownBy(()->inventory.receive(actor,request(UUID.randomUUID(),variant,"INVALID","1"))).isInstanceOf(AuthProblem.class);
        for(var role:List.of(StaffRole.SALES,StaffRole.DELIVERY)){var denied=actor(role);assertThatThrownBy(()->inventory.receive(denied,request(UUID.randomUUID(),variant,"TARIME","1"))).isInstanceOf(AuthProblem.class);assertThatThrownBy(()->inventory.list(denied,"TARIME","",0,12)).isInstanceOf(AuthProblem.class);}
        var owner=actor(StaffRole.OWNER);assertThat(inventory.receive(owner,request(UUID.randomUUID(),variant,"TARIME","1")).onHandAfter()).isEqualByComparingTo("11");
        UUID fractional=variant(true);assertThat(inventory.receive(actor,request(UUID.randomUUID(),fractional,"MWANZA","1.125")).quantity()).isEqualByComparingTo("1.125");
    }
    @Test void rollbackLeavesNoReceiptMovementOrAuditAndHistoryCannotBeMutated(){
        var actor=actor(StaffRole.INVENTORY);var variant=variant(false);var request=request(UUID.randomUUID(),variant,"TARIME","5");
        new TransactionTemplate(tx).executeWithoutResult(status->{inventory.receive(actor,request);status.setRollbackOnly();});
        assertThat(balance(variant,"TARIME","on_hand")).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.stock_receipts WHERE id=?",Long.class,request.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.audit_events WHERE target_reference=?",Long.class,request.id().toString())).isZero();
        inventory.receive(actor,request);
        assertThatThrownBy(()->jdbc.update("DELETE FROM app.stock_receipts WHERE id=?",request.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void realHttpRequiresStaffRolesAndCsrf() throws Exception {
        var actor=actor(StaffRole.INVENTORY);UUID variant=variant(false);
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        String base="http://127.0.0.1:"+port;
        var guest=client.send(HttpRequest.newBuilder(URI.create(base+"/api/staff/inventory?branch=TARIME")).build(),HttpResponse.BodyHandlers.ofString());assertThat(guest.statusCode()).isEqualTo(401);
        var csrf=client.send(HttpRequest.newBuilder(URI.create(base+"/api/security/csrf")).build(),HttpResponse.BodyHandlers.ofString());String token=json.readTree(csrf.body()).get("token").asText();
        var login=client.send(HttpRequest.newBuilder(URI.create(base+"/api/staff/auth/login")).header("Content-Type","application/json").header("X-CSRF-TOKEN",token).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("username",actor.username(),"password",PASSWORD)))).build(),HttpResponse.BodyHandlers.ofString());assertThat(login.statusCode()).isEqualTo(200);
        String body=json.writeValueAsString(request(UUID.randomUUID(),variant,"MWANZA","2"));
        var denied=client.send(HttpRequest.newBuilder(URI.create(base+"/api/staff/inventory/receipts")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());assertThat(denied.statusCode()).isEqualTo(403);
        csrf=client.send(HttpRequest.newBuilder(URI.create(base+"/api/security/csrf")).build(),HttpResponse.BodyHandlers.ofString());token=json.readTree(csrf.body()).get("token").asText();
        var ok=client.send(HttpRequest.newBuilder(URI.create(base+"/api/staff/inventory/receipts")).header("Content-Type","application/json").header("X-CSRF-TOKEN",token).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());assertThat(ok.statusCode()).isEqualTo(200);assertThat(ok.body()).contains("MWANZA");
        assertThat(balance(variant,"MWANZA","on_hand")).isEqualByComparingTo("2");
        jdbc.update("UPDATE app.staff_accounts SET role='SALES',session_version=session_version+1 WHERE id=?",actor.id());
        assertThat(client.send(HttpRequest.newBuilder(URI.create(base+"/api/staff/inventory?branch=MWANZA")).build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isIn(401,403);
    }
}
