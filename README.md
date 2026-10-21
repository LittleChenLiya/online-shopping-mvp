# 在线购物系统 · Java 版 / 挑战 A

面向单一卖家的线下交易系统。基线版保留单商品及游客意向流程；挑战 A 新增多商品、两级分类、搜索、多图与格式化描述、数量库存、客户注册登录、购买历史和卖家客户管理。

当前正式源码为 **Java 17 + Spring Boot 3.5.16 + Maven + SQLite**；前端为 HTML/CSS/JavaScript。第一阶段 Python 源码已归档到 `legacy/python-stage1/`，Java 的启动、构建和测试不依赖 Python。

## 挑战 A 启动与规则

Windows 双击 `启动挑战A.cmd`，或执行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-challenge-a.ps1`。需要 JDK 17+ 和 Maven，启动后访问 **http://127.0.0.1:8001/challenge-a**。

卖家初始账号 `seller` / `seller12345`；客户自行注册。首次先创建一级及二级分类，再发布商品。默认使用 `data/challenge-a.db`，重启保留数据。

库存规则按待教师确认的假设实现：提交意向不占库存，卖家开始交易时预留数量，成交扣减库存，失败或取消释放预留，剩余库存仍可购买。详细范围、接口及其他待确认事项见 [挑战 A 实现说明](挑战A-实现说明.md)。代码交付不代表教师验收通过。

## 基线版启动

第二次阶段性评审材料已按教师新版模板整理：[固定评审首页](https://github.com/LittleChenLiya/online-shopping-mvp/wiki) · [第2次阶段性评审报告](https://github.com/LittleChenLiya/online-shopping-mvp/wiki/第2次阶段性评审报告) · [可下载Markdown副本与模板核对](docs/review-02/)。

Windows启动器会把Java日志写到logs/server.log与server-error.log，确认服务就绪后自动打开浏览器，避免终端输出暂停影响网页。使用-NoBrowser可关闭自动打开。运行包提供“停止Java系统.bat”；源码可运行scripts/stop.ps1停止。

需要 **JDK 17+、Maven 3.6.3+**；首次构建需联网下载 Maven 依赖。

Windows 在仓库根目录双击 `启动系统.bat`，或运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start.ps1
```

脚本先构建再启动。浏览器访问 **http://127.0.0.1:8000**，Ctrl+C 停止。首次账号 `seller`，初始密码 `seller12345`，可在“卖家账号”修改；重启不会覆盖已有密码或商品数据。

通用命令（Windows / Linux / macOS，在仓库根目录执行）：

```sh
mvn clean verify
java -Dfile.encoding=UTF-8 -jar backend-java/target/online-shopping.jar
```

运行 JAR 只需 Java；前端已打进 JAR，无需另启 Node/Python 服务。若 `mvn -version` 显示 Java 8，请将 `JAVA_HOME` 指向 JDK 17；Windows 脚本会优先选用 PATH 中可用的 Java 17，也支持 `SHOP_JAVA_HOME`。

