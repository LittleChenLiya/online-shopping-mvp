# 在线购物系统：基线需求 / 六人分工

本项目面向单一卖家，买家无需注册即可浏览商品并提交购买意向；卖家在线下交易前冻结商品，成功后下架，失败则恢复上线。系统同时只允许一件未售商品，付款与交货在线下完成，只实现课程基线需求。

项目由五个业务与基础设施模块及一个系统测试模块组成，采用 Python 标准库 HTTP 服务、SQLite 数据库和 HTML/CSS/JavaScript。运行不需要第三方 Python 包，建议使用 Python 3.10 或更新版本及 Edge/Chrome 浏览器。

## 六人分工

| 负责人 | GitHub 账号 | 文件夹 | 职责与主要文件 |
|---|---|---|---|
| 谢相逸 | LittleChenLiya | `01_前端界面` | 页面、表单、交互与 API 对接；index.html、style.css、app.js |
| 余昕越 | ygccbpkn12345 | `02_商品浏览与购买意向` | 当前商品查询、买家信息校验及意向提交；buyer.py |
| 王暾（组长） | wangtun773 | `03_商品发布与历史` | 商品发布、价格图片校验及商品历史；products.py |
| 黄扬轩 | aixuan1206 | `04_交易与冻结管理` | 意向查询、冻结、成交下架与失败恢复；trades.py |
| 曾大君 | Sisyphu-s | `05_卖家账号与系统集成` | 认证、数据库、HTTP 服务及原有回归测试；database.py、security.py、server.py、test_system.py |
| 庄云月 | Ricee1129 | `06_系统测试` | 新增跨模块验收测试、执行与复核、证据及问题记录；test_acceptance.py、run_tests.py |

项目组号为第十二组。仓库由谢相逸创建并持有，组长为王暾。根目录的 `start.py` 和 `启动系统.bat` 由系统集成模块维护。各成员文件夹中的 README 说明关键函数、变量及答辩问题。01～05通过 API 和共享数据库协作，06测试这些模块；各文件夹不是分别独立的完整系统。

## 启动

从GitHub下载当前源码后，在仓库根目录运行系统集成模块的入口：

```powershell
python "05_卖家账号与系统集成/server.py"
```

根目录也提供统一启动脚本，可使用下列方式：

双击 `启动系统.bat`，或在本目录的终端中运行：

```powershell
python start.py
```

保持终端打开，在浏览器访问 **http://127.0.0.1:8000**。按 Ctrl+C 停止。

首次卖家账号：`seller`；初始密码：`seller12345`。登录后在“卖家账号”修改密码。系统没有注册入口。已有数据库中的密码不会因重新启动而重置。

端口占用时使用 `python start.py --port 8001`，访问对应端口。首次启动前可用环境变量 `SHOP_INITIAL_PASSWORD` 指定初始密码；`SHOP_DB_PATH` 可指定数据库文件。默认数据库为根目录 `data/shop.db`，启动时自动创建，停止和重新启动不会清除数据。`data` 仅保存运行数据，系统测试任务单独位于 `06_系统测试`。

## 按顺序演示

1. 首次访问买家页面，显示“当前没有在售商品”。
2. 点击“发布与历史”，登录卖家账号，填写名称、描述、价格并上传图片，发布第一件商品。
3. 用浏览器隐私窗口打开首页，查看商品并提交姓名、电话、备注。无需登录或注册。
4. 卖家在“交易管理”查看购买意向，选择一位买家并冻结商品。
5. 买家刷新首页，看到“交易中”，不能提交新意向；卖家不能发布下一件商品。
6. 先演示“交易失败”，商品恢复上线；原购买意向仍保留，可重新联系和选择买家。
7. 再次冻结，在线下完成交钱交货后点击“交易成功”，商品下架，所选意向标记成交，其余意向结束。
8. 在“发布与历史”查看已售商品，并发布下一件。修改密码后，所有卖家登录会话失效。

提交意向本身不冻结商品；卖家确认交易对象后冻结。已售商品不会删除，以便查看历史。没有购物车、在线支付、多卖家、配送、注册或其他升级需求功能。

## 接口约定

所有接口返回 JSON；成功返回 HTTP 200；失败返回 `{"error":"原因"}`。前端金额以元输入字符串，数据库和响应金额为整数分。商品编号、意向编号必须是正整数。

POST 请求设置 `Content-Type: application/json` 和 `X-Requested-With: ShopApp`。卖家登录后浏览器自动携带 HttpOnly 会话 Cookie。

| 方法与路径 | 负责成员 | 参数或响应要点 | 卖家登录 |
|---|---|---|---|
| GET /api/product | 2 | `{product:对象或null}`，含 id/name/description/image/price_cents/status/created_at | 否 |
| POST /api/intents | 2 | product_id、buyer_name、contact、note；返回 id/message | 否 |
| POST /api/products | 3 | name、description、price（字符串）、image（图片 Data URL）；返回 id/message | 是 |
| GET /api/products | 3 | `{products:[商品]}`，包含在售、冻结、已售商品 | 是 |
| GET /api/trades | 4 | `{product:当前商品或null,intents:[意向],events:[过程记录]}` | 是 |
| POST /api/trade | 4 | product_id、action；freeze 另需 intent_id | 是 |
| GET /api/account | 5 | logged_in、username | 否 |
| POST /api/login | 5 | username、password；设置 Cookie | 否 |
| POST /api/logout | 5 | 空对象；删除当前会话 | 否 |
| POST /api/password | 5 | old_password、new_password；清除所有会话 | 是 |

