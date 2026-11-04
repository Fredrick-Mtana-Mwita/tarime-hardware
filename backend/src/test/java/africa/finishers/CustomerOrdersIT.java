package africa.finishers;

import africa.finishers.orders.application.CustomerOrders;
import africa.finishers.orders.application.OrderProblem;
import static africa.finishers.orders.application.OrderModels.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerOrdersIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerOrders orders;
    static final JsonMapper JSON=JsonMapper.builder().build();
    @DynamicPropertySource static void isolated(DynamicPropertyRegistry registry) throws Exception {FoundationIT.isolatedDatabase(registry);}
    UUID customer,category,product,whole,fractional;
    String phone;
    @BeforeEach void fixture(){
        customer=UUID.randomUUID();category=UUID.randomUUID();product=UUID.randomUUID();whole=UUID.randomUUID();fractional=UUID.randomUUID();phone=CustomerAuthIT.phone();
        jdbc.update("INSERT INTO app.customer_accounts(id,name,phone,password_hash) VALUES(?,'Order test customer',?,'not-a-login-hash')",customer,phone);
        String suffix=UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO app.categories(id,slug,name,published) VALUES(?,?,?,true)",category,"order-test-"+suffix,"Order test");
        jdbc.update("INSERT INTO app.products(id,sku,slug,category_id,name,description,published) VALUES(?,?,?,?,?,'Integration fixture',true)",product,"TEST-"+suffix,"test-"+suffix,category,"Test material");
        String unit="test"+suffix.substring(0,20);
        jdbc.update("INSERT INTO app.units(code,label,fractional) VALUES(?,'piece',false),(?,'metre',true)",unit,unit+"f");
        jdbc.update("INSERT INTO app.product_variants(id,product_id,sku,label,unit_code,unit_price,currency,published) VALUES(?,?,?,'Whole',?,10.25,'TZS',true)",whole,product,"WHOLE-"+suffix,unit);
        jdbc.update("INSERT INTO app.product_variants(id,product_id,sku,label,unit_code,unit_price,currency,published) VALUES(?,?,?,'Fractional',?,1.33,'TZS',true)",fractional,product,"FRAC-"+suffix,unit+"f");
        jdbc.update("INSERT INTO app.stock_balances(variant_id,on_hand) VALUES(?,5),(?,20)",whole,fractional);
    }
    @AfterEach void hideFixture(){
        jdbc.execute("DROP TRIGGER IF EXISTS test_fail_order_notice ON app.customer_notifications");
        jdbc.execute("DROP FUNCTION IF EXISTS app.test_fail_order_notice()");
        jdbc.update("UPDATE app.categories SET published=false,archived=true WHERE id=?",category);
        jdbc.update("UPDATE app.products SET published=false,archived=true WHERE id=?",product);
    }
    Submission request(Fulfilment mode,String qty){return new Submission(List.of(new ReviewedItem(whole,new BigDecimal(qty),new BigDecimal("10.25"),"TZS")),mode,"Test customer",phone,"A test delivery address","Keep dry",Branch.TARIME);}
    long count(String table){return jdbc.queryForObject("SELECT count(*) FROM app."+table,Long.class);}
    void unchangedStock(){assertThat(jdbc.queryForObject("SELECT on_hand-reserved FROM app.stock_balances WHERE variant_id=?",BigDecimal.class,whole)).isEqualByComparingTo("5");assertThat(jdbc.queryForObject("SELECT reserved FROM app.stock_balances WHERE variant_id=?",BigDecimal.class,whole)).isZero();}
    @Test void submissionSnapshotsAmountsAndContactsButDoesNotReserveOrRecognizeASale(){
        var order=orders.submit(phone,UUID.randomUUID(),request(Fulfilment.DELIVERY,"2"));
        assertThat(order.summary().status()).isEqualTo("PENDING");
        assertThat(order.summary().subtotal()).isEqualByComparingTo("20.50");
        assertThat(order.summary().deliveryFee()).isNull();assertThat(order.summary().total()).isNull();
        assertThat(order.phone()).isEqualTo(phone);assertThat(order.address()).isEqualTo("A test delivery address");
        unchangedStock();
        jdbc.update("UPDATE app.products SET name='Renamed',published=false WHERE id=?",product);
        jdbc.update("UPDATE app.product_variants SET unit_price=99 WHERE id=?",whole);
        var saved=orders.find(phone,order.summary().id());
        assertThat(saved.items().getFirst().productName()).isEqualTo("Test material");
        assertThat(saved.items().getFirst().unitPrice()).isEqualByComparingTo("10.25");
        assertThat(saved.summary().subtotal()).isEqualByComparingTo("20.50");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.customer_notifications WHERE order_id=?",Integer.class,order.summary().id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.staff_order_notices WHERE order_id=?",Integer.class,order.summary().id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.audit_events WHERE event_type='ORDER_SUBMITTED' AND target_reference=?",Integer.class,order.summary().id().toString())).isEqualTo(1);
    }
    @Test void pickupHasZeroFeeAndFractionalUnitsRoundEachLineToTwoDecimals(){
        var quote=orders.quote(List.of(new Selection(fractional,new BigDecimal("1.125"))));
        assertThat(quote.subtotal()).isEqualByComparingTo("1.50");
        var body=new Submission(List.of(new ReviewedItem(fractional,new BigDecimal("1.125"),new BigDecimal("1.33"),"TZS")),Fulfilment.PICKUP,"Test buyer",phone,"ignored pickup address","",Branch.TARIME);
        var order=orders.submit(phone,UUID.randomUUID(),body);
        assertThat(order.summary().deliveryFee()).isZero();assertThat(order.summary().total()).isEqualByComparingTo("1.50");assertThat(order.address()).isEmpty();
    }
    @Test void quantityAndVisibilityRulesAreEnforcedBeforeAnyWrites(){
        for(String qty:List.of("0","-1","1.0001","1000001","1.5"))assertThatThrownBy(()->orders.quote(List.of(new Selection(whole,new BigDecimal(qty))))).isInstanceOf(OrderProblem.class);
        assertThatThrownBy(()->orders.quote(List.of(new Selection(whole,BigDecimal.ONE),new Selection(whole,BigDecimal.ONE)))).isInstanceOf(OrderProblem.class);
        jdbc.update("UPDATE app.product_variants SET currency='USD' WHERE id=?",fractional);
        assertThatThrownBy(()->orders.quote(List.of(new Selection(whole,BigDecimal.ONE),new Selection(fractional,BigDecimal.ONE)))).isInstanceOf(OrderProblem.class);
        jdbc.update("UPDATE app.categories SET published=false WHERE id=?",category);
        assertThatThrownBy(()->orders.quote(List.of(new Selection(whole,BigDecimal.ONE)))).isInstanceOf(OrderProblem.class);
        unchangedStock();
    }
    @Test void priceAndStockChangesRequireReviewAndALaterLineFailureLeavesNoPartialOrder(){
        long before=count("customer_orders"),notices=count("customer_notifications");
        jdbc.update("UPDATE app.product_variants SET unit_price=11 WHERE id=?",whole);
        assertThatThrownBy(()->orders.submit(phone,UUID.randomUUID(),request(Fulfilment.PICKUP,"1"))).isInstanceOfSatisfying(OrderProblem.class,e->assertThat(e.status()).isEqualTo(409));
        jdbc.update("UPDATE app.product_variants SET unit_price=10.25 WHERE id=?",whole);
        var body=request(Fulfilment.PICKUP,"1");
        var bad=new Submission(List.of(body.items().getFirst(),new ReviewedItem(fractional,new BigDecimal("21"),new BigDecimal("1.33"),"TZS")),body.fulfilment(),body.name(),phone,"","",Branch.TARIME);
        assertThatThrownBy(()->orders.submit(phone,UUID.randomUUID(),bad)).isInstanceOf(OrderProblem.class);
        jdbc.update("UPDATE app.stock_balances SET reserved=5 WHERE variant_id=?",whole);
        assertThatThrownBy(()->orders.submit(phone,UUID.randomUUID(),body)).isInstanceOf(OrderProblem.class);
        assertThat(count("customer_orders")).isEqualTo(before);assertThat(count("customer_notifications")).isEqualTo(notices);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.customer_order_items WHERE variant_id=?",Integer.class,whole)).isZero();
    }
    @Test void concurrentIdenticalSubmissionsCommitOneOrderAuditAndNotice() throws Exception {
        UUID key=UUID.randomUUID();var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<Order> work=()->{start.await(5,TimeUnit.SECONDS);return orders.submit(phone,key,request(Fulfilment.PICKUP,"1"));};
            var first=pool.submit(work);var second=pool.submit(work);start.countDown();
            var a=first.get(20,TimeUnit.SECONDS);var b=second.get(20,TimeUnit.SECONDS);
            assertThat(a.summary().id()).isEqualTo(b.summary().id());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.customer_orders WHERE customer_id=?",Integer.class,customer)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.customer_notifications WHERE order_id=?",Integer.class,a.summary().id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.audit_events WHERE target_reference=? AND event_type='ORDER_SUBMITTED'",Integer.class,a.summary().id().toString())).isEqualTo(1);
        }
        unchangedStock();
    }
    @Test void retryReturnsOriginalEvenAfterCatalogueChangesButKeyReuseWithDifferentPayloadConflicts(){
        UUID key=UUID.randomUUID();var body=request(Fulfilment.PICKUP,"1");var original=orders.submit(phone,key,body);
        jdbc.update("UPDATE app.products SET published=false WHERE id=?",product);
        assertThat(orders.submit(phone,key,body).summary().id()).isEqualTo(original.summary().id());
        assertThatThrownBy(()->orders.submit(phone,key,request(Fulfilment.PICKUP,"2"))).isInstanceOfSatisfying(OrderProblem.class,e->assertThat(e.status()).isEqualTo(409));
    }
    @Test void multiplePendingRequestsDoNotConsumeAvailableStock(){
        orders.submit(phone,UUID.randomUUID(),request(Fulfilment.PICKUP,"5"));
        orders.submit(phone,UUID.randomUUID(),request(Fulfilment.PICKUP,"5"));
        unchangedStock();assertThat(orders.list(phone,0,10).total()).isEqualTo(2);
    }
    @Test void notificationIntentFailureRollsBackOrderItemsAndAudit(){
        long before=count("customer_orders"),items=count("customer_order_items"),audit=count("audit_events"),staffNotices=count("staff_order_notices");
        jdbc.execute("CREATE FUNCTION app.test_fail_order_notice() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN IF NEW.customer_id='"+customer+"'::uuid THEN RAISE EXCEPTION 'test storage failure'; END IF; RETURN NEW; END; $$");
        jdbc.execute("CREATE TRIGGER test_fail_order_notice BEFORE INSERT ON app.customer_notifications FOR EACH ROW EXECUTE FUNCTION app.test_fail_order_notice()");
        assertThatThrownBy(()->orders.submit(phone,UUID.randomUUID(),request(Fulfilment.PICKUP,"1"))).isInstanceOf(RuntimeException.class);
        assertThat(count("customer_orders")).isEqualTo(before);assertThat(count("customer_order_items")).isEqualTo(items);assertThat(count("audit_events")).isEqualTo(audit);assertThat(count("staff_order_notices")).isEqualTo(staffNotices);unchangedStock();
    }
    @Test void successfulSubmissionLimitStillAllowsAnExistingRetry(){
        UUID key=UUID.randomUUID();var body=request(Fulfilment.PICKUP,"1");var original=orders.submit(phone,key,body);
        for(int i=1;i<20;i++)orders.submit(phone,UUID.randomUUID(),body);
        assertThatThrownBy(()->orders.submit(phone,UUID.randomUUID(),body)).isInstanceOfSatisfying(OrderProblem.class,e->assertThat(e.status()).isEqualTo(429));
        assertThat(orders.submit(phone,key,body).summary().id()).isEqualTo(original.summary().id());
        assertThat(orders.list(phone,1,7).items()).hasSize(7);unchangedStock();
    }
    class Browser {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).build();
        String csrf;
        HttpResponse<String> get(String path) throws Exception {return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
        void token() throws Exception {csrf=JSON.readTree(get("/api/security/csrf").body()).get("token").asText();}
        HttpResponse<String> post(String path,Object body,UUID key,boolean token) throws Exception {
            var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json");
            if(token)r.header("X-CSRF-TOKEN",csrf);if(key!=null)r.header("Idempotency-Key",key.toString());
            return client.send(r.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        void register() throws Exception {token();assertThat(post("/api/auth/register",CustomerAuthIT.registration(CustomerAuthIT.phone()),null,true).statusCode()).isEqualTo(201);token();}
    }
    @Test void httpEnforcesCsrfAuthenticationOwnershipAndIgnoresClientTotals() throws Exception {
        jdbc.update("DELETE FROM app.auth_rate_limits");
        var guest=new Browser();guest.token();var body=request(Fulfilment.PICKUP,"1");
        assertThat(guest.post("/api/cart/quote",Map.of("items",List.of(new Selection(whole,BigDecimal.ONE))),null,false).statusCode()).isEqualTo(403);
        assertThat(guest.post("/api/cart/quote",Map.of("items",List.of(new Selection(whole,BigDecimal.ONE))),null,true).statusCode()).isEqualTo(200);
        assertThat(guest.post("/api/orders",body,UUID.randomUUID(),true).statusCode()).isEqualTo(401);
        var a=new Browser();a.register();var b=new Browser();b.register();
        assertThat(a.post("/api/orders",body,UUID.randomUUID(),false).statusCode()).isEqualTo(403);
        var tampered=new HashMap<String,Object>();tampered.put("branch","TARIME");tampered.put("items",body.items());tampered.put("fulfilment","PICKUP");tampered.put("name",body.name());tampered.put("phone",phone);tampered.put("subtotal",0);tampered.put("total",0);tampered.put("status","CONFIRMED");tampered.put("customerId",customer);
        var submitted=a.post("/api/orders",tampered,UUID.randomUUID(),true);assertThat(submitted.statusCode()).isEqualTo(201);
        JsonNode json=JSON.readTree(submitted.body());String id=json.get("summary").get("id").asText();
        assertThat(json.get("summary").get("subtotal").decimalValue()).isEqualByComparingTo("10.25");assertThat(json.get("summary").get("status").asText()).isEqualTo("PENDING");
        assertThat(a.get("/api/orders/"+id).statusCode()).isEqualTo(200);assertThat(b.get("/api/orders/"+id).statusCode()).isEqualTo(404);assertThat(b.get("/api/orders/"+UUID.randomUUID()).statusCode()).isEqualTo(404);
        assertThat(JSON.readTree(b.get("/api/orders").body()).get("total").asInt()).isZero();
        var notices=JSON.readTree(a.get("/api/notifications").body());String notice=notices.get(0).get("id").asText();
        assertThat(b.post("/api/notifications/"+notice+"/read",Map.of(),null,true).statusCode()).isEqualTo(404);
        assertThat(a.post("/api/notifications/"+notice+"/read",Map.of(),null,true).statusCode()).isEqualTo(204);
        assertThat(a.post("/api/notifications/"+notice+"/read",Map.of(),null,true).statusCode()).isEqualTo(204);
        assertThat(JSON.readTree(a.get("/api/notifications").body()).get(0).get("readAt").isNull()).isFalse();
        assertThat(a.get("/api/admin/orders").statusCode()).isEqualTo(403);unchangedStock();
    }

    @Autowired africa.finishers.orders.application.OrderConfirmation confirmation;
    africa.finishers.identity.application.StaffPrincipal sales(){
        UUID id=UUID.randomUUID();String username="branch-"+id;
        jdbc.update("INSERT INTO app.staff_accounts(id,username,name,role,password_hash,password_change_required) VALUES(?,?,'Branch test sales','SALES','test-only',false)",id,username);
        return new africa.finishers.identity.application.StaffPrincipal(id,username,"Branch test sales",africa.finishers.identity.domain.StaffRole.SALES,0,false);
    }
    africa.finishers.orders.application.OrderConfirmation.Agreement agreed(String fee){return new africa.finishers.orders.application.OrderConfirmation.Agreement(new BigDecimal(fee),"Customer agreed by phone today to branch, items and final total.");}
    @Test void branchesAreRequiredAndCannotBorrowStock(){
        var raw=request(Fulfilment.PICKUP,"1");
        var missing=new Submission(raw.items(),raw.fulfilment(),raw.name(),phone,"","",null);
        assertThatThrownBy(()->orders.submit(phone,UUID.randomUUID(),missing)).isInstanceOf(OrderProblem.class);
        assertThatThrownBy(()->orders.quote(List.of(new Selection(whole,BigDecimal.ONE)),Branch.MWANZA)).isInstanceOf(OrderProblem.class);
        jdbc.update("INSERT INTO app.stock_balances(variant_id,branch_code,on_hand) VALUES(?,'MWANZA',2)",whole);
        assertThat(orders.quote(List.of(new Selection(whole,BigDecimal.ONE)),Branch.MWANZA).items().getFirst().available()).isEqualByComparingTo("2");
        var mwanza=new Submission(raw.items(),raw.fulfilment(),raw.name(),phone,"","",Branch.MWANZA);
        UUID key=UUID.randomUUID();var order=orders.submit(phone,key,mwanza);
        assertThatThrownBy(()->orders.submit(phone,key,raw)).isInstanceOf(OrderProblem.class);
        confirmation.confirm(sales(),order.summary().id(),agreed("0"));
        assertThat(jdbc.queryForObject("SELECT reserved FROM app.stock_balances WHERE variant_id=? AND branch_code='TARIME'",BigDecimal.class,whole)).isZero();
        assertThat(jdbc.queryForObject("SELECT reserved FROM app.stock_balances WHERE variant_id=? AND branch_code='MWANZA'",BigDecimal.class,whole)).isEqualByComparingTo("1");
    }
    @Test void confirmationReservesOncePreservesOriginalTotalsAndNotifies(){
        var order=orders.submit(phone,UUID.randomUUID(),request(Fulfilment.DELIVERY,"2"));var actor=sales();var agreement=agreed("5.50");
        var confirmed=confirmation.confirm(actor,order.summary().id(),agreement);
        assertThat(confirmed.summary().status()).isEqualTo("CONFIRMED");assertThat(confirmed.summary().total()).isEqualByComparingTo("26.00");
        confirmation.confirm(actor,order.summary().id(),agreement);
        assertThatThrownBy(()->confirmation.confirm(actor,order.summary().id(),agreed("9"))).isInstanceOf(OrderProblem.class);
        assertThat(jdbc.queryForObject("SELECT on_hand FROM app.stock_balances WHERE variant_id=?",BigDecimal.class,whole)).isEqualByComparingTo("5");
        assertThat(jdbc.queryForObject("SELECT reserved FROM app.stock_balances WHERE variant_id=?",BigDecimal.class,whole)).isEqualByComparingTo("2");
        assertThat(jdbc.queryForObject("SELECT original_total FROM app.order_confirmations WHERE order_id=?",BigDecimal.class,order.summary().id())).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.stock_reservations WHERE order_id=?",Integer.class,order.summary().id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.customer_notifications WHERE order_id=? AND title='Order confirmed'",Integer.class,order.summary().id())).isEqualTo(1);
    }
    @Test void competingConfirmationsCannotOversell() throws Exception {
        var a=orders.submit(phone,UUID.randomUUID(),request(Fulfilment.PICKUP,"4"));var b=orders.submit(phone,UUID.randomUUID(),request(Fulfilment.PICKUP,"4"));var actor=sales();
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var results=new ArrayList<Future<Boolean>>();
            for(var order:List.of(a,b))results.add(pool.submit(()->{start.await();try{confirmation.confirm(actor,order.summary().id(),agreed("0"));return true;}catch(OrderProblem e){assertThat(e.status()).isEqualTo(409);return false;}}));
            start.countDown();int successes=0;for(var result:results)if(result.get(30,TimeUnit.SECONDS))successes++;assertThat(successes).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT reserved FROM app.stock_balances WHERE variant_id=?",BigDecimal.class,whole)).isEqualByComparingTo("4");
    }
    @Test void insufficientSecondLineRollsBackAllReservations(){
        var base=request(Fulfilment.PICKUP,"2");var body=new Submission(List.of(base.items().getFirst(),new ReviewedItem(fractional,new BigDecimal("2"),new BigDecimal("1.33"),"TZS")),base.fulfilment(),base.name(),phone,"","",Branch.TARIME);
        var order=orders.submit(phone,UUID.randomUUID(),body);var actor=sales();
        // Make the last variant in lock order unavailable to exercise partial-write rollback.
        UUID last=jdbc.queryForObject("SELECT variant_id FROM app.customer_order_items WHERE order_id=? ORDER BY variant_id DESC LIMIT 1",UUID.class,order.summary().id());
        jdbc.update("UPDATE app.stock_balances SET on_hand=0 WHERE variant_id=?",last);
        assertThatThrownBy(()->confirmation.confirm(actor,order.summary().id(),agreed("0"))).isInstanceOf(OrderProblem.class);
        assertThat(jdbc.queryForObject("SELECT sum(reserved) FROM app.stock_balances WHERE variant_id IN (?,?)",BigDecimal.class,whole,fractional)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.order_confirmations WHERE order_id=?",Integer.class,order.summary().id())).isZero();
        assertThat(orders.find(phone,order.summary().id()).summary().status()).isEqualTo("PENDING");
    }
    @Test void confirmationRejectsChangedPricesMissingAgreementAndWrongRole(){
        var order=orders.submit(phone,UUID.randomUUID(),request(Fulfilment.PICKUP,"1"));var actor=sales();
        assertThatThrownBy(()->confirmation.confirm(actor,order.summary().id(),agreed("1"))).isInstanceOf(OrderProblem.class);
        assertThatThrownBy(()->confirmation.confirm(actor,order.summary().id(),new africa.finishers.orders.application.OrderConfirmation.Agreement(BigDecimal.ZERO,""))).isInstanceOf(OrderProblem.class);
        jdbc.update("UPDATE app.product_variants SET unit_price=11 WHERE id=?",whole);
        assertThatThrownBy(()->confirmation.confirm(actor,order.summary().id(),agreed("0"))).isInstanceOf(OrderProblem.class);
        jdbc.update("UPDATE app.staff_accounts SET role='INVENTORY',session_version=1 WHERE id=?",actor.id());
        assertThatThrownBy(()->confirmation.confirm(actor,order.summary().id(),agreed("0"))).isInstanceOf(africa.finishers.identity.application.AuthProblem.class);
        unchangedStock();
    }
    @Test void notificationFailureRollsBackConfirmationAndStock(){
        var order=orders.submit(phone,UUID.randomUUID(),request(Fulfilment.PICKUP,"1"));var actor=sales();
        jdbc.execute("CREATE FUNCTION app.test_fail_order_notice() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN IF NEW.customer_id='"+customer+"'::uuid THEN RAISE EXCEPTION 'test storage failure'; END IF; RETURN NEW; END; $$");
        jdbc.execute("CREATE TRIGGER test_fail_order_notice BEFORE INSERT ON app.customer_notifications FOR EACH ROW EXECUTE FUNCTION app.test_fail_order_notice()");
        assertThatThrownBy(()->confirmation.confirm(actor,order.summary().id(),agreed("0"))).isInstanceOf(RuntimeException.class);
        unchangedStock();assertThat(orders.find(phone,order.summary().id()).summary().status()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.order_confirmations WHERE order_id=?",Integer.class,order.summary().id())).isZero();
    }

    @Test void concurrentConfirmationRetriesCommitOneReservation() throws Exception {
        var order=orders.submit(phone,UUID.randomUUID(),request(Fulfilment.PICKUP,"2"));var actor=sales();var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->{start.await();return confirmation.confirm(actor,order.summary().id(),agreed("0"));});
            var second=pool.submit(()->{start.await();return confirmation.confirm(actor,order.summary().id(),agreed("0"));});
            start.countDown();assertThat(first.get(30,TimeUnit.SECONDS).summary().id()).isEqualTo(second.get(30,TimeUnit.SECONDS).summary().id());
        }
        assertThat(jdbc.queryForObject("SELECT reserved FROM app.stock_balances WHERE variant_id=?",BigDecimal.class,whole)).isEqualByComparingTo("2");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.audit_events WHERE target_reference=? AND event_type='ORDER_CONFIRMED'",Integer.class,order.summary().id().toString())).isEqualTo(1);
    }
    @Test void legacyRequestsRemainUnassignedAndCannotBeConfirmed(){
        var order=orders.submit(phone,UUID.randomUUID(),request(Fulfilment.PICKUP,"1"));var actor=sales();
        jdbc.update("UPDATE app.customer_orders SET branch_code=NULL WHERE id=?",order.summary().id());
        assertThatThrownBy(()->confirmation.confirm(actor,order.summary().id(),agreed("0"))).isInstanceOf(OrderProblem.class);
        unchangedStock();assertThat(orders.find(phone,order.summary().id()).summary().branch()).isNull();
    }
}
