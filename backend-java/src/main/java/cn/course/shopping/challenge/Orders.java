package cn.course.shopping.challenge;

import cn.course.shopping.account.AccountService;
import cn.course.shopping.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static cn.course.shopping.common.Database.*;

/** 本地假设：意向不占库存；开始交易预留；成功扣减；失败/取消释放。 */
@Service
public class Orders {
    private final Database db;
    private final Customers customers;
    private final AccountService seller;
    public Orders(Database db,Customers customers,AccountService seller){this.db=db;this.customers=customers;this.seller=seller;}
    public Map<String,Object> place(String token,JsonNode data){
        return db.write(c->{Map<String,Object> customer=customers.require(c,token);long productId=Input.id(data,"product_id"),quantity=Input.id(data,"quantity");
            if(quantity>1_000_000)throw new ApiException(400,"单次购买数量过大");
            Map<String,Object> product=Catalog.product(c,productId);
            if(product==null)throw new ApiException(404,"商品不存在");
            if("off".equals(product.get("status"))||((Number)product.get("available")).longValue()<quantity)throw new ApiException(409,"商品可用库存不足，请刷新页面");
            String phone=data.has("phone")?Input.text(data,"phone",6,30):(String)customer.get("phone");
            String location=data.has("trade_location")?Input.text(data,"trade_location",1,200):(String)customer.get("default_location");
            String note=Input.text(data,"note",0,300);
            long id=insert(c,"INSERT INTO a_orders(product_id,customer_id,quantity,unit_price_cents,product_name,phone,trade_location,note,status) VALUES(?,?,?,?,?,?,?,?,'pending')",productId,customer.get("id"),quantity,product.get("price_cents"),product.get("name"),phone,location,note);
            event(c,id,"submit","customer:"+customer.get("id"));
            return Map.of("id",id,"message","购买意向已登记，尚未预留库存；等待卖家开始交易，线下付款交货");});
    }
    public Map<String,Object> action(String token,long id,JsonNode data,boolean customerAction){
        return db.write(c->{Map<String,Object> customer=null;
            if(customerAction)customer=customers.require(c,token);else seller.require(c,token);
            Map<String,Object> order=one(c,"SELECT * FROM a_orders WHERE id=?",id);
            if(order==null||(customerAction&&((Number)order.get("customer_id")).longValue()!=((Number)customer.get("id")).longValue()))throw new ApiException(404,"订单不存在");
            String action=Input.text(data,"action",1,20),status=(String)order.get("status"),next;
            long productId=((Number)order.get("product_id")).longValue(),quantity=((Number)order.get("quantity")).longValue();
            if(customerAction){
                if(!action.equals("cancel"))throw new ApiException(400,"客户仅能取消自己的意向/交易");
                if(!Set.of("pending","frozen").contains(status))throw new ApiException(409,"订单已结束，不能重复取消");
                next="cancelled";
            }else{
                switch(action){
                    case "freeze" -> {
                        if(!status.equals("pending"))throw new ApiException(409,"只有待处理意向可以开始交易");
                        Map<String,Object> product=Catalog.product(c,productId);
                        if("off".equals(product.get("status"))||((Number)product.get("available")).longValue()<quantity)throw new ApiException(409,"库存已被其他交易预留，无法开始本次交易");
                        next="frozen";
                    }
                    case "success" -> {
                        if(!status.equals("frozen"))throw new ApiException(409,"必须先开始交易并预留库存");
                        int changed=update(c,"UPDATE a_products SET stock=stock-?,listed=CASE WHEN stock-?=0 THEN 0 ELSE listed END WHERE id=? AND stock>=?",quantity,quantity,productId,quantity);
                        if(changed!=1)throw new ApiException(409,"库存状态冲突");
                        next="completed";
                    }
                    case "failure" -> {if(!status.equals("frozen"))throw new ApiException(409,"只有交易中的订单可记录交易失败");next="failed";}
                    case "reject" -> {if(!status.equals("pending"))throw new ApiException(409,"只有待处理意向可以拒绝");next="failed";}
                    default -> throw new ApiException(400,"未知交易操作");
                }
            }
            update(c,"UPDATE a_orders SET status=?,updated_at=datetime('now','localtime') WHERE id=?",next,id);
            event(c,id,action,customerAction?"customer:"+customer.get("id"):"seller");
            return Map.of("message",switch(next){case "frozen"->"已预留本次购买数量，请线下联系客户交易";case "completed"->"交易成功，库存已扣减；库存为零自动下架";case "failed"->"订单已结束，预留数量已释放";default->"订单已取消，预留数量已释放";});
        });
    }
    public Map<String,Object> list(String token,boolean admin,Long customerId,int page,int size){
        checkPage(page,size);
        return db.read(c->{String where=" WHERE 1=1";List<Object> args=new ArrayList<>();
            if(admin){seller.require(c,token);if(customerId!=null){if(one(c,"SELECT id FROM a_customers WHERE id=?",customerId)==null)throw new ApiException(404,"客户不存在");where+=" AND o.customer_id=?";args.add(customerId);}}
            else{Map<String,Object> customer=customers.require(c,token);where+=" AND o.customer_id=?";args.add(customer.get("id"));}
            long total=((Number)one(c,"SELECT count(*) AS n FROM a_orders o"+where,args.toArray()).get("n")).longValue();
            args.add(size);args.add((page-1)*size);
            List<Map<String,Object>> list=rows(c,"SELECT o.*,c.username,(o.quantity*o.unit_price_cents) AS total_cents FROM a_orders o JOIN a_customers c ON c.id=o.customer_id"+where+" ORDER BY o.id DESC LIMIT ? OFFSET ?",args.toArray());
            return Map.of("orders",list,"total",total,"page",page,"page_size",size);});
    }
    public Map<String,Object> customerList(String token,int page,int size){
        checkPage(page,size);
        return db.read(c->{seller.require(c,token);
            List<Map<String,Object>> list=rows(c,"SELECT c.id,c.username,c.phone,c.default_location,c.created_at,(SELECT count(*) FROM a_orders o WHERE o.customer_id=c.id) AS order_count,COALESCE((SELECT SUM(o.quantity*o.unit_price_cents) FROM a_orders o WHERE o.customer_id=c.id AND o.status='completed'),0) AS completed_total_cents FROM a_customers c ORDER BY c.id DESC LIMIT ? OFFSET ?",size,(page-1)*size);
            long total=((Number)one(c,"SELECT count(*) AS n FROM a_customers").get("n")).longValue();
            return Map.of("customers",list,"total",total,"page",page,"page_size",size);});
    }
    private static void checkPage(int page,int size){if(page<1||page>1000000||size<1||size>100)throw new ApiException(400,"分页参数不合法");}
    private static void event(Connection c,long id,String action,String actor)throws SQLException{update(c,"INSERT INTO a_order_events(order_id,action,actor) VALUES(?,?,?)",id,action,actor);}
}
