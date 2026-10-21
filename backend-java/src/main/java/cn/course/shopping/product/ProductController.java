package cn.course.shopping.product;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProductController {
    private final ProductService products;
    public ProductController(ProductService products) { this.products = products; }
    @GetMapping("/api/products") public Map<String, Object> history(@CookieValue(name="shop_session", defaultValue="") String token) {
        return products.history(token);
    }
    @PostMapping("/api/products") public Map<String, Object> publish(@CookieValue(name="shop_session", defaultValue="") String token, @RequestBody JsonNode data) {
        return products.publish(token, data);
    }
}
