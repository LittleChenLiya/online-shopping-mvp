package cn.course.shopping;

import cn.course.shopping.account.*;
import cn.course.shopping.buyer.BuyerService;
import cn.course.shopping.common.Database;
import cn.course.shopping.product.ProductService;
import cn.course.shopping.trade.TradeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LegacyDatabaseCompatibilityTest {
    @TempDir Path temp;
    @Test void continuesFrozenTradeFromActualPythonDatabaseWithoutResettingPassword() throws Exception {
        Path copy = temp.resolve("legacy-copy.db");
        try (var fixture = getClass().getResourceAsStream("/legacy-stage1.sqlite")) {
            assertNotNull(fixture); Files.copy(fixture, copy);
        }
        Database db = new Database(copy.toString());
        AccountService accounts = new AccountService(db, "different-initial-password");
        accounts.initialize(); // 不覆盖旧的seller密码或数据。
        ObjectMapper json = new ObjectMapper();
        String token = accounts.login(json.valueToTree(Map.of("username", "seller", "password", "卖家🔒密码")));
        BuyerService buyers = new BuyerService(db);
        Map<?, ?> product = (Map<?, ?>) buyers.current().get("product");
        assertEquals("旧版手作杯", product.get("name")); assertEquals("frozen", product.get("status"));
        assertEquals(1990, ((Number) product.get("price_cents")).intValue());
        assertEquals("2026-10-15 10:00:00", product.get("created_at"));
        assertFalse(product.containsKey("selected_intent_id"));
        TradeService trades = new TradeService(db, accounts);
        trades.update(token, json.valueToTree(Map.of("product_id", 41, "action", "success")));
        assertNull(buyers.current().get("product"));
        Map<String, Object> result = trades.list(token);
        List<?> intents = (List<?>) result.get("intents");
        assertEquals("closed", ((Map<?, ?>) intents.get(0)).get("status"));
        assertEquals("completed", ((Map<?, ?>) intents.get(1)).get("status"));
        assertEquals("旧备注", ((Map<?, ?>) intents.get(1)).get("note"));
        assertEquals(2, ((List<?>) result.get("events")).size());
        ProductService products = new ProductService(db, accounts);
        Map<?, ?> history = (Map<?, ?>) ((List<?>) products.history(token).get("products")).get(0);
        assertEquals("sold", history.get("status")); assertNotNull(history.get("sold_at"));
    }
}
