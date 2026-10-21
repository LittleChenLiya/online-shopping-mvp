package cn.course.shopping.challenge;

import cn.course.shopping.common.*;
import cn.course.shopping.account.Passwords;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static cn.course.shopping.common.Database.*;

@Service
public class Customers {
    private final Database db;
    public Customers(Database db) { this.db = db; }
    public Map<String, Object> current(Connection connection, String token) throws SQLException {
        return one(connection, "SELECT c.id,c.username,c.phone,c.default_location,c.created_at FROM a_customers c JOIN a_customer_sessions s ON c.id=s.customer_id WHERE s.token_hash=? AND s.expires_at>?", Passwords.tokenHash(token), now());
    }
    public Map<String, Object> require(Connection connection, String token) throws SQLException {
        Map<String, Object> customer = current(connection, token);
        if (customer == null) throw new ApiException(401, "请先登录客户账号");
        return customer;
    }
    public Map<String, Object> account(String token) {
        return db.read(c -> { Map<String, Object> result=nullable("customer", current(c,token)); result.put("logged_in",result.get("customer")!=null); return result; });
    }
    public Map<String, Object> register(JsonNode data) {
        String username=Input.text(data,"username",3,40);
        if (!username.matches("[\\p{L}0-9_-]{3,40}")) throw new ApiException(400,"用户名只能包含文字、数字、下划线或横线");
        String password=Input.text(data,"password",8,128,false);
        String phone=phone(data);
        String location=Input.text(data,"default_location",1,200);
        String hash=Passwords.hash(password);
        return db.write(c -> {
            try {
                long id=insert(c,"INSERT INTO a_customers(username,password_hash,phone,default_location) VALUES(?,?,?,?)",username,hash,phone,location);
                return Map.of("id",id,"message","注册成功，请登录客户账号");
            } catch(SQLException e) { if(e.getErrorCode()==19) throw new ApiException(409,"用户名已存在"); throw e; }
        });
    }
    public String login(JsonNode data) {
        String username=Input.text(data,"username",3,40), password=Input.text(data,"password",1,128,false);
        return db.write(c -> {
            Map<String,Object> customer=one(c,"SELECT * FROM a_customers WHERE username=? COLLATE NOCASE",username);
            if(customer==null || !Passwords.verify(password,(String)customer.get("password_hash"))) throw new ApiException(401,"用户名或密码错误");
            String token=Passwords.token();
            update(c,"DELETE FROM a_customer_sessions WHERE expires_at<=?",now());
            update(c,"INSERT INTO a_customer_sessions VALUES(?,?,?)",Passwords.tokenHash(token),customer.get("id"),now()+28800);
            return token;
        });
    }
    public void logout(String token) { db.write(c -> { update(c,"DELETE FROM a_customer_sessions WHERE token_hash=?",Passwords.tokenHash(token));return null;}); }
    public Map<String,Object> profile(String token,JsonNode data) {
        return db.write(c -> {
            Map<String,Object> customer=require(c,token);
            update(c,"UPDATE a_customers SET phone=?,default_location=? WHERE id=?",phone(data),Input.text(data,"default_location",1,200),customer.get("id"));
            return Map.of("message","资料已更新；历史订单联系方式保持原快照");
        });
    }
    public Map<String,Object> password(String token, JsonNode data) {
        return db.write(c -> {
            Map<String,Object> customer=require(c,token);
            String old=Input.text(data,"old_password",1,128,false), next=Input.text(data,"new_password",8,128,false);
            if(old.equals(next)) throw new ApiException(400,"新密码不能与原密码相同");
            Map<String,Object> row=one(c,"SELECT password_hash FROM a_customers WHERE id=?",customer.get("id"));
            if(!Passwords.verify(old,(String)row.get("password_hash"))) throw new ApiException(400,"原密码错误");
            update(c,"UPDATE a_customers SET password_hash=? WHERE id=?",Passwords.hash(next),customer.get("id"));
            update(c,"DELETE FROM a_customer_sessions WHERE customer_id=?",customer.get("id"));
            return Map.of("message","密码已修改，请重新登录");
        });
    }
    private static double now(){return System.currentTimeMillis()/1000.0;}
    private static String phone(JsonNode data){
        String phone=Input.text(data,"phone",6,30);
        if(!phone.matches("[0-9+() -]+")) throw new ApiException(400,"请填写有效联系电话");
        return phone;
    }
}
