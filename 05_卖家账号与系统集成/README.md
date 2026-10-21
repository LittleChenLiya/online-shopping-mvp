# 曾大君：Java账号与系统集成

负责人：曾大君 / Sisyphu-s。源码在backend-java/src/main/java/cn/course/shopping/，旧Python集成代码/测试/说明已归档legacy/python-stage1/。

| 源码 | 职责 |
|---|---|
| ShopApplication.java | Spring Boot入口 |
| account/AccountController、AccountService | /api/account、login、logout、password；固定卖家与8小时会话 |
| account/Passwords | PBKDF2-SHA256、随机盐、令牌随机数及SHA-256摘要；兼容原Python哈希格式 |
| common/Database | SQLite表结构、参数绑定、读写事务、公开商品白名单 |
| common/Input、ApiException、ApiErrors | 严格JSON字段校验和统一错误响应 |
| web/RequestGuard | POST来源/请求头/JSON/3MB边界；响应安全头 |
| web/FrontendController | 仅提供index.html/app.js/api.js/style.css |

登录Cookie为HttpOnly、SameSite=Strict。服务端只存令牌哈希；退出删当前会话，改密删全部会话。写业务在同一事务里检查认证；PBKDF2随机盐与260000次迭代沿用旧格式，已有密码无需重置。

启动和构建见根README。JDK17、Maven；默认127.0.0.1:8000、data/shop.db。Java打包后的JAR包含前端和数据库驱动，运行不依赖Python。

数据库用BEGIN IMMEDIATE预先获取写锁，多表成功一起提交、异常一起回滚；唯一索引保证同时仅一件未售商品。启动仅INSERT OR IGNORE初始化账号，不覆盖旧密码。不要同时运行两版服务操作同一库。

系统测试由庄云月维护，曾大君负责处理集成缺陷和构建问题；详细职责以接口契约和实际PR为准。
