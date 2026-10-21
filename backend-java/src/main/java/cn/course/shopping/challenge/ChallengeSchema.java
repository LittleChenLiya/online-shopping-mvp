package cn.course.shopping.challenge;

import java.sql.*;

/** 挑战A采用独立表，保留原基线表与验收接口。所有库存业务共用SQLite写事务。 */
public final class ChallengeSchema {
    private ChallengeSchema() {}
    public static void initialize(Connection connection) throws SQLException {
        for (String sql : SQL) try (Statement statement = connection.createStatement()) { statement.execute(sql); }
    }
    private static final String[] SQL = {
        "CREATE TABLE IF NOT EXISTS a_categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, parent_id INTEGER REFERENCES a_categories(id), UNIQUE(parent_id,name))",
        "CREATE UNIQUE INDEX IF NOT EXISTS a_root_category_name ON a_categories(name) WHERE parent_id IS NULL",
        "CREATE TABLE IF NOT EXISTS a_products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, description TEXT NOT NULL, price_cents INTEGER NOT NULL CHECK(price_cents>0), category_id INTEGER NOT NULL REFERENCES a_categories(id), stock INTEGER NOT NULL CHECK(stock>=0), listed INTEGER NOT NULL CHECK(listed IN (0,1)), created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')))",
        "CREATE TABLE IF NOT EXISTS a_images (id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER NOT NULL REFERENCES a_products(id), position INTEGER NOT NULL, image TEXT NOT NULL, UNIQUE(product_id,position))",
        "CREATE TABLE IF NOT EXISTS a_customers (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL COLLATE NOCASE UNIQUE, password_hash TEXT NOT NULL, phone TEXT NOT NULL, default_location TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')))",
        "CREATE TABLE IF NOT EXISTS a_customer_sessions (token_hash TEXT PRIMARY KEY, customer_id INTEGER NOT NULL REFERENCES a_customers(id), expires_at REAL NOT NULL)",
        "CREATE TABLE IF NOT EXISTS a_orders (id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER NOT NULL REFERENCES a_products(id), customer_id INTEGER NOT NULL REFERENCES a_customers(id), quantity INTEGER NOT NULL CHECK(quantity>0), unit_price_cents INTEGER NOT NULL, product_name TEXT NOT NULL, phone TEXT NOT NULL, trade_location TEXT NOT NULL, note TEXT NOT NULL, status TEXT NOT NULL CHECK(status IN ('pending','frozen','completed','failed','cancelled')), created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')), updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime')))",
        "CREATE INDEX IF NOT EXISTS a_orders_product_status ON a_orders(product_id,status)",
        "CREATE INDEX IF NOT EXISTS a_orders_customer ON a_orders(customer_id,id)",
        "CREATE TABLE IF NOT EXISTS a_order_events (id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER NOT NULL REFERENCES a_orders(id), action TEXT NOT NULL, actor TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')))"
    };
}
