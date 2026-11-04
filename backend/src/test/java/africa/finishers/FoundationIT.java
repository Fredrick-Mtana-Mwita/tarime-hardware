package africa.finishers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.jdbc.Sql("classpath:db/dev/R__sample_catalogue.sql")
class FoundationIT {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) throws IOException {
        Properties properties = new Properties();
        Path file = Files.exists(Path.of(".env.test")) ? Path.of(".env.test") : Path.of("backend/.env.test");
        try (var reader = Files.newBufferedReader(file)) { properties.load(reader); }
        String url = properties.getProperty("FPA_TEST_DB_URL", "");
        String user = properties.getProperty("FPA_TEST_DB_USER", "");
        if (!url.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/finishers_test")
                || !user.equals("finishers_test")) {
            throw new IllegalStateException("Integration tests require the isolated loopback finishers_test database and role");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> properties.getProperty("FPA_TEST_DB_PASSWORD"));
        registry.add("spring.config.import", () -> "");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private tools.jackson.databind.JsonNode json(String path) throws Exception {
        var response=get(path);
        assertThat(response.statusCode()).isEqualTo(200);
        return tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body());
    }

    @Test
    void catalogueSearchFilterSortAndPaginationUseDatabaseValues() throws Exception {
        var all=json("/api/products");
        assertThat(all.get("total").asInt()).isEqualTo(12);
        assertThat(json("/api/categories").size()).isEqualTo(17);
        var cement=json("/api/products?q=TWIGA&category=cement&minPrice=18000&maxPrice=19000");
        assertThat(cement.get("total").asInt()).isEqualTo(1);
        assertThat(cement.get("items").get(0).get("price").decimalValue()).isEqualByComparingTo("18500");
        var first=json("/api/products?sort=price-asc&size=2");
        var next=json("/api/products?sort=price-asc&size=2&page=1");
        assertThat(first.get("items").size()).isEqualTo(2);
        assertThat(first.get("items").get(0).get("id").asText()).isNotEqualTo(next.get("items").get(0).get("id").asText());
        assertThat(json("/api/products?q=%25").get("total").asInt()).isZero();
        assertThat(json("/api/products?q=%27%20OR%201%3D1--").get("total").asInt()).isZero();
        assertThat(get("/api/products?size=49").statusCode()).isEqualTo(400);
        assertThat(get("/api/products?page=-1").statusCode()).isEqualTo(400);
        assertThat(get("/api/products?sort=unsafe").statusCode()).isEqualTo(400);
        assertThat(get("/api/products?minPrice=20&maxPrice=10").statusCode()).isEqualTo(400);
    }

    @Test
    void visibilityRulesProtectListsCountsAndDirectProductAccess() throws Exception {
        String product="00000000-0000-4000-8000-000000000100";
        assertThat(json("/api/products/"+product).get("variants").size()).isEqualTo(2);
        try {
            jdbc.update("update app.products set published=false where id=?::uuid",product);
            assertThat(get("/api/products/"+product).statusCode()).isEqualTo(404);
            assertThat(json("/api/products?q=TWIGA").get("total").asInt()).isZero();
            jdbc.update("update app.products set published=true where id=?::uuid",product);
            jdbc.update("update app.categories set published=false where slug='cement'");
            assertThat(get("/api/products/"+product).statusCode()).isEqualTo(404);
            assertThat(json("/api/categories").size()).isEqualTo(16);
            assertThat(json("/api/products?category=cement").get("total").asInt()).isZero();
        } finally {
            jdbc.update("update app.products set published=true where id=?::uuid",product);
            jdbc.update("update app.categories set published=true where slug='cement'");
        }
        assertThat(get("/api/products/00000000-0000-4000-8000-999999999999").statusCode()).isEqualTo(404);
    }

    @Test
    void availabilitySubtractsReservationsAndReadApisDoNotGrantWrites() throws Exception {
        String variant="00000000-0000-4000-8000-000000000203";
        try {
            jdbc.update("update app.stock_balances set reserved=on_hand where variant_id=?::uuid",variant);
            var detail=json("/api/products/00000000-0000-4000-8000-000000000103");
            assertThat(detail.get("variants").get(0).get("available").decimalValue()).isEqualByComparingTo("0");
            assertThat(detail.get("product").get("inStock").asBoolean()).isFalse();
            assertThat(json("/api/products?q=Filament&inStock=true").get("total").asInt()).isZero();
        } finally {jdbc.update("update app.stock_balances set reserved=0 where variant_id=?::uuid",variant);}
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/products"))
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void migratesRealPostgresAndExposesOnlySafePublicMetadata() throws Exception {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("finishers_test");
        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("finishers_test");
        assertThat(jdbc.queryForObject("select count(*) from app.flyway_schema_history where success", Integer.class)).isPositive();
        var response = get("/api/system/info");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Tarime Hardware", "\"zeroBudget\":true", "\"externalMessagingEnabled\":false")
                .doesNotContain("password", "jdbc:");
        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);
    }

    @Test
    void rejectsAnonymousProtectedReadsAndCsrfLessWrites() throws Exception {
        assertThat(get("/api/admin/orders").statusCode()).isEqualTo(401);
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:"+port+"/api/orders"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(get("/actuator/env").statusCode()).isEqualTo(401);
    }

    @Test
    void issuesCsrfTokenAndSessionCookieWithoutAuthenticating() throws Exception {
        var response = get("/api/security/csrf");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("X-CSRF-TOKEN", "token");
        assertThat(response.headers().allValues("set-cookie")).anyMatch(cookie -> cookie.contains("HttpOnly"));
    }

    @Test
    void auditHistoryRejectsMutationAndCanRollbackTestData() throws Exception {
        UUID id=UUID.randomUUID();
        try (var connection=dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement=connection.prepareStatement("insert into app.audit_events(event_id,occurred_at,actor_type,event_type,target_type,target_reference) values (?,now(),'SYSTEM','FOUNDATION_CHECK','TEST',?)")) {
                statement.setObject(1,id);statement.setString(2,id.toString());statement.executeUpdate();
            }
            assertThatThrownBy(() -> {
                try (var statement=connection.prepareStatement("delete from app.audit_events where event_id=?")) {
                    statement.setObject(1,id);statement.executeUpdate();
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("append-only");
            connection.rollback();
        }
        assertThat(jdbc.queryForObject("select count(*) from app.audit_events where event_id=?",Integer.class,id)).isZero();
    }
}