Windows 自定义端口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start.ps1 -Port 8001
```

| 配置 | 默认值 | 说明 |
|---|---|---|
| SHOP_DB_PATH | data/shop.db | 相对运行目录；建议从仓库根目录启动，或指定绝对路径 |
| SHOP_INITIAL_PASSWORD | seller12345 | 仅首次建库设置，8～128 字符 |
| SHOP_HOST / SHOP_PORT | 127.0.0.1 / 8000 | 直接运行 JAR 的监听地址/端口；start.ps1 使用 -BindAddress / -Port |
| SHOP_MAVEN_REPO | Maven 默认本地仓库 | Windows 脚本可选，用于指定可写依赖缓存 |

旧 SQLite 表结构、整数分金额、PBKDF2-SHA256 密码格式和会话格式保持兼容。接入第一阶段正式数据库前先备份，通过 SHOP_DB_PATH 指定副本验证；不要同时启动两版服务操作同一数据库。

## 六人分工：按前端与后端接口协作

Java 工程集中在 `backend-java/`，原 01～06 目录保留成员说明和验收材料。分工是责任规划，不替代成员实际提交记录。

| 负责人 / GitHub | 方向 | 当前维护文件与接口 |
|---|---|---|
| 谢相逸 / LittleChenLiya | 前端 | 01_前端界面：index.html、style.css、app.js、api.js；页面与接口调用 |
| 余昕越 / ygccbpkn12345 | Java 后端：买家接口 | buyer/BuyerController.java、BuyerService.java；GET /api/product、POST /api/intents |
| 王暾 / wangtun773 | Java 后端：商品接口；组长 | product/ProductController.java、ProductService.java；GET/POST /api/products；接口协调与审查 |
| 黄扬轩 / aixuan1206 | Java 后端：交易接口 | trade/TradeController.java、TradeService.java；GET /api/trades、POST /api/trade |
| 曾大君 / Sisyphu-s | Java 后端：账号与系统集成 | account/、common/、web/、ShopApplication.java；认证、数据库、请求边界与构建 |
| 庄云月 / Ricee1129 | Java 系统测试与跨层验收 | backend-java/src/test/java/、06_系统测试；接口、并发、数据兼容及浏览器验收 |

表中 Java 路径相对 `backend-java/src/main/java/cn/course/shopping/`。组号第十二组，组长王暾，仓库由谢相逸持有。

协作流程：共同确认 [接口契约](docs/java-migration/API契约.md) → 后端按接口实现 → 前端通过 api.js 调用 → 庄云月执行跨层回归并记录缺陷 → 组长核对需求和证据。接口变更时同步改契约、前端与测试。

## 需求与验收

| 编号 | 功能 | 关键验收条件 |
|---|---|---|
| F01 | 公开商品浏览 | 仅公开 active/frozen 商品；无商品返回 null；不泄露意向和买家信息 |
| F02 | 提交购买意向 | 仅 active 接受；电话规范化后同商品唯一；意向不自动冻结或成交 |
| F03 | 卖家登录退出 | 固定单一卖家；受保护接口未登录返回401；退出使当前会话失效 |
| F04 | 发布商品 | 在售/冻结合计最多一件，并发请求也不得突破 |
| F05 | 商品历史 | 新到旧保留 active/frozen/sold；已售商品保留成交时间 |
| F06 | 查看意向 | 卖家可查看姓名、电话、备注、状态；买家不能查看他人信息 |
| F07 | 选择并冻结 | active + 本商品 pending 意向 → frozen + selected；记录事件 |
| F08 | 交易成功 | frozen → sold；选中意向 completed，其余 closed，允许发布下一件 |
| F09 | 交易失败 | frozen → active；清空选择，原意向恢复 pending，保留过程记录 |
| F10 | 修改密码 | 校验原密码，新密码8～128字符且不同；更新哈希并清除所有会话 |

金额使用 BigDecimal 精确转整数分。图片必填 PNG/JPEG/WebP，最大2MB，校验 Data URL、Base64 和文件头；此校验不等于完整图片解码。名称1～80字、描述1～2000字、姓名1～40字、备注最多300字；字符数按 Unicode 码点计算。电话原输入6～30字符，去空格/括号/横线后为可选单个前导加号与6～20位数字。请求体最多3MB，编号必须为 JSON 正整数，禁止布尔值、浮点数或字符串代替。

SQLite 的 BEGIN IMMEDIATE 与未售商品唯一索引保护并发；商品、意向、事件在同一写事务更新。SQL使用绑定参数。密码随机盐 PBKDF2-SHA256；服务端仅保存会话令牌 SHA-256，Cookie 为 HttpOnly、SameSite=Strict、8小时有效。默认仅本机 HTTP 演示。

## 接口

响应为 JSON；成功200，失败 `{ "error": "原因" }`。POST 使用 Content-Type: application/json、X-Requested-With: ShopApp，浏览器Cookie同源携带。价格输入元字符串，price_cents输出整数分。

| 方法与路径 | 参数 / 响应 | 登录 |
|---|---|---|
| GET /api/product | product：对象或null；白名单公开商品字段 | 否 |
| POST /api/intents | product_id、buyer_name、contact、note；返回id/message | 否 |
| GET /api/products | products数组，全部商品历史 | 是 |
| POST /api/products | name、description、price、image；返回id/message | 是 |
| GET /api/trades | product、intents、events | 是 |
| POST /api/trade | product_id、action（freeze/success/failure）；freeze需intent_id | 是 |
| GET /api/account | logged_in、username | 否 |
| POST /api/login | username、password；设置Cookie | 否 |
| POST /api/logout | 空对象；清除当前会话与Cookie | 否 |
| POST /api/password | old_password、new_password；清除全部会话 | 是 |

错误码：400参数/JSON错误、401认证失败、403跨站或缺安全标识、404不存在、409业务冲突、413请求过大/空、415非JSON。详见 [接口契约](docs/java-migration/API契约.md)。

## 验证与构建

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1
```

或直接 `mvn clean verify`；JUnit/Surefire原始结果在 backend-java/target/surefire-reports/。Windows脚本同时生成06_系统测试/results-java的真实报告、XML和源码指纹。测试通过随机端口真实HTTP和独立临时SQLite执行，不操作正式数据库。

测试覆盖完整交易、权限与改密、输入边界、并发发布/意向/冻结/冲突交易、拒绝操作不改变数据、静态路径限制及旧密码兼容。历史Python报告随第一阶段归档，不作为Java测试结果。

Docker构建与运行：

```sh
docker build -t online-shopping-java:0.2.0 .
docker run --rm -p 127.0.0.1:8000:8000 -v shop-data:/data online-shopping-java:0.2.0
```

CI已改为Java构建与测试；本地结果与未执行的Docker/CI分开记录，详见 [迁移说明](docs/java-migration/迁移说明.md)。

## 顺序演示

空首页 → 卖家登录并发布带图片商品 → 买家填写意向 → 卖家选择并冻结 → 失败恢复 → 再次冻结并成交 → 查看历史并发布下一件 → 修改密码后重新登录。

图片预览、取消确认、快速切页、窄屏布局与完整流程需浏览器验收。结果详见docs/java-migration/；自动接口测试不能代替课堂演示录屏。

可选浏览器回归需要Node 22+及Edge（Windows默认）或Playwright Chromium：运行 `npm ci`、`npm run test:browser`。非Windows先执行 `npx playwright install chromium`。脚本会自动启动已构建JAR，使用隔离数据库，生成截图与JSON结果。运行购物系统本身不需要Node。

原始PPT、演示视频和Word需求保留；[历史汇报](docs/presentation-2026-10-15/) · [第一阶段归档](legacy/README.md) · [Wiki](https://github.com/LittleChenLiya/online-shopping-mvp/wiki)。
