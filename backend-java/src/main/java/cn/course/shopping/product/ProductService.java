package cn.course.shopping.product;

import cn.course.shopping.account.AccountService;
import cn.course.shopping.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Service;
import static cn.course.shopping.common.Database.*;

/** 王暾：发布商品、金额与图片校验、历史列表。 */
@Service
public class ProductService {
    public static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private static final Pattern IMAGE = Pattern.compile("data:image/(png|jpeg|webp);base64,([A-Za-z0-9+/=]+)");
    private final Database db;
    private final AccountService accounts;
    public ProductService(Database db, AccountService accounts) { this.db = db; this.accounts = accounts; }
    public Map<String, Object> publish(String token, JsonNode data) {
        return db.write(connection -> {
            accounts.require(connection, token);
            String name = Input.text(data, "name", 1, 80);
            String description = Input.text(data, "description", 1, 2000);
            long cents = price(data.get("price"));
            String image = image(data.get("image"));
            if (one(connection, "SELECT 1 FROM products WHERE status IN ('active','frozen')") != null)
                throw new ApiException(409, "已有未售出商品，成交下架后才能发布下一件");
            try {
                long id = insert(connection, "INSERT INTO products(name,description,image,price_cents,status) VALUES (?,?,?,?,'active')", name, description, image, cents);
                return Map.of("id", id, "message", "商品已发布");
            } catch (SQLException error) {
                if (error.getErrorCode() == 19) throw new ApiException(409, "只能存在一个未售出的商品");
                throw error;
            }
        });
    }
    public Map<String, Object> history(String token) {
        return db.read(connection -> {
            accounts.require(connection, token);
            return Map.of("products", rows(connection, "SELECT * FROM products ORDER BY id DESC"));
        });
    }
    public static long price(JsonNode value) {
        if (value == null || !value.isTextual() || !value.textValue().matches("[0-9]{1,7}(\\.[0-9]{1,2})?"))
            throw new ApiException(400, "价格须为正数，最多两位小数");
        BigDecimal amount = new BigDecimal(value.textValue());
        if (amount.signum() <= 0 || amount.compareTo(new BigDecimal("9999999.99")) > 0)
            throw new ApiException(400, "价格不在有效范围内");
        return amount.movePointRight(2).longValueExact();
    }
    public static String image(JsonNode value) {
        if (value == null || !value.isTextual()) throw new ApiException(400, "请上传商品图片");
        String text = value.textValue();
        if (text.length() > MAX_IMAGE_BYTES * 4 / 3 + 100) throw new ApiException(400, "图片最大 2 MB");
        Matcher match = IMAGE.matcher(text);
        if (!match.matches()) throw new ApiException(400, "图片仅支持 PNG、JPEG、WebP，最大 2 MB");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(match.group(2)); }
        catch (IllegalArgumentException error) { throw new ApiException(400, "图片编码无效"); }
        boolean valid = switch (match.group(1)) {
            case "png" -> starts(bytes, new byte[]{(byte)137,80,78,71,13,10,26,10}, 0);
            case "jpeg" -> starts(bytes, new byte[]{(byte)255,(byte)216,(byte)255}, 0);
            case "webp" -> starts(bytes, new byte[]{82,73,70,70}, 0) && starts(bytes, new byte[]{87,69,66,80}, 8);
            default -> false;
        };
        if (bytes.length > MAX_IMAGE_BYTES || !valid) throw new ApiException(400, "图片文件格式或大小不符合要求");
        return text;
    }
    private static boolean starts(byte[] data, byte[] prefix, int offset) {
        if (data.length < prefix.length + offset) return false;
        for (int i = 0; i < prefix.length; i++) if (data[offset+i] != prefix[i]) return false;
        return true;
    }
}
