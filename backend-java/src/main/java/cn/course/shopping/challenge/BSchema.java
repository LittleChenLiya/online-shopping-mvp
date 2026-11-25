package cn.course.shopping.challenge;
import java.sql.*;

public final class BSchema {
    private BSchema() {}
    public static void initialize(Connection c) throws SQLException {
        String[] statements = {
            "CREATE TABLE IF NOT EXISTS b_cart(customer_id INTEGER NOT NULL REFERENCES a_customers(id),product_id INTEGER NOT NULL REFERENCES a_products(id),quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 1000000),PRIMARY KEY(customer_id,product_id))",
            "CREATE TABLE IF NOT EXISTS b_favorites(customer_id INTEGER NOT NULL REFERENCES a_customers(id),product_id INTEGER NOT NULL REFERENCES a_products(id),PRIMARY KEY(customer_id,product_id))",
            "CREATE TABLE IF NOT EXISTS b_orders(id INTEGER PRIMARY KEY AUTOINCREMENT,customer_id INTEGER NOT NULL REFERENCES a_customers(id),status TEXT NOT NULL CHECK(status IN ('placed','confirmed','ready','shipped','completed','cancelled')),total_cents INTEGER NOT NULL,phone TEXT NOT NULL,trade_location TEXT NOT NULL,request_key TEXT NOT NULL,selection TEXT NOT NULL,created_at TEXT NOT NULL DEFAULT(datetime('now','localtime')),updated_at TEXT NOT NULL DEFAULT(datetime('now','localtime')),UNIQUE(customer_id,request_key))",
            "CREATE TABLE IF NOT EXISTS b_order_items(order_id INTEGER NOT NULL REFERENCES b_orders(id),product_id INTEGER NOT NULL REFERENCES a_products(id),product_name TEXT NOT NULL,unit_price_cents INTEGER NOT NULL CHECK(unit_price_cents>0),quantity INTEGER NOT NULL CHECK(quantity>0),PRIMARY KEY(order_id,product_id))",
            "CREATE INDEX IF NOT EXISTS b_items_product ON b_order_items(product_id,order_id)",
            "CREATE INDEX IF NOT EXISTS b_orders_customer ON b_orders(customer_id,id)",
            "CREATE TABLE IF NOT EXISTS b_rejected_items(order_id INTEGER NOT NULL REFERENCES b_orders(id),product_id INTEGER NOT NULL,name TEXT NOT NULL,reason TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS b_order_events(id INTEGER PRIMARY KEY AUTOINCREMENT,order_id INTEGER NOT NULL REFERENCES b_orders(id),status TEXT NOT NULL,actor TEXT NOT NULL,created_at TEXT NOT NULL DEFAULT(datetime('now','localtime')))"
        };
        for(String sql : statements) try(Statement s=c.createStatement()) { s.execute(sql); }
    }
}
