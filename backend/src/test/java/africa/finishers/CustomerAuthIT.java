package africa.finishers;

import africa.finishers.identity.application.AuthenticationThrottle;
import africa.finishers.identity.application.AuthProblem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerAuthIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthenticationThrottle throttle;
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final String PASSWORD="A local testing passphrase 2026!";
    @DynamicPropertySource static void isolated(DynamicPropertyRegistry registry) throws Exception {FoundationIT.isolatedDatabase(registry);}
    @org.junit.jupiter.api.BeforeEach
    void resetTestOnlyThrottleBuckets(){jdbc.update("delete from app.auth_rate_limits");}

    class Browser {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).cookieHandler(cookies).build();
        String token;
        HttpResponse<String> get(String path) throws Exception {return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
        JsonNode csrf() throws Exception {var result=get("/api/security/csrf");assertThat(result.statusCode()).isEqualTo(200);var value=JSON.readTree(result.body());token=value.get("token").asText();return value;}
        HttpResponse<String> post(String path,Object body,boolean includeToken) throws Exception {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json");
            if(includeToken)request.header("X-CSRF-TOKEN",token);
            return http.send(request.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        String sessionId(){return cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();}
        HttpResponse<String> register(String phone) throws Exception {csrf();return post("/api/auth/register",registration(phone),true);}
    }
    static String phone(){return "+2557"+String.format("%08d",new java.security.SecureRandom().nextInt(100000000));}
    static Map<String,String> registration(String phone){return Map.of("name","Test Customer","phone",phone,"email","","password",PASSWORD);}

    @Test void registrationHashesPasswordsCreatesOnlyCustomerAndAuditsWithoutSecrets() throws Exception {
        var browser=new Browser();String phone=phone();
        assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);
        assertThat(browser.post("/api/auth/register",registration(phone),false).statusCode()).isEqualTo(403);
        browser.csrf();String previousSession=browser.sessionId();
        var body=new java.util.HashMap<>(registration(phone));body.put("role","OWNER");body.put("active","true");body.put("contactVerified","true");
        var registered=browser.post("/api/auth/register",body,true);
        assertThat(registered.statusCode()).isEqualTo(201);
        var profile=JSON.readTree(registered.body());
        assertThat(profile.get("contactVerified").asBoolean()).isFalse();
        assertThat(registered.body()).doesNotContain(PASSWORD,"password","OWNER","passwordHash");
        assertThat(browser.sessionId()).isNotEqualTo(previousSession);
        String hash=jdbc.queryForObject("select password_hash from app.customer_accounts where phone=?",String.class,phone);
        assertThat(hash).startsWith("{pbkdf2}").isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD,hash)).isTrue();
        assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(200);
        assertThat(browser.get("/api/admin/orders").statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from app.audit_events where event_type='CUSTOMER_REGISTERED' and target_reference=?",Integer.class,profile.get("id").asText())).isEqualTo(1);
    }

    @Test void validatesRegistrationAndEnforcesNormalizedPhoneUniqueness() throws Exception {
        var browser=new Browser();browser.csrf();String phone=phone();
        var weak=new java.util.HashMap<>(registration(phone));weak.put("password","short");
        var invalid=browser.post("/api/auth/register",weak,true);
        assertThat(invalid.statusCode()).isEqualTo(400);assertThat(invalid.body()).doesNotContain("short");
        assertThat(browser.register(phone).statusCode()).isEqualTo(201);
        browser.csrf();
        assertThat(browser.post("/api/auth/register",registration("0"+phone.substring(4)),true).statusCode()).isEqualTo(409);
    }

    @Test void csrfRotationLogoutAndLoginPersistOnlyThroughPrivateSession() throws Exception {
        var browser=new Browser();String phone=phone();browser.csrf();String stale=browser.token;
        assertThat(browser.post("/api/auth/register",registration(phone),true).statusCode()).isEqualTo(201);
        browser.token=stale;
        assertThat(browser.post("/api/auth/logout",Map.of(),true).statusCode()).isEqualTo(403);
        browser.csrf();assertThat(browser.post("/api/auth/logout",Map.of(),true).statusCode()).isEqualTo(204);
        assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);
        browser.csrf();String beforeLogin=browser.sessionId();
        var bad=browser.post("/api/auth/login",Map.of("phone",phone,"password","wrong"),true);
        assertThat(bad.statusCode()).isEqualTo(401);
        assertThat(bad.body()).doesNotContain(phone,"wrong");
        assertThat(browser.post("/api/auth/login",Map.of("phone",phone,"password",PASSWORD),true).statusCode()).isEqualTo(200);
        assertThat(browser.sessionId()).isNotEqualTo(beforeLogin);
        assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(200);
    }

    @Test void disablingAccountImmediatelyRevokesExistingSession() throws Exception {
        var browser=new Browser();String phone=phone();assertThat(browser.register(phone).statusCode()).isEqualTo(201);
        jdbc.update("update app.customer_accounts set active=false where phone=?",phone);
        assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);
        browser.csrf();assertThat(browser.post("/api/auth/login",Map.of("phone",phone,"password",PASSWORD),true).statusCode()).isEqualTo(401);
    }

    @Test void customersCannotChooseAnotherAccountOrWriteCatalogue() throws Exception {
        var a=new Browser();var b=new Browser();String first=phone();String second=phone();
        assertThat(a.register(first).statusCode()).isEqualTo(201);assertThat(b.register(second).statusCode()).isEqualTo(201);
        var other=JSON.readTree(b.get("/api/auth/me").body());
        var own=a.get("/api/auth/me?customerId="+other.get("id").asText());
        assertThat(JSON.readTree(own.body()).get("phone").asText()).isEqualTo(first);
        a.csrf();assertThat(a.post("/api/products",Map.of("name","Unauthorized"),true).statusCode()).isEqualTo(403);
        assertThat(a.get("/api/admin/staff").statusCode()).isEqualTo(403);
    }

    @Test void authenticationThrottleHasAtomicSharedDatabaseLimit() {
        String address="test-"+UUID.randomUUID();
        for(int i=0;i<30;i++)throttle.check(AuthenticationThrottle.Operation.LOGIN,address);
        assertThatThrownBy(()->throttle.check(AuthenticationThrottle.Operation.LOGIN,address)).isInstanceOfSatisfying(AuthProblem.class,e->assertThat(e.status()).isEqualTo(429));
        assertThat(jdbc.queryForObject("select max(length(client_key)) from app.auth_rate_limits",Integer.class)).isEqualTo(64);
    }
}
