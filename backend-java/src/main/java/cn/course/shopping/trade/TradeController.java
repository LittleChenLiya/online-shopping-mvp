package cn.course.shopping.trade;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class TradeController {
    private final TradeService trades;
    public TradeController(TradeService trades) { this.trades = trades; }
    @GetMapping("/api/trades") public Map<String, Object> list(@CookieValue(name="shop_session", defaultValue="") String token) { return trades.list(token); }
    @PostMapping("/api/trade") public Map<String, String> update(@CookieValue(name="shop_session", defaultValue="") String token, @RequestBody JsonNode data) { return trades.update(token, data); }
}
