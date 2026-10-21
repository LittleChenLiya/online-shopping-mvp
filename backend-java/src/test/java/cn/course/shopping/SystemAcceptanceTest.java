package cn.course.shopping;

import cn.course.shopping.account.Passwords;
import cn.course.shopping.common.Database;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static cn.course.shopping.common.Database.*;
import static org.junit.jupiter.api.Assertions.*;

/** 庄云月：通过真实 HTTP + 独立临时 SQLite 执行跨层验收，不直接调用 Controller。 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class SystemAcceptanceTest {
    static final Path TEMP = tempDirectory();
    static final String PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=";
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    @LocalServerPort int port;
    @Autowired Database db;
    @Autowired ObjectMapper json;
    String cookie;
    record Result(int status, JsonNode body, HttpResponse<String> response) {}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("shop.db-path", () -> TEMP.resolve("acceptance.db").toString());
        registry.add("shop.initial-password", () -> "seller12345");
    }
    static Path tempDirectory() {
        try { return Files.createTempDirectory("shopping-java-test-"); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    @BeforeEach void reset() {
        db.write(connection -> {
            update(connection, "DELETE FROM sessions");
            update(connection, "DELETE FROM trade_events");
            update(connection, "UPDATE products SET selected_intent_id=NULL");
            update(connection, "DELETE FROM intents");
            update(connection, "DELETE FROM products");
            update(connection, "UPDATE seller SET password_hash=? WHERE id=1", Passwords.hash("seller12345"));
            return null;
        });
        cookie = "";
    }
    @AfterAll static void cleanup() throws Exception {
        try (var files = Files.walk(TEMP)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
    HttpResponse<String> raw(String path, String method, String body, Map<String, String> headers) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(20));
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    Result request(String path, Object data) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json"); headers.put("X-Requested-With", "ShopApp");
        if (!cookie.isEmpty()) headers.put("Cookie", cookie);
        var response = raw(path, data == null ? "GET" : "POST", data == null ? null : json.writeValueAsString(data), headers);
        return new Result(response.statusCode(), json.readTree(response.body()), response);
    }
    void login() throws Exception { login("seller12345"); }
    void login(String password) throws Exception {
        Result result = request("/api/login", Map.of("username", "seller", "password", password));
        assertEquals(200, result.status, result.body.toString());
        String header = result.response.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(header.contains("HttpOnly")); assertTrue(header.contains("SameSite=Strict"));
        cookie = header.split(";", 2)[0];
    }
    Map<String, Object> product(Map<String, Object> changes) {
        Map<String, Object> data = new HashMap<>(Map.of("name", "手作杯", "description", "唯一的一件", "price", "19.90", "image", PNG));
        data.putAll(changes); return data;
    }
    Result publish() throws Exception { return request("/api/products", product(Map.of())); }
    long prepare() throws Exception { login(); Result result = publish(); assertEquals(200, result.status); return result.body.path("id").asLong(); }
    Result intent(long id, String phone) throws Exception {
        return request("/api/intents", Map.of("product_id", id, "buyer_name", "买家", "contact", phone, "note", "周末联系"));
    }
    Result trade(long id, String action, Long intent) throws Exception {
        Map<String, Object> data = new HashMap<>(Map.of("product_id", id, "action", action));
        if (intent != null) data.put("intent_id", intent);
        return request("/api/trade", data);
    }
    @Test void fullWorkflowAndHistory() throws Exception {
        assertTrue(request("/api/product", null).body.path("product").isNull());
        long id = prepare();
        assertEquals(409, publish().status);
        JsonNode publicProduct = request("/api/product", null).body.path("product");
        assertEquals(1990, publicProduct.path("price_cents").asInt()); assertFalse(publicProduct.has("selected_intent_id"));
        long first = intent(id, "13800138000").body.path("id").asLong();
        long second = intent(id, "13900139000").body.path("id").asLong();
        assertEquals(409, trade(id, "success", null).status);
        assertEquals(200, trade(id, "freeze", first).status);
        assertEquals(409, intent(id, "13700137000").status);
        assertEquals(409, publish().status);
        assertEquals(409, trade(id, "freeze", second).status);
        assertEquals(200, trade(id, "failure", null).status);
        assertEquals("active", request("/api/product", null).body.path("product").path("status").asText());
        assertEquals(200, trade(id, "freeze", second).status);
        assertEquals(200, trade(id, "success", null).status);
        assertTrue(request("/api/product", null).body.path("product").isNull());
        assertEquals(409, trade(id, "failure", null).status);
        JsonNode result = request("/api/trades", null).body;
        assertEquals(4, result.path("events").size());
        assertEquals("completed", result.path("intents").get(0).path("status").asText());
        assertEquals("closed", result.path("intents").get(1).path("status").asText());
        JsonNode history = request("/api/products", null).body.path("products").get(0);
        assertEquals("sold", history.path("status").asText()); assertFalse(history.path("sold_at").isNull());
        long next = publish().body.path("id").asLong();
        assertEquals(409, trade(next, "freeze", first).status);
    }
    @Test void validationAndDuplicateIntent() throws Exception {
        login();
        for (String price : List.of("0", "-1", "NaN", "1.001", "10000000", "abc"))
            assertEquals(400, request("/api/products", product(Map.of("price", price))).status, price);
        for (String image : List.of("data:image/svg+xml;base64,AAAA", "data:image/png;base64,AAAA"))
            assertEquals(400, request("/api/products", product(Map.of("image", image))).status);
        assertEquals(400, request("/api/products", product(Map.of("name", " "))).status);
        long id = publish().body.path("id").asLong();
        assertEquals(200, intent(id, "13800138000").status);
        assertEquals(409, intent(id, "138-0013-8000").status);
        assertEquals(400, intent(id, "abcabc").status);
        assertEquals(400, intent(id, "138+00138000").status);
        assertEquals(409, intent(999999, "13800138000").status);
    }
    @Test void authenticationPasswordAndLogout() throws Exception {
        assertEquals(401, request("/api/products", null).status);
        assertEquals(401, request("/api/trades", null).status);
        assertEquals(401, publish().status);
        assertEquals(401, trade(1, "freeze", 1L).status);
        assertEquals(401, request("/api/password", Map.of("old_password", "x", "new_password", "newpass123")).status);
        assertEquals(401, request("/api/login", Map.of("username", "seller", "password", "wrong")).status);
        login(); String oldCookie = cookie; login();
        assertEquals(400, request("/api/password", Map.of("old_password", "wrong", "new_password", "newpass123")).status);
        assertEquals(400, request("/api/password", Map.of("old_password", "seller12345", "new_password", "short")).status);
        assertEquals(400, request("/api/password", Map.of("old_password", "seller12345", "new_password", "seller12345")).status);
        assertEquals(200, request("/api/password", Map.of("old_password", "seller12345", "new_password", "newpass123")).status);
        assertFalse(request("/api/account", null).body.path("logged_in").asBoolean());
        cookie = oldCookie;
        assertEquals(401, request("/api/products", null).status);
        assertEquals(401, request("/api/login", Map.of("username", "seller", "password", "seller12345")).status);
        login("newpass123");
        String hash = db.read(c -> (String) one(c, "SELECT password_hash FROM seller").get("password_hash"));
        assertFalse(hash.contains("newpass123"));
        assertEquals(200, request("/api/logout", Map.of()).status);
        assertFalse(request("/api/account", null).body.path("logged_in").asBoolean());
    }
    @Test void expiredAndForgedSessions() throws Exception {
        login();
        db.write(c -> { update(c, "UPDATE sessions SET expires_at=0"); return null; });
        assertEquals(401, request("/api/products", null).status);
        cookie = "shop_session=forged";
        assertFalse(request("/api/account", null).body.path("logged_in").asBoolean());
    }
    @Test void requestGuards() throws Exception {
        String data = "{\"username\":\"seller\",\"password\":\"seller12345\"}";
        assertEquals(403, raw("/api/login", "POST", data, Map.of("Content-Type", "application/json")).statusCode());
        assertEquals(403, raw("/api/login", "POST", data, Map.of("Content-Type", "application/json", "X-Requested-With", "ShopApp", "Origin", "http://evil.example")).statusCode());
        assertEquals(415, raw("/api/login", "POST", data, Map.of("Content-Type", "text/plain", "X-Requested-With", "ShopApp")).statusCode());
        assertEquals(404, request("/api/missing", null).status);
        assertEquals(400, request("/api/intents", Map.of("product_id", true, "buyer_name", "张", "contact", "13800138000")).status);
    }
    @Test void malformedJsonAndBodyLimit() throws Exception {
        Map<String, String> headers = Map.of("Content-Type", "application/json", "X-Requested-With", "ShopApp");
        for (String body : List.of("{", "[]", "null", "{} {}", "{\"x\":NaN}"))
            assertEquals(400, raw("/api/login", "POST", body, headers).statusCode(), body);
        assertEquals(413, raw("/api/login", "POST", "", headers).statusCode());
        assertEquals(413, raw("/api/login", "POST", "x".repeat(3 * 1024 * 1024 + 1), headers).statusCode());
    }
    @Test void concurrentPublish() throws Exception {
        login(); assertEquals(List.of(200, 409), compete(() -> publish().status, () -> publish().status));
        assertEquals(1, request("/api/products", null).body.path("products").size());
    }
    @Test void concurrentDuplicateIntent() throws Exception {
        long id = prepare();
        assertEquals(List.of(200, 409), compete(() -> intent(id, "13800138000").status, () -> intent(id, "13800138000").status));
        assertEquals(1, request("/api/trades", null).body.path("intents").size());
    }
    @Test void concurrentFreezeSelectsOneBuyer() throws Exception {
        long id = prepare(); long first = intent(id, "13800138000").body.path("id").asLong();
        long second = intent(id, "13900139000").body.path("id").asLong();
        assertEquals(List.of(200, 409), compete(() -> trade(id, "freeze", first).status, () -> trade(id, "freeze", second).status));
        JsonNode result = request("/api/trades", null).body;
        assertEquals("frozen", result.path("product").path("status").asText()); assertEquals(1, result.path("events").size());
        int selected = 0;
        for (JsonNode intent : result.path("intents")) if (intent.path("status").asText().equals("selected")) {
            selected++; assertEquals(intent.path("id").asLong(), result.path("product").path("selected_intent_id").asLong());
        }
        assertEquals(1, selected);
    }
    @Test void conflictingTradeResultsAreConsistent() throws Exception {
        long id = prepare(); long buyer = intent(id, "13800138000").body.path("id").asLong();
        assertEquals(200, trade(id, "freeze", buyer).status);
        assertEquals(List.of(200, 409), compete(() -> trade(id, "success", null).status, () -> trade(id, "failure", null).status));
        JsonNode result = request("/api/trades", null).body;
        assertEquals(2, result.path("events").size());
        String winner = result.path("events").get(0).path("action").asText();
        JsonNode history = request("/api/products", null).body.path("products").get(0);
        if (winner.equals("success")) {
            assertTrue(result.path("product").isNull()); assertEquals("sold", history.path("status").asText());
            assertEquals("completed", result.path("intents").get(0).path("status").asText());
        } else {
            assertEquals("active", history.path("status").asText()); assertTrue(result.path("product").path("selected_intent_id").isNull());
            assertEquals("pending", result.path("intents").get(0).path("status").asText());
        }
    }
    @Test void rejectedOperationDoesNotChangeState() throws Exception {
        long id = prepare(); intent(id, "13800138000");
        JsonNode before = request("/api/trades", null).body;
        assertEquals(409, trade(id, "freeze", 999999L).status);
        assertEquals(before, request("/api/trades", null).body);
        assertEquals(404, trade(999999, "freeze", 1L).status);
        assertEquals(400, trade(id, "unknown", null).status);
    }
    @Test void staticFilesAndPrivatePaths() throws Exception {
        for (String path : List.of("/", "/index.html", "/app.js", "/api.js", "/style.css")) {
            var response = raw(path, "GET", null, Map.of());
            assertEquals(200, response.statusCode(), path);
            assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
            assertFalse(response.body().isBlank());
        }
        for (String path : List.of("/data/shop.db", "/README.md", "/backend-java/pom.xml", "/legacy/python-stage1/start.py", "/api/../README.md"))
            assertEquals(404, raw(path, "GET", null, Map.of()).statusCode(), path);
    }
    @Test void limitsDoNotCreateProduct() throws Exception {
        login();
        for (Map<String, Object> change : List.of(Map.<String, Object>of("name", "名".repeat(81)), Map.<String, Object>of("description", "描".repeat(2001))))
            assertEquals(400, request("/api/products", product(change)).status);
        byte[] oversized = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(new byte[]{(byte)137,80,78,71,13,10,26,10}, 0, oversized, 0, 8);
        assertEquals(400, request("/api/products", product(Map.of("image", "data:image/png;base64," + Base64.getEncoder().encodeToString(oversized)))).status);
        assertTrue(request("/api/product", null).body.path("product").isNull());
        assertEquals(0, request("/api/products", null).body.path("products").size());
    }
    @Test void specialCharactersSavedAsData() throws Exception {
        login(); String name = "杯'; DROP TABLE products;-- <script>alert(1)</script>";
        Result result = request("/api/products", product(Map.of("name", name)));
        assertEquals(200, result.status);
        assertEquals(name, request("/api/product", null).body.path("product").path("name").asText());
        assertEquals(200, request("/api/intents", Map.of("product_id", result.body.path("id").asLong(), "buyer_name", "O'Reilly", "contact", "13800138000")).status);
        assertEquals("O'Reilly", request("/api/trades", null).body.path("intents").get(0).path("buyer_name").asText());
    }
    @Test void reinitializeKeepsDataAndChangedPassword() throws Exception {
        long id = prepare();
        assertEquals(200, request("/api/password", Map.of("old_password", "seller12345", "new_password", "changed123")).status);
        db.initialize(Passwords.hash("different123"));
        assertEquals(id, request("/api/product", null).body.path("product").path("id").asLong());
        login("changed123");
    }
    @Test void rejectsWrongJsonFieldTypesAndCountsUnicodeCharacters() throws Exception {
        login();
        assertEquals(400, request("/api/products", product(Map.of("name", 123))).status);
        assertEquals(400, request("/api/products", product(Map.of("price", 19.9))).status);
        Result result = request("/api/products", product(Map.of("name", "😀".repeat(80))));
        assertEquals(200, result.status);
        for (Object id : List.of(true, "1", 1.0, 0, -1))
            assertEquals(400, request("/api/intents", Map.of("product_id", id, "buyer_name", "张", "contact", "13800138000")).status);
    }
    private List<Integer> compete(Callable<Integer> first, Callable<Integer> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> a = pool.submit(() -> { start.await(); return first.call(); });
            Future<Integer> b = pool.submit(() -> { start.await(); return second.call(); });
            start.countDown(); return java.util.stream.Stream.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)).sorted().toList();
        } finally { pool.shutdownNow(); }
    }
}
