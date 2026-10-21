package cn.course.shopping.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 曾大君：SQLite 持久化。BEGIN IMMEDIATE 将检查及多表修改放进同一写事务。 */
@Component
public class Database {
    private final Path path;
    public Database(@Value("${shop.db-path}") String path) {
        this.path = Path.of(path).toAbsolutePath().normalize();
    }
    public Path path() { return path; }
    @FunctionalInterface public interface Work<T> { T run(Connection connection) throws SQLException; }

    public <T> T read(Work<T> work) { return transaction(false, work); }
    public <T> T write(Work<T> work) { return transaction(true, work); }

    private <T> T transaction(boolean write, Work<T> work) {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=10000");
                statement.execute(write ? "BEGIN IMMEDIATE" : "BEGIN");
            }
            try {
                T result = work.run(connection);
                try (Statement statement = connection.createStatement()) { statement.execute("COMMIT"); }
                return result;
            } catch (SQLException | RuntimeException error) {
                try (Statement statement = connection.createStatement()) { statement.execute("ROLLBACK"); }
                catch (SQLException rollback) { error.addSuppressed(rollback); }
                throw error;
            }
        } catch (SQLException error) {
            throw new IllegalStateException("数据库操作失败", error);
        }
    }

    public void initialize(String passwordHash) {
        try { Files.createDirectories(path.getParent()); }
        catch (java.io.IOException error) { throw new IllegalStateException("不能创建数据库目录", error); }
        write(connection -> {
            for (String ddl : SCHEMA) try (Statement statement = connection.createStatement()) { statement.execute(ddl); }
            update(connection, "INSERT OR IGNORE INTO seller VALUES (1,?,?)", "seller", passwordHash);
            cn.course.shopping.challenge.ChallengeSchema.initialize(connection);
            return null;
        });
    }

    public static List<Map<String, Object>> rows(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, args); ResultSet result = statement.executeQuery()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            ResultSetMetaData meta = result.getMetaData();
            while (result.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) row.put(meta.getColumnLabel(i), result.getObject(i));
                rows.add(row);
            }
            return rows;
        }
    }
    public static Map<String, Object> one(Connection connection, String sql, Object... args) throws SQLException {
        List<Map<String, Object>> rows = rows(connection, sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public static int update(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, args)) { return statement.executeUpdate(); }
    }
    public static long insert(Connection connection, String sql, Object... args) throws SQLException {
        update(connection, sql, args);
        return ((Number) Objects.requireNonNull(one(connection, "SELECT last_insert_rowid() AS id")).get("id")).longValue();
    }
    private static PreparedStatement prepare(Connection connection, String sql, Object... args) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
        return statement;
    }
    public static Map<String, Object> nullable(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put(key, value); return result;
    }
    public static Map<String, Object> publicProduct(Map<String, Object> row) {
        if (row == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("id", "name", "description", "image", "price_cents", "status", "created_at"))
            result.put(key, row.get(key));
        return result;
    }
    private static final String[] SCHEMA = {
        "CREATE TABLE IF NOT EXISTS seller (id INTEGER PRIMARY KEY CHECK(id=1), username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS sessions (token_hash TEXT PRIMARY KEY, expires_at REAL NOT NULL)",
        "CREATE TABLE IF NOT EXISTS products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, description TEXT NOT NULL, image TEXT NOT NULL, price_cents INTEGER NOT NULL CHECK(price_cents>0), status TEXT NOT NULL CHECK(status IN ('active','frozen','sold')), selected_intent_id INTEGER REFERENCES intents(id), created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')), sold_at TEXT)",
        "CREATE UNIQUE INDEX IF NOT EXISTS one_unsold_product ON products((1)) WHERE status IN ('active','frozen')",
        "CREATE TABLE IF NOT EXISTS intents (id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER NOT NULL REFERENCES products(id), buyer_name TEXT NOT NULL, contact TEXT NOT NULL, note TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','selected','completed','closed')), created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')), UNIQUE(product_id,contact))",
        "CREATE TABLE IF NOT EXISTS trade_events (id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER NOT NULL REFERENCES products(id), intent_id INTEGER NOT NULL REFERENCES intents(id), action TEXT NOT NULL CHECK(action IN ('freeze','success','failure')), created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')))"
    };
}
