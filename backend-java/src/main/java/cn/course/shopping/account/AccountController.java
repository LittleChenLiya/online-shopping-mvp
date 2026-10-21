package cn.course.shopping.account;

import cn.course.shopping.common.Input;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class AccountController {
    private final AccountService accounts;
    public AccountController(AccountService accounts) { this.accounts = accounts; }
    @GetMapping("/api/account") public Map<String, Object> account(@CookieValue(name="shop_session", defaultValue="") String token) {
        return Map.of("logged_in", accounts.loggedIn(token), "username", "seller");
    }
    @PostMapping("/api/login") public Map<String, String> login(@RequestBody JsonNode data, HttpServletResponse response) {
        cookie(response, accounts.login(data), AccountService.SESSION_SECONDS);
        return Map.of("message", "登录成功");
    }
    @PostMapping("/api/logout") public Map<String, String> logout(@RequestBody JsonNode data,
            @CookieValue(name="shop_session", defaultValue="") String token, HttpServletResponse response) {
        Input.object(data); accounts.logout(token); cookie(response, "", 0);
        return Map.of("message", "已退出登录");
    }
    @PostMapping("/api/password") public Map<String, String> changePassword(@RequestBody JsonNode data,
            @CookieValue(name="shop_session", defaultValue="") String token, HttpServletResponse response) {
        accounts.changePassword(token, data); cookie(response, "", 0);
        return Map.of("message", "密码已修改，请重新登录");
    }
    private static void cookie(HttpServletResponse response, String token, int age) {
        response.setHeader("Set-Cookie", "shop_session=" + token + "; HttpOnly; SameSite=Strict; Path=/; Max-Age=" + age);
    }
}
