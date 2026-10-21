package cn.course.shopping.account;

import cn.course.shopping.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.*;
import java.util.Map;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import static cn.course.shopping.common.Database.*;

/** 曾大君：固定卖家认证、8 小时会话以及改密失效。 */
@Service
public class AccountService {
    public static final int SESSION_SECONDS = 8 * 60 * 60;
    private final Database db;
    private final String initialPassword;
    public AccountService(Database db, @Value("${shop.initial-password}") String password) { this.db = db; this.initialPassword = password; }
    @PostConstruct public void initialize() {
        int length = initialPassword.codePointCount(0, initialPassword.length());
        if (length < 8 || length > 128)
            throw new IllegalArgumentException("SHOP_INITIAL_PASSWORD 长度须为 8–128 字符");
        db.initialize(Passwords.hash(initialPassword));
    }
    public boolean loggedIn(Connection connection, String token) throws SQLException {
        return one(connection, "SELECT 1 FROM sessions WHERE token_hash=? AND expires_at>?", Passwords.tokenHash(token), now()) != null;
    }
    public boolean loggedIn(String token) { return db.read(connection -> loggedIn(connection, token)); }
    public void require(Connection connection, String token) throws SQLException {
        if (!loggedIn(connection, token)) throw new ApiException(401, "请先登录卖家账号");
    }
    public String login(JsonNode data) {
        String username = Input.text(data, "username", 1, 40);
        String password = Input.text(data, "password", 1, 128, false);
        return db.write(connection -> {
            Map<String, Object> seller = one(connection, "SELECT * FROM seller WHERE id=1");
            if (!username.equals(seller.get("username")) || !Passwords.verify(password, (String) seller.get("password_hash")))
                throw new ApiException(401, "账号或密码错误");
            String token = Passwords.token();
            update(connection, "DELETE FROM sessions WHERE expires_at<=?", now());
            update(connection, "INSERT INTO sessions VALUES (?,?)", Passwords.tokenHash(token), now() + SESSION_SECONDS);
            return token;
        });
    }
    public void logout(String token) {
        db.write(connection -> { update(connection, "DELETE FROM sessions WHERE token_hash=?", Passwords.tokenHash(token)); return null; });
    }
    public void changePassword(String token, JsonNode data) {
        // 与业务修改在同一写事务内验证会话，改密与旧会话请求不会交错生效。
        db.write(connection -> {
            require(connection, token);
            String old = Input.text(data, "old_password", 1, 128, false);
            String next = Input.text(data, "new_password", 8, 128, false);
            if (old.equals(next)) throw new ApiException(400, "新密码不能与旧密码相同");
            Map<String, Object> seller = one(connection, "SELECT * FROM seller WHERE id=1");
            if (!Passwords.verify(old, (String) seller.get("password_hash"))) throw new ApiException(400, "原密码错误");
            update(connection, "UPDATE seller SET password_hash=? WHERE id=1", Passwords.hash(next));
            update(connection, "DELETE FROM sessions");
            return null;
        });
    }
    private static double now() { return System.currentTimeMillis() / 1000.0; }
}
