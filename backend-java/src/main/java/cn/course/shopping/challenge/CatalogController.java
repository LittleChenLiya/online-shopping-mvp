package cn.course.shopping.challenge;

import cn.course.shopping.common.Input;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/a")
public class CatalogController {
    private final Catalog catalog;
    public CatalogController(Catalog catalog){this.catalog=catalog;}
    @GetMapping("/categories") public Map<String,Object> categories(){return catalog.categories();}
    @GetMapping("/products") public Map<String,Object> products(@RequestParam(defaultValue="")String q,@RequestParam(required=false)Long category,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="24")int size){return catalog.list("",false,q,category,page,size);}
    @GetMapping("/products/{id}") public Map<String,Object> detail(@PathVariable long id){return catalog.detail(id);}
    @GetMapping("/seller/products") public Map<String,Object> sellerProducts(@CookieValue(name="shop_session",defaultValue="")String token,@RequestParam(defaultValue="")String q,@RequestParam(required=false)Long category,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="24")int size){return catalog.list(token,true,q,category,page,size);}
    @PostMapping("/seller/products") public Map<String,Object> publish(@CookieValue(name="shop_session",defaultValue="")String token,@RequestBody JsonNode data){return catalog.publish(token,data);}
    @PostMapping("/seller/products/{id}/stock") public Map<String,Object> stock(@CookieValue(name="shop_session",defaultValue="")String token,@PathVariable long id,@RequestBody JsonNode data){return catalog.changeStock(token,id,data);}
    @PostMapping("/seller/categories") public Map<String,Object> category(@CookieValue(name="shop_session",defaultValue="")String token,@RequestBody JsonNode data){return catalog.createCategory(token,data);}
    @PostMapping("/seller/categories/{id}") public Map<String,Object> rename(@CookieValue(name="shop_session",defaultValue="")String token,@PathVariable long id,@RequestBody JsonNode data){return catalog.renameCategory(token,id,data);}
    @PostMapping("/seller/categories/{id}/delete") public Map<String,Object> delete(@CookieValue(name="shop_session",defaultValue="")String token,@PathVariable long id,@RequestBody JsonNode data){Input.object(data);return catalog.deleteCategory(token,id);}
}