常见错误码：400 参数错误、401 未登录或凭据错误、403 请求安全校验失败、404 不存在、409 业务状态冲突、413 请求过大、415 请求格式错误。

## 状态与数据关系

```text
发布 → active（在售）
active + 选择有效购买意向 → frozen（冻结）
frozen + 交易失败 → active
frozen + 交易成功 → sold（已售 / 下架）→ 可以发布下一件
```

`seller` 只有一条记录；`products` 保存商品；`intents` 通过 product_id 关联商品；`products.selected_intent_id` 指向冻结时选中的买家；`trade_events` 保存每次冻结、成交、失败的记录；`sessions` 保存有期限的卖家会话哈希。

同一商品可以有多个意向买家，但一次交易只选择一个。SQLite 写事务和唯一索引共同保证同时最多一个未售商品。冻结、修改意向状态、记录过程在同一事务中完成。

## 验证

`06_系统测试`包含可执行测试、用例、日志与复核记录。下载仓库后可执行全部14组；下列现场结论只对应记录中的本机版本，新环境需重新执行并查看真实结果。

在项目根目录执行完整自动测试，或双击 `06_系统测试/运行测试.bat`：

```powershell
python -X utf8 -B "06_系统测试/run_tests.py"
```

06模块复用第五模块6组回归测试，新增8组验收测试，共14组，涵盖完整交易、权限和改密码、唯一商品、并发意向与冻结、冲突交易结果、非法HTTP输入、字段/图片上限、私有路径及特殊字符保存。两部分来源明确区分，不重复计数。

测试会自动临时启动服务，不需要先启动演示系统；每个用例使用独立临时数据库与随机端口，结束自动关闭和清理。Windows有D盘时，06脚本默认使用 `D:/Codex/work/shopping-tests-06`，也可通过 `SHOP_TEST_TEMP_DIR` 配置临时目录。正式商品与密码不用于自动化测试。

结果保存在 `06_系统测试/results`：

- `测试报告.md`、`test-log.txt`、`test-results.json`：真实测试结论、输出和源文件指纹。
- `复核记录.md`：断言、执行方式和正式数据库前后哈希核对。
- `问题记录-T001.md`：已观察到的连接异常及复现情况。

**2026-10-15最新现场结果：14组中13组通过、1组错误；新增8组全部通过。** 原有 `test_request_guards` 在非法Content-Type请求上出现间歇性的 Windows WinError10053，预期415响应未正常读到，原因尚未定位。正式数据库前后SHA-256一致。不能沿用历史某次全部通过的结果，或宣称此异常已修复。

本次执行负责人为庄云月，现场由Codex辅助运行与复核。浏览器图片预览、确认框、快速切页、窄屏布局及真实运行录屏仍需另行验证；自动测试通过不等于界面或公网部署已经验收。

随后发布前对集成副本执行的14组测试全部通过，Python/JavaScript语法检查和构建也通过，见 `06_系统测试/results/发布前验证说明.md`。由于T-001曾间歇出现，保留前述现场失败证据，不以本次通过宣称异常已修复。

如需单独执行原有回归或前端语法检查：

```powershell
python -X utf8 -B "05_卖家账号与系统集成/test_system.py"
node --check "01_前端界面/app.js"
```

Node仅用于开发阶段的JavaScript语法检查，运行项目不需要Node。

这是本机课程演示项目，服务默认仅监听 127.0.0.1，使用 HTTP。商品和记录增多后，历史接口目前一次返回全部记录；分页、公网 HTTPS 部署、登录限流等不属于本次基线实现。用户手工在浏览器完成一次上述演示可以检查页面交互。

## GitHub 与课程材料

- [公开仓库](https://github.com/LittleChenLiya/online-shopping-mvp) · [固定 Wiki Home](https://github.com/LittleChenLiya/online-shopping-mvp/wiki) · [版本与源码下载](https://github.com/LittleChenLiya/online-shopping-mvp/releases)
- [历史汇报材料与系统演示](docs/presentation-2026-10-15/)：保留此前发布的PPT与演示视频，当前六人版PPT和需求Word见根目录。
- `第一次汇报.pptx`：六人个人工作汇报，共13页，庄云月的测试部分包含最新结果和T-001问题。
- `在线购物系统需求规格说明书.docx`：5页，包含角色与用例、业务规则、输入约束、数据接口和验收追踪。
- `06_系统测试`：测试代码、README、用例与手工清单、个人汇报提纲、日志和复核记录。
- 第五模块 `docs`、`wiki` 中保留早期准备材料；涉及当前人员、执行结果时，应核对最新报告和在线Wiki，不沿用旧占位或历史测试结论。
- 本机已提供构建脚本、Dockerfile和CI配置草稿，其存在不代表镜像构建或部署已验证；完整部署证据及真实测试录屏仍需补充。
- 项目采用AI辅助开发与文档整理；各成员应如实登记自己的修改、执行及复核工作，不代造个人提交或完成记录。
- 当前仓库同步了六个模块、启动与构建配置、六人版PPT、需求Word及实际测试记录。历史准备稿和早期视频保留作参考；当前状态以根目录文档、最新实际测试记录及在线Wiki为准。
