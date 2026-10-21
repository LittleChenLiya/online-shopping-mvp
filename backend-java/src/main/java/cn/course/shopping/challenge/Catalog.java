package cn.course.shopping.challenge;

import cn.course.shopping.account.AccountService;
import cn.course.shopping.common.*;
import cn.course.shopping.product.ProductService;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static cn.course.shopping.common.Database.*;

/** 多商品、两级分类、图片集和库存。reserved由冻结订单求和，不另存易失配的计数。 */
@Service
public class Catalog {
    private final Database db;
    private final AccountService seller;
    public Catalog(Database db,AccountService seller){this.db=db;this.seller=seller;}
    static final String SELECT="SELECT p.*,c.name AS category_name,c.parent_id,r.name AS parent_name,COALESCE((SELECT SUM(o.quantity) FROM a_orders o WHERE o.product_id=p.id AND o.status='frozen'),0) AS reserved FROM a_products p JOIN a_categories c ON c.id=p.category_id LEFT JOIN a_categories r ON r.id=c.parent_id";
    static Map<String,Object> product(Connection c,long id) throws SQLException {return decorate(c,one(c,SELECT+" WHERE p.id=?",id));}
    private static Map<String,Object> decorate(Connection c,Map<String,Object> row) throws SQLException {
        if(row==null)return null;
        long stock=((Number)row.get("stock")).longValue(), reserved=((Number)row.get("reserved")).longValue();
        row.put("available",stock-reserved);
        row.put("status",((Number)row.get("listed")).intValue()==0||stock==0?"off":stock==reserved?"frozen":"active");
        List<String> images=new ArrayList<>();
        for(Map<String,Object> image:rows(c,"SELECT image FROM a_images WHERE product_id=? ORDER BY position",row.get("id"))) images.add((String)image.get("image"));
        row.put("images",images); return row;
    }
    public Map<String,Object> categories(){return db.read(c->Map.of("categories",rows(c,"SELECT id,name,parent_id FROM a_categories ORDER BY COALESCE(parent_id,id),parent_id,id")));}
    public Map<String,Object> createCategory(String token,JsonNode data){
        return db.write(c->{seller.require(c,token);String name=Input.text(data,"name",1,40);Long parent=null;
            if(data.hasNonNull("parent_id")){parent=Input.id(data,"parent_id");Map<String,Object> root=one(c,"SELECT * FROM a_categories WHERE id=?",parent);
                if(root==null)throw new ApiException(404,"一级分类不存在");
                if(root.get("parent_id")!=null)throw new ApiException(400,"只允许两级分类，不能创建第三级");}
            try{return Map.of("id",insert(c,"INSERT INTO a_categories(name,parent_id) VALUES(?,?)",name,parent),"message","分类已创建");}
            catch(SQLException e){if(e.getErrorCode()==19)throw new ApiException(409,"同级分类名称重复");throw e;}
        });
    }
    public Map<String,Object> renameCategory(String token,long id,JsonNode data){
        return db.write(c->{seller.require(c,token);if(one(c,"SELECT 1 FROM a_categories WHERE id=?",id)==null)throw new ApiException(404,"分类不存在");
            try{update(c,"UPDATE a_categories SET name=? WHERE id=?",Input.text(data,"name",1,40),id);}
            catch(SQLException e){if(e.getErrorCode()==19)throw new ApiException(409,"同级分类名称重复");throw e;}
            return Map.of("message","分类已重命名");});
    }
    public Map<String,Object> deleteCategory(String token,long id){
        return db.write(c->{seller.require(c,token);if(one(c,"SELECT 1 FROM a_categories WHERE id=?",id)==null)throw new ApiException(404,"分类不存在");
            if(one(c,"SELECT 1 FROM a_categories WHERE parent_id=?",id)!=null||one(c,"SELECT 1 FROM a_products WHERE category_id=?",id)!=null)
                throw new ApiException(409,"分类仍有关联子类或商品，不能删除");
            update(c,"DELETE FROM a_categories WHERE id=?",id);return Map.of("message","分类已删除");});
    }
    public Map<String,Object> publish(String token,JsonNode data){
        return db.write(c->{seller.require(c,token);
            String name=Input.text(data,"name",1,80),description=Input.text(data,"description",1,20000);
            long cents=ProductService.price(data.get("price")),category=Input.id(data,"category_id"),stock=nonnegative(data,"stock",1_000_000);
            Map<String,Object> leaf=one(c,"SELECT * FROM a_categories WHERE id=?",category);
            if(leaf==null)throw new ApiException(404,"分类不存在");
            if(leaf.get("parent_id")==null)throw new ApiException(400,"请把商品归入二级分类");
            JsonNode pictures=data.get("images");
            if(pictures==null||!pictures.isArray()||pictures.size()<1||pictures.size()>6)throw new ApiException(400,"请上传1～6张商品图片");
            List<String> images=new ArrayList<>();for(JsonNode picture:pictures)images.add(ProductService.image(picture));
            long id=insert(c,"INSERT INTO a_products(name,description,price_cents,category_id,stock,listed) VALUES(?,?,?,?,?,?)",name,description,cents,category,stock,stock>0?1:0);
            for(int i=0;i<images.size();i++)update(c,"INSERT INTO a_images(product_id,position,image) VALUES(?,?,?)",id,i,images.get(i));
            return Map.of("id",id,"message",stock==0?"商品已保存，零库存自动下架":"商品已发布");});
    }
    public Map<String,Object> changeStock(String token,long id,JsonNode data){
        return db.write(c->{seller.require(c,token);Map<String,Object> product=product(c,id);if(product==null)throw new ApiException(404,"商品不存在");
            long stock=nonnegative(data,"stock",1_000_000),reserved=((Number)product.get("reserved")).longValue();
            if(stock<reserved)throw new ApiException(409,"库存总量不能小于正在交易中预留的数量");
            boolean listed=stock>0;
            if(data.has("listed")){if(!data.get("listed").isBoolean())throw new ApiException(400,"listed须为布尔值");listed=data.get("listed").booleanValue()&&stock>0;}
            if(!listed&&reserved>0)throw new ApiException(409,"有正在交易的订单，不能手动下架");
            update(c,"UPDATE a_products SET stock=?,listed=? WHERE id=?",stock,listed?1:0,id);
            return Map.of("message",listed?"库存已更新并上架":"商品已下架");});
    }
    public Map<String,Object> detail(long id){return db.read(c->{Map<String,Object> p=product(c,id);if(p==null||"off".equals(p.get("status")))throw new ApiException(404,"商品不存在或已下架");return Map.of("product",p);});}
    public Map<String,Object> list(String token,boolean admin,String query,Long category,int page,int size){
        if(query==null)query="";final String q=query.strip();
        if(q.codePointCount(0,q.length())>100||page<1||page>1000000||size<1||size>100)throw new ApiException(400,"搜索或分页参数不合法");
        return db.read(c->{if(admin)seller.require(c,token);
            String where=admin?" WHERE 1=1":" WHERE p.listed=1 AND p.stock>0";List<Object> args=new ArrayList<>();
            if(!q.isEmpty()){where+=" AND instr(lower(p.name||' '||p.description),lower(?))>0";args.add(q);}
            if(category!=null){if(category<=0)throw new ApiException(400,"分类编号须为正整数");where+=" AND (p.category_id=? OR c.parent_id=?)";args.add(category);args.add(category);}
            long total=((Number)one(c,"SELECT count(*) AS n FROM a_products p JOIN a_categories c ON c.id=p.category_id"+where,args.toArray()).get("n")).longValue();
            args.add(size);args.add((page-1)*size);List<Map<String,Object>> products=rows(c,SELECT+where+" ORDER BY p.id DESC LIMIT ? OFFSET ?",args.toArray());
            for(Map<String,Object> p:products)decorate(c,p);
            return Map.of("products",products,"total",total,"page",page,"page_size",size);});
    }
    static long nonnegative(JsonNode data,String key,long maximum){Input.object(data);JsonNode n=data.get(key);if(n==null||!n.isIntegralNumber()||!n.canConvertToLong()||n.longValue()<0||n.longValue()>maximum)throw new ApiException(400,key+"必须为0～"+maximum+"的整数");return n.longValue();}
}
