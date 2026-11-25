package cn.course.shopping.challenge;

import cn.course.shopping.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static cn.course.shopping.common.Database.*;

/** 购物车和收藏属于登录客户；重复添加同款合并数量。 */
@Service
public class BCollection {
    private final Database db;
    private final Customers customers;
    public BCollection(Database db, Customers customers) {
        this.db = db;
        this.customers = customers;
    }
    public Map<String,Object> list(String token, boolean favorite) {
        return db.read(c -> {
            long owner = owner(c, token);
            String table = favorite ? "b_favorites" : "b_cart";
            List<Map<String,Object>> items = rows(c,
                "SELECT x.*,p.name,p.price_cents FROM " + table +
                " x JOIN a_products p ON p.id=x.product_id WHERE x.customer_id=? ORDER BY x.product_id DESC", owner);
            for (Map<String,Object> item : items) {
                Map<String,Object> product = Catalog.product(c, ((Number)item.get("product_id")).longValue());
                item.put("product", product);
            }
            return Map.of("items", items);
        });
    }
    public Map<String,Object> save(String token, JsonNode data, boolean favorite) {
        Input.object(data);
        long productId = Input.id(data, "product_id");
        return db.write(c -> {
            long owner = owner(c, token);
            Map<String,Object> product = Catalog.product(c, productId);
            if (product == null) throw new ApiException(404, "商品不存在");
            if (favorite) {
                update(c, "INSERT OR IGNORE INTO b_favorites(customer_id,product_id) VALUES(?,?)", owner, productId);
            } else {
                if ("off".equals(product.get("status"))) throw new ApiException(409, "商品已下架");
                long quantity = quantity(data);
                Map<String,Object> existing = one(c,
                    "SELECT quantity FROM b_cart WHERE customer_id=? AND product_id=?", owner, productId);
                String mode = Input.text(data, "mode", 0, 10);
                if (!mode.isEmpty() && !Set.of("add", "set").contains(mode))
                    throw new ApiException(400, "未知购物车操作");
                if (!mode.equals("set") && existing != null)
                    quantity += ((Number)existing.get("quantity")).longValue();
                if (quantity > 1_000_000) throw new ApiException(400, "数量超过上限");
                update(c, "INSERT INTO b_cart(customer_id,product_id,quantity) VALUES(?,?,?) " +
                    "ON CONFLICT(customer_id,product_id) DO UPDATE SET quantity=excluded.quantity",
                    owner, productId, quantity);
            }
            return Map.of("message", favorite ? "已收藏" : "购物车已更新");
        });
    }
    public Map<String,Object> remove(String token, long id, boolean favorite) {
        return db.write(c -> {
            long owner = owner(c, token);
            update(c, "DELETE FROM " + (favorite ? "b_favorites" : "b_cart") +
                " WHERE customer_id=? AND product_id=?", owner, id);
            return Map.of("message", "已移除");
        });
    }
    public Map<String,Object> move(String token, JsonNode data) {
        List<Long> ids = selected(data);
        return db.write(c -> {
            long owner = owner(c, token);
            for (long id : ids) {
                if (one(c, "SELECT 1 FROM b_cart WHERE customer_id=? AND product_id=?", owner, id) == null)
                    throw new ApiException(400, "所选商品不在您的购物车");
                update(c, "INSERT OR IGNORE INTO b_favorites(customer_id,product_id) VALUES(?,?)", owner, id);
                update(c, "DELETE FROM b_cart WHERE customer_id=? AND product_id=?", owner, id);
            }
            return Map.of("message", "所选商品已移入收藏");
        });
    }
    long owner(Connection c, String token) throws SQLException {
        return ((Number)customers.require(c, token).get("id")).longValue();
    }
    static long quantity(JsonNode data) {
        long value = Input.id(data, "quantity");
        if (value > 1_000_000) throw new ApiException(400, "购买数量超过上限");
        return value;
    }
    static List<Long> selected(JsonNode data) {
        Input.object(data);
        JsonNode values = data.get("product_ids");
        if (values == null || !values.isArray() || values.isEmpty() || values.size() > 100)
            throw new ApiException(400, "请选择1至100种商品");
        Set<Long> ids = new LinkedHashSet<>();
        for (JsonNode value : values) {
            if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0)
                throw new ApiException(400, "商品编号须为正整数");
            if (!ids.add(value.longValue())) throw new ApiException(400, "商品编号重复");
        }
        return new ArrayList<>(ids);
    }
}
