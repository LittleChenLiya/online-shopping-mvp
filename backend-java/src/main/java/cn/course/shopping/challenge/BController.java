package cn.course.shopping.challenge;

import cn.course.shopping.common.Input;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** 挑战B复用客户/卖家Cookie，所有所有权和状态检查在服务事务内执行。 */
@RestController
@RequestMapping("/api/b")
public class BController {
    private final BCollection collection;
    private final BOrders orders;
    public BController(BCollection collection, BOrders orders) { this.collection=collection; this.orders=orders; }
    @GetMapping("/cart")
    public Map<String,Object> cart(@CookieValue(name="shop_customer_session",defaultValue="") String token) { return collection.list(token,false); }
    @PostMapping("/cart")
    public Map<String,Object> saveCart(@CookieValue(name="shop_customer_session",defaultValue="") String token,@RequestBody JsonNode data) { return collection.save(token,data,false); }
    @PostMapping("/cart/{id}/remove")
    public Map<String,Object> removeCart(@CookieValue(name="shop_customer_session",defaultValue="") String token,@PathVariable long id,@RequestBody JsonNode data) { Input.object(data); return collection.remove(token,id,false); }
    @PostMapping("/cart/favorite")
    public Map<String,Object> move(@CookieValue(name="shop_customer_session",defaultValue="") String token,@RequestBody JsonNode data) { return collection.move(token,data); }
    @GetMapping("/favorites")
    public Map<String,Object> favorites(@CookieValue(name="shop_customer_session",defaultValue="") String token) { return collection.list(token,true); }
    @PostMapping("/favorites")
    public Map<String,Object> saveFavorite(@CookieValue(name="shop_customer_session",defaultValue="") String token,@RequestBody JsonNode data) { return collection.save(token,data,true); }
    @PostMapping("/favorites/{id}/remove")
    public Map<String,Object> removeFavorite(@CookieValue(name="shop_customer_session",defaultValue="") String token,@PathVariable long id,@RequestBody JsonNode data) { Input.object(data); return collection.remove(token,id,true); }
    @PostMapping("/orders")
    public Map<String,Object> checkout(@CookieValue(name="shop_customer_session",defaultValue="") String token,@RequestBody JsonNode data) { return orders.checkout(token,data); }
    @GetMapping("/orders")
    public Map<String,Object> own(@CookieValue(name="shop_customer_session",defaultValue="") String token,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return orders.list(token,false,null,page,size); }
    @GetMapping("/seller/orders")
    public Map<String,Object> all(@CookieValue(name="shop_session",defaultValue="") String token,@RequestParam(required=false) Long customer_id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return orders.list(token,true,customer_id,page,size); }
    @GetMapping("/seller/customers")
    public Map<String,Object> customers(@CookieValue(name="shop_session",defaultValue="") String token) { return orders.customers(token); }
    @PostMapping("/orders/{id}")
    public Map<String,Object> cancel(@CookieValue(name="shop_customer_session",defaultValue="") String token,@PathVariable long id,@RequestBody JsonNode data) { return orders.action(token,id,data,false); }
    @PostMapping("/seller/orders/{id}")
    public Map<String,Object> transition(@CookieValue(name="shop_session",defaultValue="") String token,@PathVariable long id,@RequestBody JsonNode data) { return orders.action(token,id,data,true); }
}
