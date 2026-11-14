package africa.finishers;

import africa.finishers.identity.domain.StaffRole;
import africa.finishers.identity.domain.Totp;
import africa.finishers.identity.infrastructure.StaffSecretVault;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** C2, C4, FR-19, FR-20, BR-08: manual payments, receipts, Owner-approved corrections and refunds. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FinanceIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired StaffSecretVault vault;
    @Autowired Clock clock;
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final String PASSWORD="Finance test passphrase 2026!";
    @DynamicPropertySource static void isolated(DynamicPropertyRegistry registry) throws Exception {
        FoundationIT.isolatedDatabase(registry);
        registry.add("FPA_STAFF_MFA_KEY",()->Base64.getEncoder().encodeToString(new byte[32]));
    }
    @BeforeEach void resetThrottle(){jdbc.update("DELETE FROM app.auth_rate_limits");}

    class Browser {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();String csrf;UUID staffId;
        HttpResponse<String> get(String path) throws Exception {return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
        void token() throws Exception {csrf=JSON.readTree(get("/api/security/csrf").body()).get("token").asText();}
        HttpResponse<String> post(String path,Object body) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json").header("X-CSRF-TOKEN",csrf)
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        JsonNode json(HttpResponse<String> response){assertThat(response.statusCode()).as(response.body()).isBetween(200,299);return JSON.readTree(response.body());}
        HttpResponse<String> pay(UUID order,Map<String,Object> body) throws Exception {return post("/api/staff/finance/orders/"+order+"/payments",body);}
        HttpResponse<String> adjust(UUID order,Map<String,Object> body) throws Exception {return post("/api/staff/finance/orders/"+order+"/adjustments",body);}
        JsonNode ledger(UUID order) throws Exception {return json(get("/api/staff/finance/orders/"+order));}
    }
    Browser staff(StaffRole role) throws Exception {
        UUID id=UUID.randomUUID();byte[] secret=role==StaffRole.OWNER?Totp.newSecret():null;
        jdbc.update("INSERT INTO app.staff_accounts(id,username,name,role,password_hash,password_change_required,mfa_secret_encrypted) VALUES(?,?,?,?,?,false,?)",
                id,"finance-"+id,"Finance "+role,role.name(),passwords.encode(PASSWORD),secret==null?null:vault.encrypt(id,secret));
        var b=new Browser();b.staffId=id;b.token();
        String code=secret==null?"":Totp.code(secret,clock.instant().getEpochSecond()/30,6);
        assertThat(b.post("/api/staff/auth/login",Map.of("username","finance-"+id,"password",PASSWORD,"code",code)).statusCode()).isEqualTo(200);
        b.token();return b;
    }
    /** A delivery order of 37 TZS materials plus a 13 TZS fee: 50 TZS in total. */
    UUID order(UUID customer,String status){
        UUID id=UUID.randomUUID();
        if(customer==null){customer=UUID.randomUUID();jdbc.update("INSERT INTO app.customer_accounts(id,name,phone,password_hash) VALUES(?,'Finance customer',?,'not-a-login-hash')",customer,CustomerAuthIT.phone());}
        jdbc.update("INSERT INTO app.customer_orders(id,reference,customer_id,idempotency_key,request_hash,status,fulfilment,contact_name,contact_phone,address,currency,subtotal,delivery_fee,total,branch_code) VALUES(?,?,?,?,?,?,'DELIVERY','Finance customer','+255712345678','Plot 4, Tarime town','TZS',37,13,50,'TARIME')",
                id,"FPA-"+id.toString().replace("-","").toUpperCase(),customer,UUID.randomUUID(),"0".repeat(64),status);
        return id;
    }
    static Map<String,Object> cash(UUID id,String amount){return Map.of("id",id,"method","CASH","amount",new BigDecimal(amount));}
    static Map<String,Object> mobile(UUID id,String amount,String reference){return Map.of("id",id,"method","MOBILE_MONEY","provider","MPESA","reference",reference,"amount",new BigDecimal(amount));}
    static String ref(){return "TX"+UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase();}

    @Test void salesRecordsPartAndFullPaymentsWithReceiptsButNoOverpayment() throws Exception {
        var sales=staff(StaffRole.SALES);
        UUID pending=order(null,"PENDING");
        assertThat(sales.pay(pending,cash(UUID.randomUUID(),"10")).statusCode()).isEqualTo(409);
        UUID o=order(null,"CONFIRMED");
        for(var role:List.of(StaffRole.INVENTORY,StaffRole.DELIVERY))assertThat(staff(role).pay(o,cash(UUID.randomUUID(),"10")).statusCode()).isEqualTo(403);
        assertThat(sales.pay(o,Map.of("id",UUID.randomUUID(),"method","MOBILE_MONEY","provider","MPESA","amount",10)).statusCode()).isEqualTo(400);
        assertThat(sales.pay(o,Map.of("id",UUID.randomUUID(),"method","CASH","reference","ABC123456","amount",10)).statusCode()).isEqualTo(400);
        assertThat(sales.pay(o,cash(UUID.randomUUID(),"0")).statusCode()).isEqualTo(400);
        assertThat(sales.pay(o,cash(UUID.randomUUID(),"10.005")).statusCode()).isEqualTo(400);
        UUID first=UUID.randomUUID();
        var ledger=sales.json(sales.pay(o,cash(first,"20")));
        assertThat(ledger.get("balance").get("status").asText()).isEqualTo("PART_PAID");
        assertThat(ledger.get("balance").get("due").decimalValue()).isEqualByComparingTo("30");
        assertThat(ledger.get("payments").get(0).get("receiptNumber").asText()).matches("RCT-\\d{4}-\\d{6}");
        assertThat(sales.pay(o,cash(first,"20")).statusCode()).isEqualTo(200);
        assertThat(sales.pay(o,cash(first,"25")).statusCode()).isEqualTo(409);
        assertThat(sales.pay(o,cash(UUID.randomUUID(),"30.01")).statusCode()).isEqualTo(409);
        String reference=ref();
        var paid=sales.json(sales.pay(o,mobile(UUID.randomUUID(),"30",reference.toLowerCase())));
        assertThat(paid.get("balance").get("status").asText()).isEqualTo("PAID");
        assertThat(paid.get("payments").get(1).get("reference").asText()).isEqualTo(reference);
        assertThat(sales.pay(o,cash(UUID.randomUUID(),"1")).statusCode()).isEqualTo(409);
        UUID other=order(null,"CONFIRMED");
        assertThat(sales.pay(other,mobile(UUID.randomUUID(),"5",reference)).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.payments WHERE order_id=?",Integer.class,o)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.audit_events WHERE target_reference=? AND event_type='PAYMENT_RECORDED'",Integer.class,o.toString())).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT title FROM app.customer_notifications WHERE order_id=?",String.class,o)).containsOnly("Payment received").hasSize(2);
        assertThatThrownBy(()->jdbc.update("UPDATE app.payments SET amount=1 WHERE id=?",first)).hasMessageContaining("append-only");
        assertThatThrownBy(()->jdbc.update("DELETE FROM app.payments WHERE id=?",first)).hasMessageContaining("append-only");
    }
    @Test void concurrentPaymentsNeverExceedTheTotal() throws Exception {
        var sales=staff(StaffRole.SALES);UUID o=order(null,"CONFIRMED");
        var pool=Executors.newFixedThreadPool(4);
        try{
            var results=pool.invokeAll(List.<Callable<Integer>>of(()->sales.pay(o,cash(UUID.randomUUID(),"30")).statusCode(),()->sales.pay(o,cash(UUID.randomUUID(),"30")).statusCode(),
                    ()->sales.pay(o,cash(UUID.randomUUID(),"30")).statusCode()));
            int ok=0;for(var r:results)if(r.get()==200)ok++;
            assertThat(ok).isOne();
        }finally{pool.shutdown();}
        assertThat(jdbc.queryForObject("SELECT net_paid FROM app.order_balances WHERE order_id=?",BigDecimal.class,o)).isEqualByComparingTo("30");
    }
    @Test void aWrongReceiptIsCancelledOnlyAfterOwnerApprovalAndPendingRequestsDoNotChangeBalances() throws Exception {
        var sales=staff(StaffRole.SALES);UUID o=order(null,"CONFIRMED");
        UUID payment=UUID.randomUUID();
        sales.json(sales.pay(o,cash(payment,"50")));
        UUID request=UUID.randomUUID();
        var reversal=Map.<String,Object>of("id",request,"kind","REVERSAL","paymentId",payment,"reason","Cash was counted twice by mistake at the counter.");
        assertThat(sales.adjust(o,Map.of("id",UUID.randomUUID(),"kind","REVERSAL","paymentId",payment,"reason","short")).statusCode()).isEqualTo(400);
        var waiting=sales.json(sales.adjust(o,reversal));
        assertThat(waiting.get("balance").get("status").asText()).isEqualTo("PAID");
        assertThat(sales.adjust(o,reversal).statusCode()).isEqualTo(200);
        assertThat(sales.adjust(o,Map.of("id",UUID.randomUUID(),"kind","REVERSAL","paymentId",payment,"reason","Another request for the same receipt.")).statusCode()).isEqualTo(409);
        assertThat(sales.adjust(o,Map.of("id",UUID.randomUUID(),"kind","REFUND","amount",1,"refundMethod","CASH","reason","Refund while a cancellation is pending.")).statusCode()).isEqualTo(409);
        assertThat(sales.json(sales.get("/api/staff/finance/adjustments")).toString()).contains(request.toString());
        assertThat(sales.post("/api/staff/finance/adjustments/"+request+"/approve",Map.of()).statusCode()).isEqualTo(403);
        var owner=staff(StaffRole.OWNER);
        assertThat(owner.post("/api/staff/finance/adjustments/"+request+"/approve",Map.of("note","Checked the till")).statusCode()).isEqualTo(204);
        assertThat(owner.post("/api/staff/finance/adjustments/"+request+"/approve",Map.of("note","Checked the till")).statusCode()).isEqualTo(204);
        assertThat(owner.post("/api/staff/finance/adjustments/"+request+"/reject",Map.of("note","Too late")).statusCode()).isEqualTo(409);
        var after=owner.ledger(o);
        assertThat(after.get("balance").get("status").asText()).isEqualTo("UNPAID");
        assertThat(after.get("balance").get("due").decimalValue()).isEqualByComparingTo("50");
        assertThat(after.get("payments").get(0).get("reversed").asBoolean()).isTrue();
        assertThat(after.get("adjustments").get(0).get("decidedBy").asText()).startsWith("Finance OWNER");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.customer_notifications WHERE order_id=? AND title='Receipt cancelled'",Integer.class,o)).isOne();
        assertThatThrownBy(()->jdbc.update("UPDATE app.payment_adjustments SET reason='Changed after approval' WHERE id=?",request)).hasMessageContaining("append-only");
        // A rejected request is closed and changes nothing.
        UUID second=UUID.randomUUID();sales.json(sales.pay(o,cash(second,"50")));
        UUID doubt=UUID.randomUUID();
        sales.json(sales.adjust(o,Map.of("id",doubt,"kind","REVERSAL","paymentId",second,"reason","Not sure this cash reached the till.")));
        assertThat(owner.post("/api/staff/finance/adjustments/"+doubt+"/reject",Map.of()).statusCode()).isEqualTo(400);
        assertThat(owner.post("/api/staff/finance/adjustments/"+doubt+"/reject",Map.of("note","The cash is in the till.")).statusCode()).isEqualTo(204);
        assertThat(owner.ledger(o).get("balance").get("status").asText()).isEqualTo("PAID");
    }
    @Test void aCancelledPaidOrderShowsARefundDueUntilTheOwnerApprovesTheRefund() throws Exception {
        var sales=staff(StaffRole.SALES);UUID o=order(null,"CONFIRMED");
        sales.json(sales.pay(o,cash(UUID.randomUUID(),"20")));
        assertThat(sales.post("/api/staff/orders/"+o+"/cancel",Map.of("reason","The customer changed their mind by phone.")).statusCode()).isEqualTo(200);
        var cancelled=sales.ledger(o);
        assertThat(cancelled.get("balance").get("status").asText()).isEqualTo("REFUND_DUE");
        assertThat(cancelled.get("balance").get("refundDue").decimalValue()).isEqualByComparingTo("20");
        assertThat(sales.pay(o,cash(UUID.randomUUID(),"1")).statusCode()).isEqualTo(409);
        assertThat(sales.json(sales.get("/api/staff/finance/orders?filter=OUTSTANDING&q="+URLEncoder.encode(cancelled.get("reference").asText(),"UTF-8"))).get("total").asInt()).isOne();
        assertThat(sales.adjust(o,Map.of("id",UUID.randomUUID(),"kind","REFUND","amount",21,"refundMethod","CASH","reason","Refund for the cancelled order.")).statusCode()).isEqualTo(409);
        assertThat(sales.adjust(o,Map.of("id",UUID.randomUUID(),"kind","REFUND","amount",20,"refundMethod","MOBILE_MONEY","reason","Refund for the cancelled order.")).statusCode()).isEqualTo(400);
        UUID refund=UUID.randomUUID();
        sales.json(sales.adjust(o,Map.of("id",refund,"kind","REFUND","amount",20,"refundMethod","MOBILE_MONEY","refundProvider","AIRTEL_MONEY","reason","Refund for the cancelled order.")));
        assertThat(sales.ledger(o).get("balance").get("status").asText()).isEqualTo("REFUND_DUE");
        var owner=staff(StaffRole.OWNER);
        assertThat(owner.post("/api/staff/finance/adjustments/"+refund+"/approve",Map.of()).statusCode()).isEqualTo(400);
        String reference=ref();
        assertThat(owner.post("/api/staff/finance/adjustments/"+refund+"/approve",Map.of("reference",reference)).statusCode()).isEqualTo(204);
        var refunded=owner.ledger(o);
        assertThat(refunded.get("balance").get("status").asText()).isEqualTo("REFUNDED");
        assertThat(refunded.get("balance").get("netPaid").decimalValue()).isZero();
        assertThat(jdbc.queryForObject("SELECT message FROM app.customer_notifications WHERE order_id=? AND title='Refund sent'",String.class,o)).contains("Airtel Money",reference);
        assertThat(sales.json(sales.get("/api/staff/finance/orders?filter=OUTSTANDING&q="+URLEncoder.encode(refunded.get("reference").asText(),"UTF-8"))).get("total").asInt()).isZero();
    }
    @Test void customersSeeOnlyTheirOwnReceiptsWithoutStaffDetails() throws Exception {
        var customer=new Browser();customer.token();
        assertThat(customer.post("/api/auth/register",CustomerAuthIT.registration(CustomerAuthIT.phone())).statusCode()).isEqualTo(201);customer.token();
        UUID me=UUID.fromString(customer.json(customer.get("/api/auth/me")).get("id").asText());
        UUID mine=order(me,"CONFIRMED"),theirs=order(null,"CONFIRMED");
        var sales=staff(StaffRole.SALES);
        sales.json(sales.pay(mine,Map.of("id",UUID.randomUUID(),"method","CASH","amount",15,"note","Paid to the driver at the gate")));
        var view=customer.json(customer.get("/api/orders/"+mine+"/payments"));
        assertThat(view.get("balance").get("due").decimalValue()).isEqualByComparingTo("35");
        assertThat(view.get("receipts").get(0).get("receiptNumber").asText()).startsWith("RCT-");
        assertThat(view.toString()).doesNotContain("Paid to the driver","Finance SALES");
        assertThat(customer.get("/api/orders/"+theirs+"/payments").statusCode()).isEqualTo(404);
        assertThat(sales.get("/api/orders/"+mine+"/payments").statusCode()).isIn(401,403);
    }
    @Test void theDaySummarySeparatesMoneyReceivedRefundsAndCompletedSales() throws Exception {
        var sales=staff(StaffRole.SALES);
        String today=LocalDate.now(ZoneId.of("Africa/Dar_es_Salaam")).toString();
        var before=sales.json(sales.get("/api/staff/finance/summary?date="+today));
        UUID o=order(null,"CONFIRMED");
        sales.json(sales.pay(o,cash(UUID.randomUUID(),"20")));
        sales.json(sales.pay(o,mobile(UUID.randomUUID(),"30",ref())));
        jdbc.update("UPDATE app.customer_orders SET status='DELIVERED' WHERE id=?",o);
        jdbc.update("INSERT INTO app.fulfilment_events(order_id,status,branch_code,actor_id,recipient) VALUES(?,'DELIVERED','TARIME',?,'Finance recipient')",o,sales.staffId);
        var after=sales.json(sales.get("/api/staff/finance/summary?date="+today));
        assertThat(after.get("cash").decimalValue().subtract(before.get("cash").decimalValue())).isEqualByComparingTo("20");
        assertThat(after.get("mobileMoney").decimalValue().subtract(before.get("mobileMoney").decimalValue())).isEqualByComparingTo("30");
        assertThat(after.get("completedSales").decimalValue().subtract(before.get("completedSales").decimalValue())).isEqualByComparingTo("50");
        assertThat(after.get("completedOrders").asInt()-before.get("completedOrders").asInt()).isOne();
        assertThat(sales.get("/api/staff/finance/summary?date="+LocalDate.parse(today).plusDays(1)).statusCode()).isEqualTo(400);
        assertThat(staff(StaffRole.INVENTORY).get("/api/staff/finance/summary").statusCode()).isEqualTo(403);
    }
}
