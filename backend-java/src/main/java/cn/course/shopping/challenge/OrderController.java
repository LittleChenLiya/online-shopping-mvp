package cn.course.shopping.challenge;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/a")
public class OrderController {
    private final Orders orders;
    public OrderController(Orders orders){this.orders=orders;}
    @PostMapping("/orders") public Map<String,Object> place(@CookieValue(name="shop_customer_session",defaultValue="")String token,@RequestBody JsonNode data){return orders.place(token,data);}
    @GetMapping("/customer/orders") public Map<String,Object> own(@CookieValue(name="shop_customer_session",defaultValue="")String token,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return orders.list(token,false,null,page,size);}
    @PostMapping("/customer/orders/{id}") public Map<String,Object> cancel(@CookieValue(name="shop_customer_session",defaultValue="")String token,@PathVariable long id,@RequestBody JsonNode data){return orders.action(token,id,data,true);}
    @GetMapping("/seller/orders") public Map<String,Object> all(@CookieValue(name="shop_session",defaultValue="")String token,@RequestParam(required=false)Long customer_id,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return orders.list(token,true,customer_id,page,size);}
    @PostMapping("/seller/orders/{id}") public Map<String,Object> trade(@CookieValue(name="shop_session",defaultValue="")String token,@PathVariable long id,@RequestBody JsonNode data){return orders.action(token,id,data,false);}
    @GetMapping("/seller/customers") public Map<String,Object> customers(@CookieValue(name="shop_session",defaultValue="")String token,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return orders.customerList(token,page,size);}
}
