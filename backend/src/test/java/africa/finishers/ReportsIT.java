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
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** E1 / FR-21: separate measures on their own date basis, role-scoped reports and safe CSV export. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportsIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired StaffSecretVault vault;
    @Autowired Clock clock;
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final String PASSWORD="Reports test passphrase 2026!";
    static final String TODAY=LocalDate.now(ZoneId.of("Africa/Dar_es_Salaam")).toString();
    @DynamicPropertySource static void isolated(DynamicPropertyRegistry registry) throws Exception {
        FoundationIT.isolatedDatabase(registry);
        registry.add("FPA_STAFF_MFA_KEY",()->Base64.getEncoder().encodeToString(new byte[32]));
    }
    @BeforeEach void resetThrottle(){jdbc.update("DELETE FROM app.auth_rate_limits");}

    class Browser {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();String csrf;UUID id;
        HttpResponse<String> get(String path) throws Exception {return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));}
        JsonNode json(String path) throws Exception {var r=get(path);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);return JSON.readTree(r.body());}
        HttpResponse<String> post(String path,Object body) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json").header("X-CSRF-TOKEN",csrf)
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
    }
    Browser staff(StaffRole role) throws Exception {
        UUID id=UUID.randomUUID();byte[] secret=role==StaffRole.OWNER?Totp.newSecret():null;
        jdbc.update("INSERT INTO app.staff_accounts(id,username,name,role,password_hash,password_change_required,mfa_secret_encrypted) VALUES(?,?,?,?,?,false,?)",
                id,"reports-"+id,"Reports "+role,role.name(),passwords.encode(PASSWORD),secret==null?null:vault.encrypt(id,secret));
        var b=new Browser();b.id=id;b.csrf=JSON.readTree(b.get("/api/security/csrf").body()).get("token").asText();
        String code=secret==null?"":Totp.code(secret,clock.instant().getEpochSecond()/30,6);
        assertThat(b.post("/api/staff/auth/login",Map.of("username","reports-"+id,"password",PASSWORD,"code",code)).statusCode()).isEqualTo(200);
        b.csrf=JSON.readTree(b.get("/api/security/csrf").body()).get("token").asText();return b;
    }
    record Fixture(UUID order,UUID variant){}
    /** A product with stock 20 at Tarime and an order for 4 of it: 4 x 10 = 40 materials plus 10 delivery = 50. */
    Fixture order(String productName,String status,UUID staffId){
        UUID customer=UUID.randomUUID(),category=UUID.randomUUID(),product=UUID.randomUUID(),variant=UUID.randomUUID(),order=UUID.randomUUID();
        jdbc.update("INSERT INTO app.customer_accounts(id,name,phone,password_hash) VALUES(?,'Report customer',?,'not-a-login-hash')",customer,CustomerAuthIT.phone());
        jdbc.update("INSERT INTO app.categories(id,slug,name,published) VALUES(?,?,'Report category',false)",category,"report-"+category);
        jdbc.update("INSERT INTO app.products(id,sku,slug,category_id,name,description) VALUES(?,?,?,?,?,'Fixture')",product,"REP-"+product,"rep-"+product,category,productName);
        jdbc.update("INSERT INTO app.units(code,label) VALUES('staff-test-unit','piece') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO app.product_variants(id,product_id,sku,label,unit_code,unit_price,currency) VALUES(?,?,?,'Standard','staff-test-unit',10,'TZS')",variant,product,"REP-V-"+variant);
        jdbc.update("INSERT INTO app.stock_balances(variant_id,branch_code,on_hand,reserved,low_stock_threshold) VALUES(?,'TARIME',20,4,18)",variant);
        jdbc.update("INSERT INTO app.customer_orders(id,reference,customer_id,idempotency_key,request_hash,status,fulfilment,contact_name,contact_phone,address,currency,subtotal,delivery_fee,total,branch_code) VALUES(?,?,?,?,?,?,'DELIVERY','Report customer','+255712345678','Plot 4','TZS',40,10,50,'TARIME')",
                order,"FPA-"+order.toString().replace("-","").toUpperCase(),customer,UUID.randomUUID(),"0".repeat(64),status);
        jdbc.update("INSERT INTO app.customer_order_items(order_id,variant_id,product_id,product_name,variant_label,sku,unit,quantity,unit_price,line_total) VALUES(?,?,?,?,'Standard',?,'piece',4,10,40)",order,variant,product,productName,"REP-V-"+variant);
        jdbc.update("INSERT INTO app.order_confirmations(order_id,actor_id,branch_code,delivery_fee,agreed_total,agreement_note) VALUES(?,?,'TARIME',10,50,'Customer agreed by phone')",order,staffId);
        jdbc.update("INSERT INTO app.stock_reservations VALUES(?,?,'TARIME',4,4)",order,variant);
        return new Fixture(order,variant);
    }
    void pay(UUID order,String method,String amount,UUID staffId){
        jdbc.update("INSERT INTO app.payments(id,order_id,receipt_number,method,provider,reference,amount,currency,recorded_by) VALUES(?,?,?,?,?,?,?,'TZS',?)",UUID.randomUUID(),order,
                "RCT-T-"+UUID.randomUUID().toString().substring(0,8),method,method.equals("CASH")?null:"MPESA",method.equals("CASH")?null:"RP"+UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase(),new BigDecimal(amount),staffId);
    }
    static BigDecimal d(JsonNode node,String path){JsonNode n=node;for(String p:path.split("\\."))n=n.get(p);return n.decimalValue();}

    @Test void salesSeparatesRequestedValueCompletedSalesMoneyAndRefunds() throws Exception {
        var sales=staff(StaffRole.SALES);
        String range="?from="+TODAY+"&to="+TODAY;
        var before=sales.json("/api/staff/reports/sales"+range);
        var f=order("Report cement","CONFIRMED",sales.id);
        pay(f.order(),"CASH","20",sales.id);pay(f.order(),"MOBILE_MONEY","30",sales.id);
        jdbc.update("UPDATE app.customer_orders SET status='DELIVERED' WHERE id=?",f.order());
        jdbc.update("INSERT INTO app.fulfilment_events(order_id,status,branch_code,actor_id,recipient) VALUES(?,'DELIVERED','TARIME',?,'Report recipient')",f.order(),sales.id);
        jdbc.update("INSERT INTO app.payment_adjustments(id,order_id,kind,amount,reason,refund_method,status,requested_by,decided_by,decided_at) VALUES(?,?,'REFUND',5,'One piece returned unused by the customer.','CASH','APPROVED',?,?,now())",UUID.randomUUID(),f.order(),sales.id,sales.id);
        var after=sales.json("/api/staff/reports/sales"+range);
        assertThat(after.get("orders").get("submitted").asLong()-before.get("orders").get("submitted").asLong()).isOne();
        assertThat(d(after,"orders.requestedValue").subtract(d(before,"orders.requestedValue"))).isEqualByComparingTo("40");
        assertThat(after.get("orders").get("confirmed").asLong()-before.get("orders").get("confirmed").asLong()).isOne();
        assertThat(d(after,"sales.gross").subtract(d(before,"sales.gross"))).isEqualByComparingTo("50");
        assertThat(d(after,"sales.refunds").subtract(d(before,"sales.refunds"))).isEqualByComparingTo("5");
        assertThat(d(after,"sales.net").subtract(d(before,"sales.net"))).isEqualByComparingTo("45");
        assertThat(d(after,"money.cash").subtract(d(before,"money.cash"))).isEqualByComparingTo("20");
        assertThat(d(after,"money.mobileMoney").subtract(d(before,"money.mobileMoney"))).isEqualByComparingTo("30");
        assertThat(d(after,"money.net").subtract(d(before,"money.net"))).isEqualByComparingTo("45");
        assertThat(after.get("days")).hasSize(1);
        assertThat(after.get("days").get(0).get("date").asText()).isEqualTo(TODAY);
        assertThat(after.get("topProducts").toString()).contains("Report cement");
        var week=sales.json("/api/staff/reports/sales");
        assertThat(week.get("days")).hasSize(7);
    }
    @Test void eachRoleSeesOnlyItsReportsAndDatesAreChecked() throws Exception {
        var sales=staff(StaffRole.SALES);var inventory=staff(StaffRole.INVENTORY);var driver=staff(StaffRole.DELIVERY);var owner=staff(StaffRole.OWNER);
        for(String path:List.of("/api/staff/reports/sales","/api/staff/reports/balances","/api/staff/reports/stock","/api/staff/reports/sales.csv"))
            assertThat(owner.get(path).statusCode()).as(path).isEqualTo(200);
        assertThat(sales.get("/api/staff/reports/balances").statusCode()).isEqualTo(200);
        assertThat(sales.get("/api/staff/reports/stock").statusCode()).isEqualTo(403);
        assertThat(inventory.get("/api/staff/reports/stock").statusCode()).isEqualTo(200);
        assertThat(inventory.get("/api/staff/reports/sales").statusCode()).isEqualTo(403);
        assertThat(inventory.get("/api/staff/reports/balances.csv").statusCode()).isEqualTo(403);
        for(String path:List.of("/api/staff/reports/sales","/api/staff/reports/stock","/api/staff/reports/balances"))
            assertThat(driver.get(path).statusCode()).as(path).isEqualTo(403);
        LocalDate today=LocalDate.parse(TODAY);
        assertThat(sales.get("/api/staff/reports/sales?from="+today+"&to="+today.minusDays(1)).statusCode()).isEqualTo(400);
        assertThat(sales.get("/api/staff/reports/sales?to="+today.plusDays(1)).statusCode()).isEqualTo(400);
        assertThat(sales.get("/api/staff/reports/sales?from="+today.minusDays(400)+"&to="+today).statusCode()).isEqualTo(400);
        assertThat(sales.get("/api/staff/reports/sales?from=13-11-2026").statusCode()).isEqualTo(400);
        assertThat(new Browser().get("/api/staff/reports/sales").statusCode()).isIn(401,403);
    }
    @Test void balancesListMoneyOwedEitherWay() throws Exception {
        var sales=staff(StaffRole.SALES);
        var owing=order("Report owing","CONFIRMED",sales.id);pay(owing.order(),"CASH","15",sales.id);
        var cancelled=order("Report cancelled","CANCELLED",sales.id);pay(cancelled.order(),"CASH","12",sales.id);
        var r=sales.json("/api/staff/reports/balances");
        var rows=new HashMap<String,JsonNode>();for(var row:r.get("rows"))rows.put(row.get("orderId").asText(),row);
        assertThat(d(rows.get(owing.order().toString()),"due")).isEqualByComparingTo("35");
        assertThat(d(rows.get(cancelled.order().toString()),"refundDue")).isEqualByComparingTo("12");
        assertThat(d(r,"owedToShop")).isGreaterThanOrEqualTo(new BigDecimal("35"));
    }
    @Test void stockShowsLevelsMovementsAndSafeCsv() throws Exception {
        var owner=staff(StaffRole.OWNER);
        var f=order("=HYPERLINK(\"http://evil\")","CONFIRMED",owner.id);
        jdbc.update("INSERT INTO app.stock_receipts(id,actor_id,variant_id,branch_code,product_name,variant_label,sku,unit_label,quantity,source_reference,reason,on_hand_after,reserved_after) VALUES(?,?,?,'TARIME','x','Standard','s','piece',6,'GRN-7','Delivery from supplier',20,4)",UUID.randomUUID(),owner.id,f.variant());
        jdbc.update("INSERT INTO app.fulfilment_events(order_id,status,branch_code,actor_id) VALUES(?,'OUT_FOR_DELIVERY','TARIME',?)",f.order(),owner.id);
        var r=owner.json("/api/staff/reports/stock");
        JsonNode row=null;for(var x:r.get("rows"))if(x.get("variantId").asText().equals(f.variant().toString()))row=x;
        assertThat(row).isNotNull();
        assertThat(d(row,"available")).isEqualByComparingTo("16");
        assertThat(row.get("lowStock").asBoolean()).isTrue();
        assertThat(d(row,"valueAtPrice")).isEqualByComparingTo("200");
        assertThat(d(row,"received")).isEqualByComparingTo("6");
        assertThat(d(row,"dispatched")).isEqualByComparingTo("4");
        var csv=owner.get("/api/staff/reports/stock.csv");
        assertThat(csv.statusCode()).isEqualTo(200);
        assertThat(csv.headers().firstValue("Content-Type").orElse("")).startsWith("text/csv");
        assertThat(csv.headers().firstValue("Content-Disposition").orElse("")).contains("attachment").contains(".csv");
        assertThat(csv.body()).startsWith("﻿\"Product\"");
        assertThat(csv.body()).contains("\"'=HYPERLINK(\"\"http://evil\"\")\"").doesNotContain(",\"=HYPERLINK");
    }
}
