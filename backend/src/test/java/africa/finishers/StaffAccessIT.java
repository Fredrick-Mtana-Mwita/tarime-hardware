package africa.finishers;

import africa.finishers.identity.application.*;
import africa.finishers.identity.domain.*;
import africa.finishers.identity.infrastructure.StaffSecretVault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StaffAccessIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired StaffSecretVault vault;
    @Autowired StaffAccounts staff;
    @Autowired Clock clock;
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final String PASSWORD="Staff integration passphrase 2026!",CHANGED="A new private staff passphrase 2026!";
    @DynamicPropertySource static void isolated(DynamicPropertyRegistry registry) throws Exception {
        FoundationIT.isolatedDatabase(registry);
        registry.add("FPA_STAFF_MFA_KEY",()->Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("FPA_STAFF_RECOVERY_ENABLED",()->"true");
    }
    @BeforeEach void resetThrottle(){jdbc.update("DELETE FROM app.auth_rate_limits");}
    record Fixture(UUID id,String username,StaffRole role,byte[] secret) {}
    Fixture fixture(StaffRole role,boolean temporary){
        UUID id=UUID.randomUUID();String username="test-"+id;byte[] secret=role==StaffRole.OWNER?Totp.newSecret():null;
        jdbc.update("INSERT INTO app.staff_accounts(id,username,name,role,password_hash,password_change_required,mfa_secret_encrypted) VALUES(?,?,?,?,?,?,?)",id,username,"Test "+role,role.name(),passwords.encode(PASSWORD),temporary,secret==null?null:vault.encrypt(id,secret));
        return new Fixture(id,username,role,secret);
    }
    String code(Fixture f){return f.secret()==null?"":Totp.code(f.secret(),clock.instant().getEpochSecond()/30,6);}
    class Browser {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient http=HttpClient.newBuilder().cookieHandler(cookies).build();String csrf;
        HttpResponse<String> get(String path) throws Exception {return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
        void token() throws Exception {csrf=JSON.readTree(get("/api/security/csrf").body()).get("token").asText();}
        HttpResponse<String> write(String path,Object body,boolean token,String method) throws Exception {
            var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json");if(token)r.header("X-CSRF-TOKEN",csrf);
            return http.send(r.method(method,HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> post(String path,Object body) throws Exception {return write(path,body,true,"POST");}
        HttpResponse<String> login(Fixture f,String verification) throws Exception {token();var result=post("/api/staff/auth/login",Map.of("username",f.username(),"password",PASSWORD,"code",verification));if(result.statusCode()==200)token();return result;}
        String sessionId(){return cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();}
    }
    @Test void ownerRequiresMfaRotatesSessionAndCannotReplayAnAuthenticatorCode() throws Exception {
        var owner=fixture(StaffRole.OWNER,false);var browser=new Browser();browser.token();String before=browser.sessionId();
        assertThat(browser.write("/api/staff/auth/login",Map.of("username",owner.username(),"password",PASSWORD),false,"POST").statusCode()).isEqualTo(403);
        assertThat(browser.post("/api/staff/auth/login",Map.of("username",owner.username(),"password",PASSWORD)).statusCode()).isEqualTo(401);
        String otp=code(owner);var login=browser.login(owner,otp);assertThat(login.statusCode()).isEqualTo(200);assertThat(browser.sessionId()).isNotEqualTo(before);
        assertThat(login.body()).doesNotContain(PASSWORD,"mfaSecret","passwordHash","encryptedSecret","version");
        assertThat(browser.get("/api/staff/accounts").statusCode()).isEqualTo(200);
        assertThat(new Browser().login(owner,otp).statusCode()).isEqualTo(401);
        assertThat(browser.post("/api/staff/auth/logout",Map.of()).statusCode()).isEqualTo(204);
        assertThat(browser.get("/api/staff/me").statusCode()).isEqualTo(401);
    }
    @Test void allFourRolesHaveExplicitDirectApiBoundaries() throws Exception {
        var guest=new Browser();assertThat(guest.get("/api/staff/orders").statusCode()).isEqualTo(401);
        for(var role:StaffRole.values()){
            var f=fixture(role,false);var b=new Browser();assertThat(b.login(f,code(f)).statusCode()).isEqualTo(200);
            assertThat(b.get("/api/staff/me").statusCode()).isEqualTo(200);
            int review=role==StaffRole.OWNER||role==StaffRole.SALES?200:403;
            assertThat(b.get("/api/staff/orders").statusCode()).isEqualTo(review);
            assertThat(b.get("/api/staff/notifications").statusCode()).isEqualTo(review);
            assertThat(b.get("/api/staff/accounts").statusCode()).isEqualTo(role==StaffRole.OWNER?200:403);
            assertThat(b.get("/api/orders").statusCode()).isEqualTo(403);
            assertThat(b.post("/api/products",Map.of("name","forbidden")).statusCode()).isEqualTo(403);
            assertThat(b.post("/api/staff/orders/"+UUID.randomUUID()+"/confirm",Map.of()).statusCode()).isEqualTo(review==200?400:403);
        }
        // A second driver has exactly the same denial; no unimplemented assignment route grants access.
        var driver=new Browser();var second=fixture(StaffRole.DELIVERY,false);assertThat(driver.login(second,"").statusCode()).isEqualTo(200);
        assertThat(driver.get("/api/staff/orders/"+UUID.randomUUID()).statusCode()).isEqualTo(403);
    }
    @Test void publicCustomerRegistrationCannotGrantStaffAuthority() throws Exception {
        var customer=new Browser();customer.token();var data=new HashMap<String,Object>(CustomerAuthIT.registration(CustomerAuthIT.phone()));data.put("role","OWNER");data.put("staffId",UUID.randomUUID());
        assertThat(customer.post("/api/auth/register",data).statusCode()).isEqualTo(201);customer.token();
        assertThat(customer.get("/api/staff/me").statusCode()).isEqualTo(403);assertThat(customer.get("/api/staff/orders").statusCode()).isEqualTo(403);assertThat(customer.get("/api/staff/accounts").statusCode()).isEqualTo(403);
    }
    @Test void temporaryPasswordCannotReachToolsAndChangingItRevokesOtherSessions() throws Exception {
        var sales=fixture(StaffRole.SALES,true);var a=new Browser();var b=new Browser();
        assertThat(a.login(sales,"").statusCode()).isEqualTo(200);assertThat(b.login(sales,"").statusCode()).isEqualTo(200);
        assertThat(a.get("/api/staff/orders").statusCode()).isEqualTo(403);assertThat(a.get("/api/staff/notifications").statusCode()).isEqualTo(403);
        String stale=a.csrf,before=a.sessionId();
        var changed=a.post("/api/staff/auth/password",Map.of("currentPassword",PASSWORD,"newPassword",CHANGED));assertThat(changed.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(changed.body()).get("passwordChangeRequired").asBoolean()).isFalse();assertThat(a.sessionId()).isNotEqualTo(before);
        assertThat(a.get("/api/staff/orders").statusCode()).isEqualTo(200);
        assertThat(b.get("/api/staff/me").statusCode()).isEqualTo(401);
        a.csrf=stale;assertThat(a.post("/api/staff/auth/logout",Map.of()).statusCode()).isEqualTo(403);
        String stored=jdbc.queryForObject("SELECT password_hash FROM app.staff_accounts WHERE id=?",String.class,sales.id());assertThat(passwords.matches(CHANGED,stored)).isTrue();assertThat(passwords.matches(PASSWORD,stored)).isFalse();
    }
    @Test void ownerManagesNamedAccountsButCannotPromoteWithoutProtectedOwnerEnrollment() throws Exception {
        var f=fixture(StaffRole.OWNER,false);var owner=new Browser();assertThat(owner.login(f,code(f)).statusCode()).isEqualTo(200);
        String username="new-"+UUID.randomUUID();var body=Map.of("username",username,"name","Named Sales","role","SALES","temporaryPassword",PASSWORD);
        var created=owner.post("/api/staff/accounts",body);assertThat(created.statusCode()).isEqualTo(201);assertThat(created.body()).doesNotContain(PASSWORD,"password_hash","mfa_secret","code_hash");
        var data=JSON.readTree(created.body());String id=data.get("profile").get("id").asText();long revision=data.get("revision").asLong();
        assertThat(data.get("profile").get("passwordChangeRequired").asBoolean()).isTrue();
        assertThat(owner.post("/api/staff/accounts",body).statusCode()).isEqualTo(409);
        var promote=Map.of("revision",revision,"name","Named Sales","role","OWNER","active",true);
        assertThat(owner.write("/api/staff/accounts/"+id,promote,true,"PUT").statusCode()).isEqualTo(409);
        var access=Map.of("revision",revision,"name","Named Inventory","role","INVENTORY","active",false);
        assertThat(owner.write("/api/staff/accounts/"+id,access,true,"PUT").statusCode()).isEqualTo(200);
        assertThat(owner.write("/api/staff/accounts/"+id,access,true,"PUT").statusCode()).isEqualTo(409);
        var self=Map.of("revision",0,"name","Owner","role","SALES","active",false);
        assertThat(owner.write("/api/staff/accounts/"+f.id(),self,true,"PUT").statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.audit_events WHERE target_reference=? AND event_type='STAFF_ACCESS_CHANGED'",Integer.class,id)).isEqualTo(1);
        var sales=new Browser();var sf=fixture(StaffRole.SALES,false);sales.login(sf,"");assertThat(sales.post("/api/staff/accounts",body).statusCode()).isEqualTo(403);
    }
    @Test void roleChangesAndDeactivationInvalidateAlreadySignedInStaff() throws Exception {
        var sales=fixture(StaffRole.SALES,false);var browser=new Browser();assertThat(browser.login(sales,"").statusCode()).isEqualTo(200);
        jdbc.update("UPDATE app.staff_accounts SET role='DELIVERY',session_version=session_version+1 WHERE id=?",sales.id());
        assertThat(browser.get("/api/staff/orders").statusCode()).isEqualTo(401);assertThat(browser.get("/api/staff/me").statusCode()).isEqualTo(401);
        assertThat(browser.login(sales,"").statusCode()).isEqualTo(200);assertThat(browser.get("/api/staff/orders").statusCode()).isEqualTo(403);
        jdbc.update("UPDATE app.staff_accounts SET active=false WHERE id=?",sales.id());assertThat(browser.get("/api/staff/me").statusCode()).isEqualTo(401);assertThat(browser.login(sales,"").statusCode()).isEqualTo(401);
    }
    @Test void recoveryIsSingleUseExpiringAndSafeUnderConcurrentAttempts() throws Exception {
        var owner=fixture(StaffRole.OWNER,false);String code=RecoveryCodes.generate().getFirst();
        jdbc.update("INSERT INTO app.staff_recovery_codes(staff_id,code_hash,expires_at) VALUES(?,?,now()+interval '1 day')",owner.id(),RecoveryCodes.hash(code));
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<Boolean> login=()->{start.await(5,TimeUnit.SECONDS);try{staff.login(owner.username(),PASSWORD,code);return true;}catch(AuthProblem e){assertThat(e.status()).isEqualTo(401);return false;}};
            var a=pool.submit(login);var b=pool.submit(login);start.countDown();assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.audit_events WHERE actor_id=? AND event_type='STAFF_RECOVERY_CODE_USED'",Integer.class,owner.id())).isEqualTo(1);
        String expired=RecoveryCodes.generate().getFirst();jdbc.update("INSERT INTO app.staff_recovery_codes(staff_id,code_hash,expires_at) VALUES(?,?,now()-interval '1 day')",owner.id(),RecoveryCodes.hash(expired));
        assertThatThrownBy(()->staff.login(owner.username(),PASSWORD,expired)).isInstanceOf(AuthProblem.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.staff_recovery_codes WHERE staff_id=? AND used_at IS NOT NULL",Integer.class,owner.id())).isEqualTo(1);
    }
    @Test void loginAbuseControlsAreSharedAndCannotBeBypassedByChangingUsernameCase() throws Exception {
        var f=fixture(StaffRole.SALES,false);var b=new Browser();b.token();
        for(int i=0;i<10;i++)assertThat(b.post("/api/staff/auth/login",Map.of("username",i%2==0?f.username():f.username().toUpperCase(),"password","wrong")).statusCode()).isEqualTo(401);
        assertThat(b.post("/api/staff/auth/login",Map.of("username",f.username(),"password",PASSWORD)).statusCode()).isEqualTo(429);
    }
    UUID orderFixture(String name,String fulfilment,String status){
        UUID id=UUID.randomUUID(),customer=UUID.randomUUID(),category=UUID.randomUUID(),product=UUID.randomUUID(),variant=UUID.randomUUID();
        jdbc.update("INSERT INTO app.customer_accounts(id,name,phone,password_hash) VALUES(?,?,?,'not-a-login-hash')",customer,name,CustomerAuthIT.phone());
        jdbc.update("INSERT INTO app.categories(id,slug,name,published) VALUES(?,?,'Archived test category',false)",category,"staff-test-"+category);
        jdbc.update("INSERT INTO app.products(id,sku,slug,category_id,name,description) VALUES(?,?,?,?,?,'Test fixture')",product,"STAFF-"+product,"staff-"+product,category,"Review material snapshot");
        jdbc.update("INSERT INTO app.units(code,label) VALUES('staff-test-unit','piece') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO app.product_variants(id,product_id,sku,label,unit_code,unit_price,currency) VALUES(?,?,?,'Standard','staff-test-unit',18.50,'TZS')",variant,product,"STAFF-V-"+variant);
        jdbc.update("INSERT INTO app.stock_balances(variant_id,on_hand) VALUES(?,3)",variant);
        jdbc.update("INSERT INTO app.customer_orders(id,reference,customer_id,idempotency_key,request_hash,status,fulfilment,contact_name,contact_phone,address,instructions,currency,subtotal,delivery_fee,total) VALUES(?,?,?,?,?,?,?,?,?,'Private test address','Private test instruction','TZS',37,?,?)",
                id,"FPA-"+id.toString().replace("-","").toUpperCase(),customer,UUID.randomUUID(),"0".repeat(64),status,fulfilment,name,"+255712345678",fulfilment.equals("PICKUP")?0:null,fulfilment.equals("PICKUP")?37:null);
        jdbc.update("INSERT INTO app.customer_order_items(order_id,variant_id,product_id,product_name,variant_label,sku,unit,quantity,unit_price,line_total) VALUES(?,?,?,'Review material snapshot','Standard',?,'piece',2,18.50,37)",id,variant,product,"STAFF-V-"+variant);
        jdbc.update("INSERT INTO app.staff_order_notices(id,order_id) VALUES(?,?)",id,id);
        return id;
    }
    @Test void reviewProjectionIsRoleScopedFilteredAndReadOnly() throws Exception {
        String name="Review "+UUID.randomUUID();UUID pending=orderFixture(name,"DELIVERY","PENDING");orderFixture(name,"PICKUP","PENDING");orderFixture(name,"DELIVERY","CONFIRMED");
        var sales=new Browser();var account=fixture(StaffRole.SALES,false);assertThat(sales.login(account,"").statusCode()).isEqualTo(200);
        String query=URLEncoder.encode(name,java.nio.charset.StandardCharsets.UTF_8);
        var result=sales.get("/api/staff/orders?q="+query+"&fulfilment=DELIVERY&size=1");assertThat(result.statusCode()).isEqualTo(200);
        var page=JSON.readTree(result.body());assertThat(page.get("total").asInt()).isEqualTo(1);assertThat(page.get("items").get(0).get("summary").get("id").asText()).isEqualTo(pending.toString());
        assertThat(result.body()).doesNotContain("Private test address","Private test instruction","contact_phone","idempotency","request_hash","password");
        var detail=sales.get("/api/staff/orders/"+pending);assertThat(detail.statusCode()).isEqualTo(200);assertThat(detail.body()).contains("Private test address","Private test instruction","Review material snapshot");
        assertThat(sales.get("/api/staff/orders?size=51").statusCode()).isEqualTo(400);
        assertThat(sales.get("/api/staff/orders?q=%25%27%20OR%201%3D1--").statusCode()).isEqualTo(200);
        assertThat(sales.get("/api/staff/orders/"+UUID.randomUUID()).statusCode()).isEqualTo(404);
        var inventory=new Browser();var inv=fixture(StaffRole.INVENTORY,false);inventory.login(inv,"");
        assertThat(inventory.get("/api/staff/orders/"+pending).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT status FROM app.customer_orders WHERE id=?",String.class,pending)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT sum(s.reserved) FROM app.stock_balances s JOIN app.customer_order_items i ON i.variant_id=s.variant_id WHERE i.order_id=?",java.math.BigDecimal.class,pending)).isZero();
    }
    @Test void staffNoticeReadStateIsPersonalAndInventoryCannotReadOrderUpdates() throws Exception {
        UUID id=orderFixture("Notice "+UUID.randomUUID(),"PICKUP","PENDING");
        var a=new Browser();var b=new Browser();a.login(fixture(StaffRole.SALES,false),"");b.login(fixture(StaffRole.SALES,false),"");
        assertThat(a.post("/api/staff/notifications/"+id+"/read",Map.of()).statusCode()).isEqualTo(204);
        assertThat(a.post("/api/staff/notifications/"+id+"/read",Map.of()).statusCode()).isEqualTo(204);
        var mine=JSON.readTree(a.get("/api/staff/notifications").body());var theirs=JSON.readTree(b.get("/api/staff/notifications").body());
        assertThat(java.util.stream.StreamSupport.stream(mine.spliterator(),false).filter(n->n.get("id").asText().equals(id.toString())).findFirst().orElseThrow().get("readAt").isNull()).isFalse();
        assertThat(java.util.stream.StreamSupport.stream(theirs.spliterator(),false).filter(n->n.get("id").asText().equals(id.toString())).findFirst().orElseThrow().get("readAt").isNull()).isTrue();
        assertThat(a.get("/api/staff/notifications").body()).doesNotContain("Private test address","Private test instruction","+255712345678");
        assertThat(a.post("/api/staff/notifications/"+UUID.randomUUID()+"/read",Map.of()).statusCode()).isEqualTo(404);
        var inventory=new Browser();inventory.login(fixture(StaffRole.INVENTORY,false),"");assertThat(inventory.post("/api/staff/notifications/"+id+"/read",Map.of()).statusCode()).isEqualTo(403);
    }
    @AfterAll void exportOptionalBrowserFixtures() throws Exception {
        if(!Boolean.getBoolean("fpa.test.browser-fixtures"))return;
        // FoundationIT's guarded datasource accepts only loopback finishers_test with its dedicated role.
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("finishers_test");
        assertThat(jdbc.queryForObject("SELECT current_user",String.class)).isEqualTo("finishers_test");
        var owner=fixture(StaffRole.OWNER,false);var sales=fixture(StaffRole.SALES,true);var inventory=fixture(StaffRole.INVENTORY,false);var delivery=fixture(StaffRole.DELIVERY,false);
        String name="Browser review "+UUID.randomUUID();UUID id=orderFixture(name,"DELIVERY","PENDING");
        var fixture=Map.of("owner",owner.username(),"ownerSecret",Totp.base32(owner.secret()),"sales",sales.username(),"inventory",inventory.username(),"delivery",delivery.username(),"password",PASSWORD,"orderId",id.toString(),"contactName",name,"mfaKey",Base64.getEncoder().encodeToString(new byte[32]));
        java.nio.file.Path root=java.nio.file.Files.isDirectory(java.nio.file.Path.of("frontend"))?java.nio.file.Path.of(""):java.nio.file.Path.of("..");
        java.nio.file.Files.createDirectories(root.resolve(".local"));
        java.nio.file.Files.writeString(root.resolve(".local/staff-browser-fixtures.json"),JSON.writeValueAsString(fixture));
    }

    @Test void salesHttpConfirmationRequiresCsrfAndReturnsConfirmedBranch() throws Exception {
        UUID id=orderFixture("Confirmation HTTP "+UUID.randomUUID(),"PICKUP","PENDING");
        jdbc.update("UPDATE app.customer_orders SET branch_code='TARIME' WHERE id=?",id);
        jdbc.update("UPDATE app.products SET published=true WHERE id IN (SELECT product_id FROM app.customer_order_items WHERE order_id=?)",id);
        jdbc.update("UPDATE app.product_variants SET published=true WHERE id IN (SELECT variant_id FROM app.customer_order_items WHERE order_id=?)",id);
        jdbc.update("UPDATE app.categories SET published=true WHERE id IN (SELECT category_id FROM app.products WHERE id IN (SELECT product_id FROM app.customer_order_items WHERE order_id=?))",id);
        var b=new Browser();var f=fixture(StaffRole.SALES,false);assertThat(b.login(f,"").statusCode()).isEqualTo(200);
        var agreement=Map.of("deliveryFee",0,"note","Customer agreed by phone today to Tarime pickup and total 37 TZS.");
        String token=b.csrf;b.csrf="invalid";assertThat(b.post("/api/staff/orders/"+id+"/confirm",agreement).statusCode()).isEqualTo(403);b.csrf=token;
        var response=b.post("/api/staff/orders/"+id+"/confirm",agreement);assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).contains("CONFIRMED","TARIME");
        assertThat(b.post("/api/staff/orders/"+id+"/confirm",agreement).statusCode()).isEqualTo(200);
        jdbc.update("UPDATE app.categories SET published=false WHERE id IN (SELECT category_id FROM app.products WHERE id IN (SELECT product_id FROM app.customer_order_items WHERE order_id=?))",id);
    }
}
