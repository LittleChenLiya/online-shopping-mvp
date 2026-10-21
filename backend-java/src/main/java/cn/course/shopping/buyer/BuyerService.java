package cn.course.shopping.buyer;

import cn.course.shopping.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.SQLException;
import java.util.Map;
import org.springframework.stereotype.Service;
import static cn.course.shopping.common.Database.*;

/** 余昕越：买家公开查询及免注册购买意向。 */
@Service
public class BuyerService {
    private final Database db;
    public BuyerService(Database db) { this.db = db; }
    public Map<String, Object> current() {
        return db.read(connection -> nullable("product", publicProduct(one(connection,
                "SELECT * FROM products WHERE status IN ('active','frozen')"))));
    }
    public Map<String, Object> submit(JsonNode data) {
        long productId = Input.id(data, "product_id");
        String name = Input.text(data, "buyer_name", 1, 40);
        String original = Input.text(data, "contact", 6, 30);
        if (!original.matches("[0-9+()\\- ]+")) throw new ApiException(400, "联系方式请填写电话号码");
        String contact = original.replaceAll("[()\\- ]", "");
        // 加号只能作为国际区号前缀，不能在号码中间或重复出现。
        if (!contact.matches("\\+?[0-9]{6,20}")) throw new ApiException(400, "电话号码长度或格式不正确");
        String note = Input.text(data, "note", 0, 300);
        return db.write(connection -> {
            Map<String, Object> product = one(connection, "SELECT * FROM products WHERE id=?", productId);
            if (product == null || !"active".equals(product.get("status")))
                throw new ApiException(409, "商品当前不可购买，请刷新页面");
            try {
                long id = insert(connection, "INSERT INTO intents(product_id,buyer_name,contact,note) VALUES (?,?,?,?)", productId, name, contact, note);
                return Map.of("id", id, "message", "意向已提交，请等待卖家联系；付款与交货在线下完成");
            } catch (SQLException error) {
                if (error.getErrorCode() == 19) throw new ApiException(409, "该电话已提交过此商品的购买意向，请等待卖家联系");
                throw error;
            }
        });
    }
}
