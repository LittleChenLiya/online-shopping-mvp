package cn.course.shopping.trade;

import cn.course.shopping.account.AccountService;
import cn.course.shopping.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Service;
import static cn.course.shopping.common.Database.*;

/** 黄扬轩：选择买家后冻结，成功下架，失败恢复；多表更新同成同败。 */
@Service
public class TradeService {
    private final Database db;
    private final AccountService accounts;
    public TradeService(Database db, AccountService accounts) { this.db = db; this.accounts = accounts; }
    public Map<String, Object> list(String token) {
        return db.read(connection -> {
            accounts.require(connection, token);
            Map<String, Object> result = nullable("product", one(connection, "SELECT * FROM products WHERE status IN ('active','frozen')"));
            result.put("intents", rows(connection, "SELECT i.*,p.name AS product_name FROM intents i JOIN products p ON p.id=i.product_id ORDER BY i.id DESC"));
            result.put("events", rows(connection, "SELECT e.*,p.name AS product_name,i.buyer_name FROM trade_events e JOIN products p ON p.id=e.product_id JOIN intents i ON i.id=e.intent_id ORDER BY e.id DESC"));
            return result;
        });
    }
    public Map<String, String> update(String token, JsonNode data) {
        return db.write(connection -> {
            accounts.require(connection, token);
            long productId = Input.id(data, "product_id");
            String action = Input.text(data, "action", 1, 20);
            if (!Set.of("freeze", "success", "failure").contains(action)) throw new ApiException(400, "无效的交易操作");
            Map<String, Object> product = one(connection, "SELECT * FROM products WHERE id=?", productId);
            if (product == null) throw new ApiException(404, "商品不存在");
            long intentId;
            if (action.equals("freeze")) {
                if (!"active".equals(product.get("status"))) throw new ApiException(409, "只有在售商品可以冻结");
                intentId = Input.id(data, "intent_id");
                Map<String, Object> intent = one(connection, "SELECT * FROM intents WHERE id=? AND product_id=?", intentId, productId);
                if (intent == null || !"pending".equals(intent.get("status"))) throw new ApiException(409, "请选择该商品的有效购买意向");
                Database.update(connection, "UPDATE products SET status='frozen',selected_intent_id=? WHERE id=?", intentId, productId);
                Database.update(connection, "UPDATE intents SET status='selected' WHERE id=?", intentId);
            } else {
                if (!"frozen".equals(product.get("status")) || product.get("selected_intent_id") == null)
                    throw new ApiException(409, "商品必须先选择买家并冻结，才能处理交易结果");
                intentId = ((Number) product.get("selected_intent_id")).longValue();
                if (action.equals("success")) {
                    Database.update(connection, "UPDATE products SET status='sold',sold_at=datetime('now','localtime') WHERE id=?", productId);
                    Database.update(connection, "UPDATE intents SET status=CASE WHEN id=? THEN 'completed' ELSE 'closed' END WHERE product_id=?", intentId, productId);
                } else {
                    Database.update(connection, "UPDATE products SET status='active',selected_intent_id=NULL WHERE id=?", productId);
                    Database.update(connection, "UPDATE intents SET status='pending' WHERE id=?", intentId);
                }
            }
            Database.update(connection, "INSERT INTO trade_events(product_id,intent_id,action) VALUES (?,?,?)", productId, intentId, action);
            return Map.of("message", switch (action) {
                case "freeze" -> "商品已冻结，请在线下完成交易";
                case "success" -> "交易成功，商品已下架，可以发布下一件商品";
                default -> "交易失败，商品已恢复上线";
            });
        });
    }
}
