package cn.course.shopping.challenge;

import cn.course.shopping.common.Input;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/a/customer")
public class CustomerController {
    private final Customers customers;
    public CustomerController(Customers customers){this.customers=customers;}
    @GetMapping("/account") public Map<String,Object> account(@CookieValue(name="shop_customer_session",defaultValue="")String token){return customers.account(token);}
    @PostMapping("/register") public Map<String,Object> register(@RequestBody JsonNode data){return customers.register(data);}
    @PostMapping("/login") public Map<String,String> login(@RequestBody JsonNode data,HttpServletResponse response){cookie(response,customers.login(data),28800);return Map.of("message","客户登录成功");}
    @PostMapping("/logout") public Map<String,String> logout(@RequestBody JsonNode data,@CookieValue(name="shop_customer_session",defaultValue="")String token,HttpServletResponse response){Input.object(data);customers.logout(token);cookie(response,"",0);return Map.of("message","客户已退出");}
    @PostMapping("/profile") public Map<String,Object> profile(@RequestBody JsonNode data,@CookieValue(name="shop_customer_session",defaultValue="")String token){return customers.profile(token,data);}
    @PostMapping("/password") public Map<String,Object> password(@RequestBody JsonNode data,@CookieValue(name="shop_customer_session",defaultValue="")String token,HttpServletResponse response){Map<String,Object> result=customers.password(token,data);cookie(response,"",0);return result;}
    private static void cookie(HttpServletResponse response,String token,int age){response.setHeader("Set-Cookie","shop_customer_session="+token+"; HttpOnly; SameSite=Strict; Path=/; Max-Age="+age);}
}
