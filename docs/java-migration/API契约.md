# Java 前后端接口契约 v0.2.0

此页描述本地Java实际实现。前端负责人谢相逸；后端按buyer/product/trade/account包分工；庄云月维护跨层验收。全部使用UTF-8 JSON，前后端同源。成功HTTP 200，失败JSON为 `{"error":"原因"}`。

## 请求约定

- POST必须为JSON对象，带 `Content-Type: application/json` 和 `X-Requested-With: ShopApp`；最多3MB，空体413。
- 浏览器Origin存在时必须匹配当前服务；不启用CORS。登录Cookie为HttpOnly、SameSite=Strict、Path=/，有效8小时。
- 文本不接受数字/数组/null强制转换；编号为JSON正整数，拒绝true、1.0和"1"。价格为元字符串，如"19.90"；响应price_cents为1990。
- 商品状态active/frozen/sold，意向状态pending/selected/completed/closed；交易操作freeze/success/failure。

## 接口清单与负责人

| 接口 | 负责人 | 请求 | 成功响应 |
|---|---|---|---|
| GET /api/product | 余昕越 | 无 | product为对象或null；仅id/name/description/image/price_cents/status/created_at |
| POST /api/intents | 余昕越 | product_id、buyer_name、contact、note（可省略） | id、message |
| GET /api/products | 王暾 | 卖家Cookie | products数组；含selected_intent_id、sold_at；id降序 |
| POST /api/products | 王暾 | 卖家Cookie；name、description、price、image | id、message |
| GET /api/trades | 黄扬轩 | 卖家Cookie | product当前商品或null；intents意向数组、events过程数组，id降序 |
| POST /api/trade | 黄扬轩 | 卖家Cookie；product_id、action；freeze另需intent_id | message |
| GET /api/account | 曾大君 | Cookie可选 | logged_in布尔值、username（seller） |
| POST /api/login | 曾大君 | username、password | message；响应Set-Cookie |
| POST /api/logout | 曾大君 | 空对象 | message；清除当前会话Cookie |
| POST /api/password | 曾大君 | 卖家Cookie；old_password、new_password | message；清除全部会话及当前Cookie |

intents每项包含id/product_id/buyer_name/contact/note/status/created_at/product_name。events包含id/product_id/intent_id/action/created_at/product_name/buyer_name。买家公开接口不会返回这些数组。

## 错误和边界

| 错误码 | 情况 |
|---|---|
| 400 | 非法JSON/非对象、字段类型或长度错误、无效金额/图片/电话/操作、原密码错误 |
| 401 | 未登录/过期/伪造会话、用户名或密码错误 |
| 403 | 缺少安全标识，或Origin不匹配 |
| 404 | 不存在的页面/API，交易目标商品不存在 |
| 409 | 未售商品冲突、商品不可接收意向、同电话重复意向、非法交易状态/意向选择 |
| 413 | 请求为空或超过3MB |
| 415 | POST不使用application/json |

名称1～80、描述1～2000、姓名1～40、备注0～300个Unicode字符。电话输入6～30字符，去空格/括号/横线后为可选单个前导+及6～20位数字。密码按原值校验（不去首尾空白），新密码8～128字符且不能与旧密码相同。

图片为PNG/JPEG/WebP Data URL；原始字节最多2MB；Base64与文件头必须匹配MIME。校验不是完整图片解码。金额0.01～9999999.99元，最多两位小数。

## 原子状态转换

| 当前状态 | 操作 | 结果 |
|---|---|---|
| 无未售商品 | 发布 | active；同时最多一件active或frozen |
| active | freeze，选择同商品pending意向 | 商品frozen，意向selected，保存selected_intent_id，记录freeze事件 |
| frozen | failure | 商品active，清空selected_intent_id，所选意向pending，记录failure事件 |
| frozen | success | 商品sold并记sold_at，选中意向completed，其余closed，记录success事件 |

状态修改在SQLite BEGIN IMMEDIATE事务内，拒绝操作不产生部分写入。卖家业务写操作在同一事务重新检查会话，避免改密后旧会话穿透。

第一阶段接口路径与前端字段延续；本次额外收紧电话加号位置，拒绝原版可能放过的`138+00138000`或多个`+`。
