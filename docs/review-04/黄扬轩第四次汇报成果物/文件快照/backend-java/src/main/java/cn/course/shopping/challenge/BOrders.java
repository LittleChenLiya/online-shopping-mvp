package cn.course.shopping.challenge;

import cn.course.shopping.account.AccountService;
import cn.course.shopping.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static cn.course.shopping.common.Database.*;

/** 一次下单对应一个主订单及多条快照明细。SQLite写事务串行化库存检查和预留。 */
@Service
public class BOrders {
    private final Database db;
    private final Customers customers;
    private final AccountService seller;
    public BOrders(Database db, Customers customers, AccountService seller) {
        this.db = db;
        this.customers = customers;
        this.seller = seller;
    }
    public Map<String,Object> checkout(String token, JsonNode data) {
        List<Long> ids = BCollection.selected(data);
        String key = Input.text(data, "request_key", 8, 80);
        return db.write(c -> {
            Map<String,Object> customer = customers.require(c, token);
            long owner = ((Number)customer.get("id")).longValue();
            String signature = ids.stream().sorted().map(String::valueOf).reduce((a,b) -> a + "," + b).orElse("");
            Map<String,Object> retry = one(c, "SELECT * FROM b_orders WHERE customer_id=? AND request_key=?", owner, key);
            if (retry != null) {
                if (!signature.equals(retry.get("selection"))) throw new ApiException(409, "请求标识已用于其他商品");
                return result(c, ((Number)retry.get("id")).longValue());
            }
            List<Map<String,Object>> accepted = new ArrayList<>();
            List<Map<String,Object>> rejected = new ArrayList<>();
            long total = 0;
            for (long id : ids) {
                Map<String,Object> cart = one(c, "SELECT quantity FROM b_cart WHERE customer_id=? AND product_id=?", owner, id);
                if (cart == null) throw new ApiException(400, "所选商品不在您的购物车");
                Map<String,Object> product = Catalog.product(c, id);
                long quantity = ((Number)cart.get("quantity")).longValue();
                if ("off".equals(product.get("status")) || ((Number)product.get("available")).longValue() < quantity) {
                    rejected.add(Map.of("product_id", id, "name", product.get("name"), "reason", "缺货"));
                    continue;
                }
                Map<String,Object> item = new LinkedHashMap<>();
                item.put("product_id", id);
                item.put("product_name", product.get("name"));
                item.put("unit_price_cents", product.get("price_cents"));
                item.put("quantity", quantity);
                accepted.add(item);
                total += quantity * ((Number)product.get("price_cents")).longValue();
            }
            if (accepted.isEmpty()) return Map.of("created", false, "rejected", rejected, "message", "所选商品均缺货，未生成订单");
            long order = insert(c, "INSERT INTO b_orders(customer_id,status,total_cents,phone,trade_location,request_key,selection) " +
                "VALUES(?,'placed',?,?,?,?,?)", owner, total, customer.get("phone"), customer.get("default_location"), key, signature);
            for (Map<String,Object> item : accepted) {
                update(c, "INSERT INTO b_order_items(order_id,product_id,product_name,unit_price_cents,quantity) VALUES(?,?,?,?,?)",
                    order, item.get("product_id"), item.get("product_name"), item.get("unit_price_cents"), item.get("quantity"));
                update(c, "DELETE FROM b_cart WHERE customer_id=? AND product_id=?", owner, item.get("product_id"));
            }
            for (Map<String,Object> item : rejected)
                update(c, "INSERT INTO b_rejected_items(order_id,product_id,name,reason) VALUES(?,?,?,?)",
                    order, item.get("product_id"), item.get("name"), item.get("reason"));
            event(c, order, "placed", "customer:" + owner);
            return result(c, order);
        });
    }
    private Map<String,Object> result(Connection c, long id) throws SQLException {
        return Map.of("created", true, "order", detail(c, id),
            "rejected", rows(c, "SELECT product_id,name,reason FROM b_rejected_items WHERE order_id=?", id),
            "message", "下单成功，有货商品已合并为一笔订单");
    }
    private Map<String,Object> detail(Connection c, long id) throws SQLException {
        Map<String,Object> order = one(c, "SELECT o.*,u.username FROM b_orders o JOIN a_customers u ON u.id=o.customer_id WHERE o.id=?", id);
        order.remove("request_key");
        order.remove("selection");
        order.put("items", rows(c, "SELECT product_id,product_name,unit_price_cents,quantity,unit_price_cents*quantity AS subtotal_cents " +
            "FROM b_order_items WHERE order_id=? ORDER BY product_id", id));
        order.put("events", rows(c, "SELECT status,actor,created_at FROM b_order_events WHERE order_id=? ORDER BY id", id));
        return order;
    }
    public Map<String,Object> list(String token, boolean admin, Long customerId, int page, int size) {
        if (page < 1 || page > 1_000_000 || size < 1 || size > 100) throw new ApiException(400, "分页参数不合法");
        return db.read(c -> {
            String where = "";
            List<Object> args = new ArrayList<>();
            if (admin) {
                seller.require(c, token);
                if (customerId != null) {
                    where = " WHERE customer_id=?";
                    args.add(customerId);
                }
            } else {
                where = " WHERE customer_id=?";
                args.add(customers.require(c, token).get("id"));
            }
            long count = ((Number)one(c, "SELECT count(*) AS n FROM b_orders" + where, args.toArray()).get("n")).longValue();
            args.add(size);
            args.add((page - 1) * size);
            List<Map<String,Object>> orders = new ArrayList<>();
            for (Map<String,Object> row : rows(c, "SELECT id FROM b_orders" + where + " ORDER BY id DESC LIMIT ? OFFSET ?", args.toArray()))
                orders.add(detail(c, ((Number)row.get("id")).longValue()));
            return Map.of("orders", orders, "total", count, "page", page, "page_size", size);
        });
    }
    public Map<String,Object> customers(String token) {
        return db.read(c -> {
            seller.require(c, token);
            return Map.of("customers", rows(c, "SELECT u.id,u.username,u.phone,u.default_location,u.created_at," +
                "(SELECT count(*) FROM b_orders o WHERE o.customer_id=u.id) AS order_count," +
                "COALESCE((SELECT sum(total_cents) FROM b_orders o WHERE o.customer_id=u.id AND status='completed'),0) AS completed_total_cents " +
                "FROM a_customers u ORDER BY u.id DESC"));
        });
    }
    public Map<String,Object> action(String token, long id, JsonNode data, boolean admin) {
        String action = Input.text(data, "action", 1, 20);
        return db.write(c -> {
            Object owner = null;
            if (admin) seller.require(c, token);
            else owner = customers.require(c, token).get("id");
            Map<String,Object> order = one(c, "SELECT * FROM b_orders WHERE id=?", id);
            if (order == null || (!admin && !owner.equals(order.get("customer_id"))))
                throw new ApiException(404, "订单不存在");
            String status = (String)order.get("status");
            String next;
            if (action.equals("cancel")) {
                Set<String> allowed = admin ? Set.of("placed","confirmed","ready","shipped") : Set.of("placed","confirmed","ready");
                if (!allowed.contains(status)) throw new ApiException(409, "当前状态不允许取消订单");
                next = "cancelled";
                if (status.equals("shipped")) {
                    for (Map<String,Object> item : rows(c, "SELECT product_id,quantity FROM b_order_items WHERE order_id=?", id))
                        update(c, "UPDATE a_products SET stock=stock+?,listed=1 WHERE id=?", item.get("quantity"), item.get("product_id"));
                }
            } else {
                if (!admin) throw new ApiException(403, "客户仅可取消自己的订单");
                next = switch (action) {
                    case "confirm" -> "confirmed";
                    case "ready" -> "ready";
                    case "ship" -> "shipped";
                    case "complete" -> "completed";
                    default -> throw new ApiException(400, "未知订单操作");
                };
                String required = Map.of("confirmed","placed","ready","confirmed","shipped","ready","completed","shipped").get(next);
                if (!status.equals(required)) throw new ApiException(409, "订单须按顺序处理，请刷新后重试");
                if (next.equals("shipped")) {
                    for (Map<String,Object> item : rows(c, "SELECT product_id,quantity FROM b_order_items WHERE order_id=?", id)) {
                        long quantity = ((Number)item.get("quantity")).longValue();
                        int changed = update(c, "UPDATE a_products SET stock=stock-?,listed=CASE WHEN stock-?=0 THEN 0 ELSE listed END " +
                            "WHERE id=? AND stock>=?", quantity, quantity, item.get("product_id"), quantity);
                        if (changed != 1) throw new ApiException(409, "库存冲突");
                    }
                }
            }
            update(c, "UPDATE b_orders SET status=?,updated_at=datetime('now','localtime') WHERE id=?", next, id);
            event(c, id, next, admin ? "seller" : "customer:" + owner);
            return Map.of("message", "订单状态已更新", "order", detail(c, id));
        });
    }
    private static void event(Connection c, long id, String status, String actor) throws SQLException {
        update(c, "INSERT INTO b_order_events(order_id,status,actor) VALUES(?,?,?)", id, status, actor);
    }
}
