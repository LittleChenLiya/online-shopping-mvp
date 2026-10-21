# 成员五：卖家账号与系统集成

## 我的任务和文件

- `database.py`：共享数据库结构、初始化、事务和公开字段转换。
- `security.py`：固定卖家登录、退出、会话验证和修改密码。
- `server.py`：HTTP 服务、静态文件、JSON 校验、模块路由合并、错误响应。
- `test_system.py`：用临时数据库运行六组集成测试。
- 根目录 `start.py`、`启动系统.bat`：统一启动入口。

成员 2/3/4 负责业务 SQL；本模块提供统一的连接和权限函数。成员 1 对接合并后的 HTTP API。

## 关键函数

| 文件 / 函数 | 功能 |
|---|---|
| database.connection(write=False) | 创建连接、启用外键；写操作 BEGIN IMMEDIATE；正常提交，异常回滚，最后关闭 |
| database.initialize(password_hash) | 建表、索引和唯一卖家，已有数据不覆盖 |
| database.public_product(row) | 白名单输出公开字段，避免内部字段直接暴露 |
| security.hash_password(password) | 随机盐 + PBKDF2-HMAC-SHA256，返回轮数/盐/哈希的组合字符串 |
| security.verify_password(password, stored) | 从存储值取盐与轮数重新计算，使用 compare_digest 比较 |
| security.session_hash(request) | 从 Cookie 读取令牌，再哈希为数据库查找键 |
| security.logged_in(request) / require_seller(request) | 判断有效会话 / 未登录抛出 401 |
| security.login(request, data) | 验证固定账号和密码，创建随机会话并设置 Cookie |
| security.logout(request, data) | 删除当前会话并清除 Cookie |
| security.change_password(request, data) | 验证旧密码和会话、更新哈希，清除全部会话，要求重新登录 |
| security.account(request, data) | 返回是否登录及固定账号名称，不返回密码和会话 |
| Handler.text() / integer() | 通用文本长度、类型和正整数校验；拒绝布尔值充当编号 |
| Handler.dispatch() | 解析请求、查找业务路由，或读取允许的静态文件；统一错误处理 |
| Handler.respond() | 写状态码、JSON/静态响应、安全头及 Cookie |
| server.main() | 解析端口、初始化数据库、启动和关闭服务器 |

## 关键变量和表

| 名称 | 含义 |
|---|---|
| ROOT / FRONTEND | 项目根目录 / 静态文件所在目录 |
| DB_PATH | SQLite 路径，默认根目录 data/shop.db；可用 SHOP_DB_PATH 设置 |
| SESSION_SECONDS | 登录有效期 8 小时，服务端按 expires_at 检查 |
| PASSWORD_ITERATIONS | PBKDF2 计算 260000 轮，提高密码猜测成本 |
| MAX_BODY_BYTES | JSON 请求体上限 3 MB，容纳 2 MB 图片的 Base64 内容 |
| ROUTES | 合并其他模块的 `(HTTP方法,路径) -> 函数` 映射 |
| seller | id 必须为 1，不提供新增卖家和注册接口 |
| sessions | 会话令牌的 SHA-256 哈希和过期时间，数据库不直接存原始令牌 |
| products / intents | 商品 / 买家意向；主键和外键关联 |
| one_unsold_product | active/frozen 行的常量表达式唯一索引，保证最多一个未售商品 |
| trade_events | 冻结、成功、失败的过程记录 |

## 安全和集成约定

密码带随机盐哈希保存。登录 Cookie 使用 HttpOnly、SameSite=Strict；POST 必须有自定义请求头，浏览器跨站 Origin 被拒绝，服务不开放 CORS。本机 HTTP 演示没有设置 Secure Cookie；部署 HTTPS 时应设置。此项目没有公网登录限流，默认仅监听本机。

处理器只允许 `/`、`/index.html`、`/app.js`、`/style.css` 静态文件，不能通过 URL 读取数据库或任意磁盘路径。未预期异常在终端记录详细日志，客户端只收到通用错误。

执行 `python start.py`；端口冲突时加 `--port 8001`。没有 Python 第三方依赖。直接执行 `python .\05_卖家账号与系统集成\test_system.py` 验证集成；测试用 D 盘临时数据库，不访问正式 data/shop.db。

## 老师可能问什么

**问：五个文件夹为什么不是五个系统？**  
答：按成员职责分模块，统一入口导入模块并合并路由，共用数据库；每个人的代码组合起来才覆盖整个基线。

**问：怎么保证只有一个卖家？**  
答：seller 的主键有 CHECK(id=1)，初始化仅插入该记录；不存在注册和新增卖家接口。

**问：为什么密码不直接保存？盐有什么作用？**  
答：泄露数据库时不应直接得到密码。随机盐让相同密码也产生不同哈希，PBKDF2 多轮计算增加尝试成本。

**问：Cookie 和 sessions 怎么关联？**  
答：浏览器拿到随机令牌；数据库存令牌的 SHA-256 哈希及过期时间。请求到达后哈希 Cookie 令牌，再查有效会话。

**问：为什么修改密码后退出全部登录？**  
答：清除 sessions，使其他浏览器持有的旧 Cookie 也不能继续访问后台，而不只是当前页面跳回登录。

**问：如何保证只能存在一件商品？**  
答：索引只包含 active/frozen 行，每行索引键都是常量 1，唯一性要求这些行最多一条；sold 行不占名额。

**问：事务为什么用 BEGIN IMMEDIATE？**  
答：写事务开始时取得写锁，商品状态检查和修改不会被另一个写操作插入其间；异常时一起回滚。

**问：重启会丢数据或重置密码吗？**  
答：不会。SQLite 写到文件，建表使用 IF NOT EXISTS，卖家初始化使用 INSERT OR IGNORE。

**问：为什么使用 HTTPServer 而不是 Flask？**  
答：基线接口少，标准库足够，省去依赖安装；框架可以简化大型项目开发，但本次选型方便课堂演示。

**问：测试做了什么，如何保证没破坏演示数据？**  
答：六组测试覆盖全流程、校验、权限与改密码、请求防护、并发发布、重复初始化；每个测试独立使用 D 盘临时数据库，结束自动删除。

**问：这些安全措施能直接用于公网生产吗？**  
答：不能直接按本机配置上线。公网还需要 HTTPS、Secure Cookie、登录限流和运维配置；本次完成的是课程基线。
