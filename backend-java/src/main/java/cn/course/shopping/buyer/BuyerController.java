package cn.course.shopping.buyer;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class BuyerController {
    private final BuyerService buyers;
    public BuyerController(BuyerService buyers) { this.buyers = buyers; }
    @GetMapping("/api/product") public Map<String, Object> current() { return buyers.current(); }
    @PostMapping("/api/intents") public Map<String, Object> submit(@RequestBody JsonNode data) { return buyers.submit(data); }
}
