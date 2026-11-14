package africa.finishers;

import africa.finishers.identity.domain.StaffRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** FR-14, BR-04 and C1: rejection and cancellation with reasons, reservation release and notices. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderDecisionsIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final String PASSWORD="Order decision passphrase 2026!";
    static final String REASON="The requested tiles are discontinued by the supplier.";
    @DynamicPropertySource static void isolated(DynamicPropertyRegistry registry) throws Exception {
        FoundationIT.isolatedDatabase(registry);
        registry.add("FPA_STAFF_MFA_KEY",()->Base64.getEncoder().encodeToString(new byte[32]));
    }
    @BeforeEach void resetThrottle(){jdbc.update("DELETE FROM app.auth_rate_limits");}

    UUID staff(StaffRole role){
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO app.staff_accounts(id,username,name,role,password_hash,password_change_required) VALUES(?,?,?,?,?,false)",id,"decision-"+id,"Test "+role,role.name(),passwords.encode(PASSWORD));
        return id;
    }
    class Browser {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();String csrf;
        Browser(UUID staffId) throws Exception {
            token();
            var login=post("/api/staff/auth/login",Map.of("username","decision-"+staffId,"password",PASSWORD,"code",""));
            assertThat(login.statusCode()).isEqualTo(200);token();
        }
        HttpResponse<String> get(String path) throws Exception {return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
        void token() throws Exception {csrf=JSON.readTree(get("/api/security/csrf").body()).get("token").asText();}
        HttpResponse<String> post(String path,Object body) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json").header("X-CSRF-TOKEN",csrf)
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        int reject(UUID order,String reason) throws Exception {return post("/api/staff/orders/"+order+"/reject",Map.of("reason",reason)).statusCode();}
        int cancel(UUID order,String reason) throws Exception {return post("/api/staff/orders/"+order+"/cancel",Map.of("reason",reason)).statusCode();}
    }
    record Order(UUID id,UUID variant,UUID customer) {}
    /** An order for two pieces. When confirmed, two pieces are reserved against a balance of five. */
    Order order(String status,String branch){
        UUID id=UUID.randomUUID(),customer=UUID.randomUUID(),category=UUID.randomUUID(),product=UUID.randomUUID(),variant=UUID.randomUUID();
        jdbc.update("INSERT INTO app.customer_accounts(id,name,phone,password_hash) VALUES(?,?,?,'not-a-login-hash')",customer,"Decision customer",CustomerAuthIT.phone());
        jdbc.update("INSERT INTO app.categories(id,slug,name,published) VALUES(?,?,'Decision test category',false)",category,"decision-"+category);
        jdbc.update("INSERT INTO app.products(id,sku,slug,category_id,name,description) VALUES(?,?,?,?,'Decision material','Test fixture')",product,"DEC-"+product,"dec-"+product,category);
        jdbc.update("INSERT INTO app.units(code,label) VALUES('staff-test-unit','piece') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO app.product_variants(id,product_id,sku,label,unit_code,unit_price,currency) VALUES(?,?,?,'Standard','staff-test-unit',18.50,'TZS')",variant,product,"DEC-V-"+variant);
        jdbc.update("INSERT INTO app.stock_balances(variant_id,branch_code,on_hand,reserved) VALUES(?,?,5,?)",variant,branch,status.equals("PENDING")?0:2);
        jdbc.update("INSERT INTO app.customer_orders(id,reference,customer_id,idempotency_key,request_hash,status,fulfilment,contact_name,contact_phone,address,currency,subtotal,delivery_fee,total,branch_code) VALUES(?,?,?,?,?,?,'DELIVERY','Decision customer','+255712345678','Plot 4, Tarime town','TZS',37,0,37,?)",
                id,"FPA-"+id.toString().replace("-","").toUpperCase(),customer,UUID.randomUUID(),"0".repeat(64),status,branch);
        jdbc.update("INSERT INTO app.customer_order_items(order_id,variant_id,product_id,product_name,variant_label,sku,unit,quantity,unit_price,line_total) VALUES(?,?,?,'Decision material','Standard',?,'piece',2,18.50,37)",id,variant,product,"DEC-V-"+variant);
        if(!status.equals("PENDING")){
            jdbc.update("INSERT INTO app.order_confirmations(order_id,actor_id,branch_code,delivery_fee,agreed_total,agreement_note) VALUES(?,?,?,0,37,'Customer agreed by phone')",id,staff(StaffRole.SALES),branch);
            jdbc.update("INSERT INTO app.stock_reservations VALUES(?,?,?,2,2)",id,variant,branch);
        }
        return new Order(id,variant,customer);
    }
    BigDecimal reserved(Order o){return jdbc.queryForObject("SELECT reserved FROM app.stock_balances WHERE variant_id=?",BigDecimal.class,o.variant());}
    BigDecimal onHand(Order o){return jdbc.queryForObject("SELECT on_hand FROM app.stock_balances WHERE variant_id=?",BigDecimal.class,o.variant());}
    String status(Order o){return jdbc.queryForObject("SELECT status FROM app.customer_orders WHERE id=?",String.class,o.id());}

    @Test void salesRejectsAPendingRequestWithACustomerFacingReasonOnce() throws Exception {
        var o=order("PENDING","TARIME");
        var inventory=new Browser(staff(StaffRole.INVENTORY));
        assertThat(inventory.reject(o.id(),REASON)).isEqualTo(403);
        var sales=new Browser(staff(StaffRole.SALES));
        assertThat(sales.reject(o.id(),"No stock")).isEqualTo(400);
        assertThat(sales.reject(UUID.randomUUID(),REASON)).isEqualTo(404);
        var response=sales.post("/api/staff/orders/"+o.id()+"/reject",Map.of("reason",REASON));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).get("decision").get("reason").asText()).isEqualTo(REASON);
        assertThat(JSON.readTree(response.body()).get("summary").get("status").asText()).isEqualTo("REJECTED");
        assertThat(sales.reject(o.id(),REASON)).isEqualTo(200);
        assertThat(sales.reject(o.id(),"A different reason for the same order.")).isEqualTo(409);
        assertThat(sales.cancel(o.id(),REASON)).isEqualTo(409);
        assertThat(sales.post("/api/staff/orders/"+o.id()+"/confirm",Map.of("deliveryFee",0,"note","Customer agreed by phone today.")).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.order_decisions WHERE order_id=? AND decision='REJECTED' AND previous_status='PENDING'",Integer.class,o.id())).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.customer_notifications WHERE order_id=? AND customer_id=? AND title='Order request not accepted' AND message LIKE ?",Integer.class,o.id(),o.customer(),"%"+REASON+"%")).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.staff_order_notices WHERE order_id=? AND kind='REJECTED'",Integer.class,o.id())).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.audit_events WHERE target_reference=? AND event_type='ORDER_REJECTED'",Integer.class,o.id().toString())).isOne();
        assertThat(sales.get("/api/staff/notifications").body()).contains("\"kind\":\"REJECTED\"");
        assertThat(reserved(o)).isZero();
    }
    @Test void cancellingAConfirmedOrderReleasesItsReservationOnceAndStopsTheHandover() throws Exception {
        var o=order("CONFIRMED","TARIME");
        UUID driverId=staff(StaffRole.DELIVERY);
        var sales=new Browser(staff(StaffRole.SALES));
        assertThat(sales.post("/api/staff/fulfilment/"+o.id(),Map.of("target","READY_FOR_DELIVERY","driver",driverId)).statusCode()).isEqualTo(200);
        assertThat(sales.reject(o.id(),REASON)).isEqualTo(409);
        assertThat(new Browser(driverId).cancel(o.id(),REASON)).isEqualTo(403);
        assertThat(sales.cancel(o.id(),REASON)).isEqualTo(200);
        assertThat(sales.cancel(o.id(),REASON)).isEqualTo(200);
        assertThat(status(o)).isEqualTo("CANCELLED");
        assertThat(reserved(o)).isZero();
        assertThat(onHand(o)).isEqualByComparingTo("5");
        assertThat(jdbc.queryForObject("SELECT remaining FROM app.stock_reservations WHERE order_id=?",BigDecimal.class,o.id())).isZero();
        assertThat(jdbc.queryForList("SELECT quantity FROM app.reservation_releases WHERE order_id=? AND reason='ORDER_CANCELLED'",BigDecimal.class,o.id()))
                .singleElement().satisfies(q->assertThat(q).isEqualByComparingTo("2"));
        assertThat(jdbc.queryForObject("SELECT previous_status FROM app.order_decisions WHERE order_id=?",String.class,o.id())).isEqualTo("READY_FOR_DELIVERY");
        var driver=new Browser(driverId);
        assertThat(driver.get("/api/staff/fulfilment").body()).doesNotContain(o.id().toString());
        assertThat(driver.post("/api/staff/fulfilment/"+o.id(),Map.of("target","OUT_FOR_DELIVERY")).statusCode()).isEqualTo(409);
        assertThat(sales.post("/api/staff/fulfilment/"+o.id(),Map.of("target","OUT_FOR_DELIVERY")).statusCode()).isEqualTo(409);
        assertThat(onHand(o)).isEqualByComparingTo("5");
        assertThat(jdbc.queryForObject("SELECT message FROM app.customer_notifications WHERE order_id=? AND title='Order cancelled'",String.class,o.id())).contains(REASON,"released");
    }
    @Test void anOrderThatLeftTheShopCannotBeCancelled() throws Exception {
        var o=order("CONFIRMED","TARIME");
        UUID driverId=staff(StaffRole.DELIVERY);
        var sales=new Browser(staff(StaffRole.SALES));
        assertThat(sales.post("/api/staff/fulfilment/"+o.id(),Map.of("target","READY_FOR_DELIVERY","driver",driverId)).statusCode()).isEqualTo(200);
        assertThat(new Browser(driverId).post("/api/staff/fulfilment/"+o.id(),Map.of("target","OUT_FOR_DELIVERY")).statusCode()).isEqualTo(200);
        assertThat(sales.cancel(o.id(),REASON)).isEqualTo(409);
        assertThat(status(o)).isEqualTo("OUT_FOR_DELIVERY");
        assertThat(onHand(o)).isEqualByComparingTo("3");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.reservation_releases WHERE order_id=?",Integer.class,o.id())).isZero();
    }
    @Test void oldOrdersAtTheClosedBranchCanStillBeCancelledButNotChangedOtherwise() throws Exception {
        Order o;
        jdbc.update("UPDATE app.branches SET active=true WHERE code='MWANZA'");
        try{o=order("CONFIRMED","MWANZA");}finally{jdbc.update("UPDATE app.branches SET active=false WHERE code='MWANZA'");}
        var sales=new Browser(staff(StaffRole.SALES));
        assertThat(sales.post("/api/staff/fulfilment/"+o.id(),Map.of("target","READY_FOR_DELIVERY","driver",staff(StaffRole.DELIVERY))).statusCode()).isEqualTo(409);
        assertThat(sales.cancel(o.id(),"The Mwanza branch has closed, so this order cannot be filled.")).isEqualTo(200);
        assertThat(status(o)).isEqualTo("CANCELLED");
        assertThat(reserved(o)).isZero();
        assertThatThrownBy(()->jdbc.update("UPDATE app.stock_balances SET on_hand=on_hand+1 WHERE variant_id=?",o.variant())).hasMessageContaining("branch is closed");
    }
    @Test void decisionsAndReleasesAreAppendOnly() throws Exception {
        var o=order("CONFIRMED","TARIME");
        assertThat(new Browser(staff(StaffRole.SALES)).cancel(o.id(),REASON)).isEqualTo(200);
        assertThatThrownBy(()->jdbc.update("UPDATE app.order_decisions SET reason='Edited after the fact' WHERE order_id=?",o.id())).hasMessageContaining("append-only");
        assertThatThrownBy(()->jdbc.update("DELETE FROM app.reservation_releases WHERE order_id=?",o.id())).hasMessageContaining("append-only");
    }
}
