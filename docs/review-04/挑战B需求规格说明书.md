# 挑战 B 需求规格说明书

版本：2.0；更新日期：2026-11-26；适用：在线购物系统第四次阶段性评审。

本版把原 B-R01～B-R10 的粗粒度条目细化为 **105 条可单独验证的需求**，覆盖收藏、购物车、批量转移、多商品下单、五阶段流转、双方取消、共享库存、查询权限、页面和可靠性。每条给出编号、明确规则及“操作 → 预期结果”，供开发、评审和验收共用。

## 1. 需求依据与范围

### 1.1 来源分级

| 标记 | 来源 | 本文含义 |
|---|---|---|
| 课B.1-1 | [课程需求 B.1 第1项](https://github.com/sebestp/coursewise/wiki/课程需求) | 订单具有客户下单、商家确认、备货完成、开始发货、交易完成五个阶段 |
| 课B.1-2 | 同上 B.1 第2项 | 客户可以在开始发货前取消订单 |
| 课B.1-3 | 同上 B.1 第3项 | 商家可以在交易完成前取消订单 |
| 课B.2-1～4 | 同上 B.2 第1～4项 | 收藏、加入购物车、购物车多选下单、购物车多选转收藏 |
| 澄1 | 既有第4次报告记录的组长转述澄清 | 一次提交生成一笔含多商品清单的订单，展示名称、单价、数量和总金额 |
| 澄2 | 同上 | 逐项核对库存，缺货项排除，其余有货项正常下单 |
| 继A | 挑战 A 的既有能力 | 注册客户、商品和库存、客户历史、卖家客户档案继续提供，并接入 B |
| 约 | 本项目为使需求可实现、可验收而采用的具体约定 | 数量上限、幂等、快照、预留时点、整单取消等，不冒充课程新增原文要求 |

澄1、澄2 的来源为[既有第四次阶段性评审报告](https://github.com/LittleChenLiya/online-shopping-mvp/blob/main/docs/review-04/第四次阶段性评审报告.md)的记录，尚无独立教师澄清原件链接。若教师后续调整边界，应据其意见修改约定及对应测试。

### 1.2 本轮业务范围

以单卖家和已注册客户为参与者，升级商品预选和订单处理。继续复用 A 的账号、商品、分类、图片和库存。B 订单具有独立主表、商品明细及事件记录。

线上支付、银行卡与第三方支付、多收货地址、物流轨迹和售后申请属于后续挑战 C，不作为本轮验收项；“开始发货”在本轮是卖家登记的业务状态，不等于已对接快递接口。付款交接仍按既有线下方式处理。

### 1.3 角色与前置条件

| 角色 | 允许行为 |
|---|---|
| 游客 | 使用现有公开商品浏览；操作个人预选或订单前需登录 |
| 已登录客户 | 管理本人收藏和购物车，多选下单，查看本人订单，在发货前取消本人订单 |
| 已登录卖家 | 查看全部客户及订单、按客户筛选，顺序推进订单，在交易完成前取消订单 |

客户使用 shop_customer_session Cookie，卖家使用 shop_session Cookie，二者独立。写请求还须遵守第 3 节公共接口约定。

## 2. 业务术语、状态及边界

### 2.1 术语与金额

| 术语 | 定义 |
|---|---|
| 一种商品 | 一个 product_id；同款购买多件由 quantity 表示 |
| 一笔订单 | 一个主订单 ID，可含多条商品明细；同单共用状态 |
| 实物库存 stock | 尚未正式发货扣除的库存总数 |
| 预留 reserved | A 中 frozen 的数量，加 B 中 placed、confirmed、ready 的明细数量 |
| 可用 available | stock-reserved；不同页面和下单使用同一口径 |
| 单价/小计/总额 | 接口以整数分表示，界面转换为元；subtotal_cents=unit_price_cents×quantity |
| request_key | 客户生成的下单请求标识，按客户范围唯一；用于重放成功创建的订单 |

### 2.2 状态、操作及库存

| 当前状态 | 含义 | 卖家允许的 action | 客户允许的 action | 本单库存含义 |
|---|---|---|---|---|
| placed | 客户下单 | confirm、cancel | cancel | 已预留、未扣实物 |
| confirmed | 商家确认 | ready、cancel | cancel | 保持预留 |
| ready | 备货完成 | ship、cancel | cancel | 保持预留 |
| shipped | 开始发货 | complete、cancel | 无 | 已扣实物、不再预留 |
| completed | 交易完成 | 无 | 无 | 已扣实物，终态 |
| cancelled | 已取消 | 无 | 无 | 释放预留或恢复已发数量，终态 |

五个阶段是正常成功路径；cancelled 是取消终态。所有操作对整单生效。状态冲突或重复操作应返回 409，不能重复扣减或恢复库存。

### 2.3 必须明确的业务约定

| 问题 | 本版规则 | 验收关注点 |
|---|---|---|
| 车中存 3 件但库存只有 2 件 | 允许保存购物车；结算时整条 P 缺货，不自动减为 2 件 | 不能把加购成功理解为已预留或一定能下单 |
| 部分缺货 | 有货项合为一单；缺货项留车，返回独立 rejected 清单 | 订单、缺货反馈、购物车三处一致 |
| 全部缺货 | created=false，不返回 order，不创建订单 | 不生成零明细订单，也不占库存 |
| 同款多件 | 一个商品一条明细，数量可以大于 1 | 区别“商品种数”和“购买件数” |
| 下单联系资料 | 使用客户当前 phone、default_location，不接受该接口临时覆盖 | 历史订单保留快照 |
| 卖家发货后取消 | 当前实现恢复已扣数量并上架商品 | 按整单作废处理；实际实物退回须在线下核实 |
| 成功重试 | 同客户、同 key、同商品集合返回原单和原缺货记录，集合顺序无关 | 不按修改后的购物车重新计算原单 |
| 全缺货重试 | 未成功建单时不保存幂等订单记录，同 key 再调用会重新判断 | 本轮没有为全缺货结果提供永久重放承诺 |
| 移除不存在条目 | 返回成功，不影响其他客户；移除 POST 仍须传 {} | 不以 404 作为重复移除的预期 |
| 新建、移除收藏 | 收藏可保留下架商品；收藏本身不带购买数量 | 从收藏加购按新请求数量处理 |

## 3. 对外接口与参数

本节是当前实现的接口契约，用来定位下文的需求验证入口。业务实现核对基准为主仓库提交 da42a7470f804bd267312b2d5a9b94b17e28c8c2，文档修订不改变业务源码。

### 3.1 公共约定

- B 接口前缀为 /api/b；本地默认服务 http://127.0.0.1:8002。
- POST 使用 Content-Type: application/json 和 X-Requested-With: ShopApp，正文为 JSON 对象。
- 若 POST 携带 Origin，它必须匹配服务端协议与 Host；不带 Origin 的合法调试请求可进入其他校验。
- B POST 正文须非空且不超过 3 MiB；移除操作也传 {}。
- 业务成功为 HTTP 200。写入成功通常仅返回 message，读取列表应另调 GET。
- 错误体为 {"error":"可读原因"}。缺字段、类型错误等为 400；未认证 401；越权推进或公共安全边界拒绝 403；商品/订单不存在 404；状态、库存或标识冲突 409；非 JSON 媒体类型 415；空正文或过大正文 413。
- 多处同时不合法时按实际校验顺序返回，不能要求某个业务错误优先于公共请求拦截。
- JSON 中 product_id、quantity 和 product_ids 元素按严格整数校验，不把字符串、布尔值或小数强转为 ID。移除接口的路径 ID 由 long 绑定；当前对不存在的数字 ID 幂等成功，未额外限定路径数值必须大于 0。

### 3.2 购物车和收藏接口

| 方法与路径 | 身份 | 参数位置及约束 | 成功返回 | 关联需求 |
|---|---|---|---|---|
| GET /api/b/cart | 客户 | 无业务参数 | items，含 quantity 和 product 当前信息 | B-CART |
| POST /api/b/cart | 客户 | JSON product_id 正整数，quantity 整数1～1000000，mode可选add/set；省略或空串为add | message=购物车已更新 | B-CART |
| POST /api/b/cart/{id}/remove | 客户 | 路径id为商品ID；JSON {} | message=已移除 | B-CART |
| GET /api/b/favorites | 客户 | 无业务参数 | items，含 product，不含购物车quantity | B-FAV |
| POST /api/b/favorites | 客户 | JSON product_id 正整数且存在 | message=已收藏 | B-FAV |
| POST /api/b/favorites/{id}/remove | 客户 | 路径id为商品ID；JSON {} | message=已移除 | B-FAV |
| POST /api/b/cart/favorite | 客户 | JSON product_ids，1～100个唯一正整数，均在本人购物车 | message=所选商品已移入收藏 | B-MOVE |

product 包含 id、name、price_cents、stock、reserved、available、status、images 等当前信息。购物车/收藏读取空数据时返回 items=[]。

### 3.3 订单和客户档案接口

| 方法与路径 | 身份 | 参数位置及约束 | 成功返回 | 关联需求 |
|---|---|---|---|---|
| POST /api/b/orders | 客户 | JSON product_ids：1～100个唯一正整数；request_key：去空白后8～80字符文本 | 建单时 created=true、order、rejected、message；全缺货时 created=false、rejected、message | B-ORDER |
| GET /api/b/orders | 客户 | 查询page、size，可省略；强制当前客户范围 | orders、total、page、page_size | B-QUERY |
| POST /api/b/orders/{id} | 客户 | 路径id为订单ID；JSON action=cancel | message、order | B-CANCEL |
| GET /api/b/seller/orders | 卖家 | 查询customer_id可选；page、size可省略 | orders、total、page、page_size | B-QUERY |
| GET /api/b/seller/customers | 卖家 | 无业务参数 | customers，含order_count、completed_total_cents | B-QUERY |
| POST /api/b/seller/orders/{id} | 卖家 | 路径id为订单ID；JSON action=confirm/ready/ship/complete/cancel | message、order | B-STATE、B-CANCEL |

page 默认1且范围1～1000000；size 默认20且范围1～100。订单按ID倒序。order 中包含 id、customer_id、username、status、total_cents、phone、trade_location、created_at、updated_at、items、events；返回时不暴露内部 request_key、selection 字段。该版本没有 GET /api/b/orders/{id} 单独详情路由，完整明细随列表或操作返回。

### 3.4 请求与关键响应示例

~~~json
{"product_id":101,"quantity":2,"mode":"add"}
~~~

~~~json
{"product_ids":[101,102],"request_key":"review-b-order-0001"}
~~~

成功且部分缺货时的响应结构节选：

~~~json
{
  "created":true,
  "order":{
    "id":21,
    "status":"placed",
    "total_cents":3980,
    "items":[{"product_id":101,"product_name":"陶杯","unit_price_cents":1990,"quantity":2,"subtotal_cents":3980}]
  },
  "rejected":[{"product_id":102,"name":"陶碗","reason":"缺货"}],
  "message":"下单成功，有货商品已合并为一笔订单"
}
~~~

## 4. 逐条需求与验证方式

### 4.1 验证准备与判定约定

准备独立测试数据库、卖家账号 S 和两个客户甲/乙，各自使用独立 Cookie。准备在售商品 P、Q、R，单价可用 1990 分、初始库存均为 5；“P”等符号在接口中替换为实际数字 ID。999999 仅表示已确认不存在且不在购物车的测试 ID，不能直接假定它永远不存在。

每条需求使用独立数据或先还原前置状态；涉及已确认、已备货、已发货的测试应先按正常步骤推进。HTTP 测试默认安全头、媒体类型、Cookie 均合法，只改变该条指定条件。金额核对接口整数分；库存通过商品查询或卖家库存页核对。

“甲加购、甲下单、卖家推进”等操作可在页面或 Postman/HTTP 客户端完成；边界类型、请求标识、并发及状态码需检查网络请求。表中预期结果是验收标准，不能用“页面能打开”代替数量、归属、金额或状态断言。

### 4.2 收藏商品（9 条）

| 编号 | 需求点 | 详细要求 | 简明验证：操作 → 预期结果 | 来源 |
|---|---|---|---|---|
| B-FAV-01 | 添加收藏 | 登录客户可把存在的商品加入本人收藏，不改变库存。 | 甲收藏 P 后查询应出现 P；stock、reserved 不变。 | 课B.2-1 |
| B-FAV-02 | 收藏列表 | 客户可查看本人的收藏，含商品当前名称、价格、状态、可用数量与图片。 | 收藏 P 后核对 product 的 name、price_cents、status、available、images。 | 课B.2-1/约 |
| B-FAV-03 | 收藏去重 | 同一客户重复收藏同款只保留一条，重复请求不报错。 | 连续收藏 P 两次均为 200，列表中 P 只有一条。 | 约 |
| B-FAV-04 | 客户隔离 | 甲的收藏不能被乙看到或移除。 | 甲乙均收藏 P；乙移除后，甲仍有 P、乙无 P。 | 约 |
| B-FAV-05 | 移除收藏 | 客户可移除本人指定收藏，不删除商品和购物车条目。 | 甲同时收藏并加购 P；移除收藏后购物车仍有 P，卖家仍能查询 P。 | 约 |
| B-FAV-06 | 重复移除 | 不存在或已移除的收藏按幂等成功处理。 | 对 P 连续移除两次，均为 200，收藏无 P。 | 约 |
| B-FAV-07 | 下架收藏 | 下架商品继续保留收藏；允许按已知 ID 收藏仍存在的下架商品。 | P 下架后按其已知 ID 收藏，返回 200，收藏中 status=off。 | 约 |
| B-FAV-08 | 无效商品 | 收藏编号须为正整数且商品存在。 | 分别传 0、字符串编号、未使用的正整数 ID；前两者 400，后者 404。 | 约 |
| B-FAV-09 | 空收藏 | 没有收藏时返回空列表，页面显示空提示。 | 清空甲收藏再查询，items=[]，页面正常。 | 约 |

### 4.3 购物车管理（12 条）

| 编号 | 需求点 | 详细要求 | 简明验证：操作 → 预期结果 | 来源 |
|---|---|---|---|---|
| B-CART-01 | 选择加购 | 登录客户可按商品编号及数量把未下架商品加入购物车。 | 在售 P 提交 quantity=2，查询应有 P 且数量为 2。 | 课B.2-2 |
| B-CART-02 | 同款合并 | 同一客户同款只有一行；默认及 add 模式累加数量。 | 空车中 P 先加 2 件再加 3 件，应仅一行且 quantity=5。 | 约 |
| B-CART-03 | 覆盖修改 | mode=set 将数量设置为请求值；原条目不存在时创建。 | 已有 P 五件 set 为 2，不存在的 Q set 为 3；查询分别为 2、3。 | 约 |
| B-CART-04 | 数量范围 | 单次数量及累加结果均须为 1～1000000，拒绝越界且不改原数据。 | 传 0、-1、1000001 均为 400；已有 1000000 再加 1 也为 400，原值不变。 | 约 |
| B-CART-05 | 数量类型 | quantity 必须为 JSON 整数，拒绝字符串、小数、布尔值、null 和缺失。 | 分别提交字符串“2”、1.5、true、null 及缺失 quantity，均为 400。 | 约 |
| B-CART-06 | 模式约束 | mode 支持 add、set；省略或去除首尾空白后为空时累加；其他值拒绝。 | 已有 P 一件，省略 mode 加 1 后为 2；mode=delete 应为 400。 | 约 |
| B-CART-07 | 移除条目 | 按商品 ID 移除本人购物车条目；重复移除仍成功。 | 甲移除 P 两次均为 200；乙的 P 不受影响。 | 约 |
| B-CART-08 | 读取购物车 | 展示每项数量及商品当前单价、实物库存、预留、可用量和状态。 | 加入 P 后核对 quantity、product.price_cents、stock、reserved、available、status。 | 约 |
| B-CART-09 | 客户隔离 | 购物车归属由会话决定，不接受请求体 customer_id 指定他人身份。 | 甲加购 P，乙查不到；乙带甲 customer_id 加购，只写乙的购物车。 | 约 |
| B-CART-10 | 不占库存 | 加购、改数量和移除不预留、不扣实物库存。 | 记录库存，依次加购、改数量、移除，前后 stock、reserved 相同。 | 约 |
| B-CART-11 | 下架与缺货边界 | 不存在商品加购为 404，下架为 409；未下架时允许先存高于可用量的数量。 | available=2 的在售 P 加购 3 件应成功；无预留时下架 P 后再加购为 409。 | 约 |
| B-CART-12 | 空购物车 | 无条目时返回 items=[] 并显示空提示。 | 清空后查询并打开购物车页，应无错误。 | 约 |

### 4.4 从购物车批量转收藏（9 条）

| 编号 | 需求点 | 详细要求 | 简明验证：操作 → 预期结果 | 来源 |
|---|---|---|---|---|
| B-MOVE-01 | 多选转收藏 | 客户可一次选择多种购物车商品，转入收藏并删除对应购物车条目。 | 车有 P、Q，提交 [P,Q]，收藏各一条且车中均无。 | 课B.2-4 |
| B-MOVE-02 | 选择范围 | 单次选择须为含 1～100 项的数组。 | []、101 个互异 ID 为 400；准备 100 项合法购物车商品后转移应成功。 | 约 |
| B-MOVE-03 | 编号类型 | 数组项必须为可转换为 long 的正整数。 | 分别用 0、-1、字符串编号、1.5、true 作为数组项，均为 400。 | 约 |
| B-MOVE-04 | 编号唯一 | 一次选择不能包含重复商品编号。 | 提交 [P,P] 为 400，购物车和收藏不变。 | 约 |
| B-MOVE-05 | 本人条目 | 所有选中商品必须在当前客户购物车中。 | 仅甲车有 P，乙提交 [P] 为 400，甲车不变。 | 约 |
| B-MOVE-06 | 收藏去重 | 已收藏的商品仍可转移，最终只保留一条收藏。 | 先收藏 P，再从车转收藏 P，应成功，收藏一条且车无 P。 | 约 |
| B-MOVE-07 | 保留未选项 | 转移只修改选择集，其他商品保留在购物车。 | 车有 P、Q、R，仅转 [P,Q]，R 仍在车。 | 约 |
| B-MOVE-08 | 整批回滚 | 任一选择不合法，已经处理的合法项也回滚，不允许半批成功。 | P 合法且未收藏，999999 不在车；提交 [P,999999] 为 400，P 仍在车且未新增收藏。 | 约 |
| B-MOVE-09 | 不保留数量 | 收藏只保存商品关系，不保存原购物车购买数量。 | P 五件转收藏后收藏无 quantity；从收藏页加购后原空车中 P 为 1 件。 | 约 |

### 4.5 购物车多商品下单（17 条）

| 编号 | 需求点 | 详细要求 | 简明验证：操作 → 预期结果 | 来源 |
|---|---|---|---|---|
| B-ORDER-01 | 勾选下单 | 客户可选择本人购物车的一种或多种商品下单，数量取购物车已保存值。 | P 两件、Q 一件，勾选二者下单，明细数量分别为 2、1。 | 课B.2-3 |
| B-ORDER-02 | 选择集约束 | 下单使用 1～100 个唯一正整数商品 ID，拒绝空选择、重复及非法类型。 | 提交 []、[P,P]、含字符串的数组，均为 400且不新增订单。 | 约 |
| B-ORDER-03 | 条目归属 | 任一选中商品不在本人车内时拒绝整次建单。 | [本人P,不在本人车的Q] 为 400，订单总数和 P 购物车条目不变。 | 约 |
| B-ORDER-04 | 一单多明细 | 一次成功提交只建一张主订单，所有成功商品在该单下分别形成明细。 | 提交 P、Q 返回一个 order.id，items 含二者，不拆为两个单号。 | 澄1 |
| B-ORDER-05 | 订单初态 | 新订单状态为 placed，记录客户及创建时间。 | 成功下单后 status=placed、customer_id 正确、created_at 非空。 | 课B.1-1/约 |
| B-ORDER-06 | 明细完整 | 明细保存商品 ID、名称、下单单价、数量及小计。 | 检查每项 product_id、product_name、unit_price_cents、quantity、subtotal_cents。 | 澄1 |
| B-ORDER-07 | 金额计算 | 金额使用整数分；总额等于成功明细数量乘单价之和。 | P、Q 单价均为 1990 分，数量 2、1，小计 3980、1990，总额 5970。 | 澄1/约 |
| B-ORDER-08 | 价格快照 | 以服务端事务内当前价格建快照，不信任前端伪造单价。 | 请求额外传 unit_price_cents=1，返回仍为服务器商品单价；检查明细快照。 | 约 |
| B-ORDER-09 | 联系快照 | 保存下单时客户电话和默认交易地点；后续改资料不覆盖历史。 | 下单后修改客户资料，旧单 phone、trade_location 仍为原值。 | 约 |
| B-ORDER-10 | 逐项缺货 | 逐项校验状态和可用量；下架或数量不足的整条商品排除，不自动减量。 | P 请求 3 但只余 2，Q 有货；订单仅含 Q，不将 P 改为 2 件。 | 澄2/约 |
| B-ORDER-11 | 部分成功反馈 | 部分缺货时有货商品照常建单，同时返回缺货 ID、名称和原因。 | P 缺货、Q 有货，应 created=true，rejected 含 P 的 product_id、name、reason。 | 澄2 |
| B-ORDER-12 | 保留缺货项 | 仅删除成功明细对应的购物车条目，缺货项原数量保留。 | 部分缺货下单后，P 仍在车且数量不变，成功的 Q 已移除。 | 澄2/约 |
| B-ORDER-13 | 保留未选项 | 未勾选商品不随下单清空。 | 车有 P、Q、R，仅对有货 P、Q 下单，R 仍在车。 | 约 |
| B-ORDER-14 | 全部缺货 | 全缺货返回 created=false、缺货列表和提示，不建空单、不清车。 | 提交前后订单总数相同；响应无 order，原车不变。 | 澄2/约 |
| B-ORDER-15 | 请求标识 | request_key 必须为去首尾空白后 8～80 字符的文本。 | 缺失、7 字符、81 字符、数字 key 均为 400，不建单。 | 约 |
| B-ORDER-16 | 成功重试幂等 | 同客户、同 key、同商品集合重放原订单及原缺货记录，不重复预留，顺序不同视为同一集合。 | 先 [P,Q] 后同 key [Q,P]，order.id 相同、订单数与 reserved 不增。 | 约 |
| B-ORDER-17 | 标识冲突与隔离 | 同客户同 key 换商品集合为 409；不同客户 key 相互独立。 | 甲用 K 下单 P 后改投 Q 为 409；乙用 K 为本人商品下单可成功。 | 约 |

### 4.6 订单五阶段与流转记录（11 条）

| 编号 | 需求点 | 详细要求 | 简明验证：操作 → 预期结果 | 来源 |
|---|---|---|---|---|
| B-STATE-01 | 五阶段 | 业务状态依次为 placed、confirmed、ready、shipped、completed。 | 创建订单并依次 confirm、ready、ship、complete，逐次核对五状态。 | 课B.1-1 |
| B-STATE-02 | 商家确认 | 仅 placed 订单允许卖家 confirm，变为 confirmed。 | 新单执行 confirm 为 200，再查 status=confirmed。 | 课B.1-1 |
| B-STATE-03 | 备货完成 | 仅 confirmed 允许卖家 ready，变为 ready。 | 先 confirm 再 ready 为 200，status=ready。 | 课B.1-1 |
| B-STATE-04 | 开始发货 | 仅 ready 允许卖家 ship，变为 shipped 并处理库存。 | 确认、备货后 ship 为 200，状态 shipped，库存按明细扣减。 | 课B.1-1/约 |
| B-STATE-05 | 交易完成 | 仅 shipped 允许卖家 complete，变为 completed，不再次扣库存。 | 发货后 complete 为 200，状态 completed，stock 不再变化。 | 课B.1-1/约 |
| B-STATE-06 | 禁止跳级 | 阶段必须顺序推进，不允许越过前置阶段。 | placed 直接 ship 为 409，状态、库存不变。 | 约 |
| B-STATE-07 | 禁止重复推进 | 同一操作不得重复记录事件或重复扣库存。 | shipped 再次 ship 为 409，stock 和事件数不变。 | 约 |
| B-STATE-08 | 客户不能推进 | 客户仅可取消自己订单，不能确认、备货、发货或完成。 | 客户对本人订单提交 action=ship 为 403，状态不变。 | 约 |
| B-STATE-09 | 未知操作 | 卖家提交未知 action 返回 400，不改订单。 | 卖家对存在订单传 action=finish 为 400，状态不变。 | 约 |
| B-STATE-10 | 终态锁定 | completed、cancelled 为终态，不能继续推进或再次取消。 | 卖家对两个终态分别执行 confirm、ship、cancel 均为 409，数据不变。 | 约 |
| B-STATE-11 | 事件留痕 | 建单及每次成功操作记录状态、操作者和时间，失败不追加事件。 | 正常单 events 为五状态顺序且 actor、created_at 非空；一次失败跳级不增事件。 | 约 |

### 4.7 双方取消权限与结果（9 条）

| 编号 | 需求点 | 详细要求 | 简明验证：操作 → 预期结果 | 来源 |
|---|---|---|---|---|
| B-CANCEL-01 | 客户取消新单 | 客户可取消本人 placed 订单。 | 新单由客户 cancel 为 200，status=cancelled。 | 课B.1-2 |
| B-CANCEL-02 | 客户取消确认单 | 客户可取消本人 confirmed 订单。 | 卖家 confirm 后客户 cancel 成功。 | 课B.1-2 |
| B-CANCEL-03 | 客户取消备货单 | 客户可取消本人 ready 订单。 | 卖家 ready 后、ship 前客户 cancel 成功。 | 课B.1-2 |
| B-CANCEL-04 | 客户发货后禁取消 | 客户不能取消 shipped、completed 订单。 | 分别对两状态客户 cancel 为 409，状态与库存不变。 | 课B.1-2 |
| B-CANCEL-05 | 商家发货前取消 | 卖家可取消 placed、confirmed、ready 订单。 | 准备三个对应状态的订单，卖家各 cancel 均成功。 | 课B.1-3 |
| B-CANCEL-06 | 商家发货后取消 | 卖家可取消 shipped 订单，并按本版约定恢复已扣数量。 | stock=5，下单发货 2 后为 3；卖家取消后恢复为 5。 | 课B.1-3/约 |
| B-CANCEL-07 | 商家完成后禁取消 | 卖家不能取消 completed 订单。 | completed 执行 cancel 为 409，状态、库存不变。 | 课B.1-3 |
| B-CANCEL-08 | 整单取消 | 多明细共用一个取消状态，不提供逐商品取消。 | 两明细订单一次 cancel 后主单 cancelled，两商品预留都释放。 | 约 |
| B-CANCEL-09 | 防重复恢复 | 已取消订单再次取消拒绝，库存仅恢复或释放一次。 | 取消后再次 cancel 为 409，stock、reserved 与事件数不再变化。 | 约 |

### 4.8 共享库存与一致性（9 条）

| 编号 | 需求点 | 详细要求 | 简明验证：操作 → 预期结果 | 来源 |
|---|---|---|---|---|
| B-STOCK-01 | 可用库存口径 | available=stock-reserved，reserved 包含 A 冻结数及 B 发货前订单明细数量。 | stock=5，A冻结1、B预留2，应 reserved=3、available=2。 | 约 |
| B-STOCK-02 | 建单预留 | B 建单成功立即预留成功明细数量，不改实物库存。 | stock=5 下单 2 后 stock=5、reserved=2、available=3。 | 约 |
| B-STOCK-03 | 确认备货维持预留 | confirmed、ready 保持原预留，不额外累加。 | 上述单 confirm、ready 后均为 stock=5、reserved=2。 | 约 |
| B-STOCK-04 | 发货扣减 | 发货按明细扣实物库存，同时该单不再计入预留。 | 上述单 ship 后 stock=3、reserved=0、available=3。 | 约 |
| B-STOCK-05 | 发货前取消释放 | 发货前取消只释放预留，实物库存不加不减。 | stock=5 下单 2 后取消，应 stock=5、reserved=0、available=5。 | 约 |
| B-STOCK-06 | 售罄及取消恢复 | 发货扣至零自动下架；本版卖家取消已发货单后恢复数量并上架。 | 最后1件发货后公开详情404；卖家取消后 stock=1，公开详情恢复。 | 继A/约 |
| B-STOCK-07 | 并发最后一件 | 不同客户并发购买最后一件时至多一笔成功，不能超卖。 | 两个会话各加购1并并发结算，仅一个 created=true，reserved=1。 | 约 |
| B-STOCK-08 | A与B共享 | A 冻结限制 B 下单，B 预留也限制 A 冻结，不能分别占用同一件。 | stock=1先A冻结后B下单应缺货；换新商品先B下单后A冻结应拒绝。 | 约 |
| B-STOCK-09 | 库存调整约束 | 卖家不能调低库存至预留以下，存在预留时也不能手动下架。 | reserved=2时 stock 改1或 listed=false，均为409且数据不变。 | 继A/约 |

### 4.9 查询、客户档案与权限（9 条）

| 编号 | 需求点 | 详细要求 | 简明验证：操作 → 预期结果 | 来源 |
|---|---|---|---|---|
| B-QUERY-01 | 客户订单列表 | 登录客户可分页查看本人 B 订单、状态、明细、金额、联系快照和事件。 | 甲下单后 GET /api/b/orders，核对 orders、items、events 和联系快照。 | 继A/约 |
| B-QUERY-02 | 客户查询隔离 | 客户列表不含其他客户，附带 customer_id 也不能改变归属。 | 甲乙各下单；乙附甲 customer_id 查询，仍只返回乙的订单。 | 约 |
| B-QUERY-03 | 卖家全量订单 | 卖家可分页查看所有客户 B 订单。 | 甲乙各下一单，卖家列表能找到二者。 | 继A/约 |
| B-QUERY-04 | 客户筛选 | 卖家支持按 customer_id 筛选，total 与列表使用相同条件。 | 指定甲 ID，所有返回订单归属甲，total 等于甲订单总数。 | 继A/约 |
| B-QUERY-05 | 分页参数 | page 默认1，范围1～1000000；size默认20，范围1～100；按订单ID倒序。 | 3单 size=2，第一页2条、次页1条；page=0或size=101为400。 | 约 |
| B-QUERY-06 | 分页响应 | 响应含 orders、total、page、page_size；超出末页返回空列表而非错误。 | 请求合法但大于末页的 page，orders=[]且total仍为总数。 | 约 |
| B-QUERY-07 | 客户统计 | 卖家可查询注册资料、订单数、完成金额；金额只统计 completed。 | 客户有 completed=5970分及取消单，completed_total_cents仅为5970，order_count包含所有状态订单。 | 继A/约 |
| B-QUERY-08 | 卖家身份隔离 | 只有客户会话时不能访问卖家订单和客户档案。 | 无卖家Cookie、有客户Cookie时请求 seller/orders 和 seller/customers，均401。 | 约 |
| B-QUERY-09 | 操作订单归属 | 客户操作他人或不存在订单均为404，不能改变他人状态。 | 乙取消甲订单为404；甲重查状态未变。 | 约 |

### 4.10 页面交互与可见结果（8 条）

| 编号 | 需求点 | 详细要求 | 简明验证：操作 → 预期结果 | 来源 |
|---|---|---|---|---|
| B-UI-01 | 详情预选入口 | 商品详情提供数量加购、收藏及结果反馈。 | 登录后在 /challenge-b#product/P 加购、收藏，提示成功且列表有P。 | 课B.2-1/课B.2-2 |
| B-UI-02 | 购物车勾选 | 提供逐项勾选和全选，批量操作使用当前选择集。 | 车有P、Q，先只勾P再全选，检查请求分别含1个和2个ID。 | 课B.2-3/课B.2-4 |
| B-UI-03 | 数量与移除 | 购物车可保存数量并移除条目，刷新后结果与服务端一致。 | 页面P改3保存，刷新仍为3；移除后刷新无P。 | 约 |
| B-UI-04 | 收藏加入购物车 | 收藏页可按1件加购，原收藏继续保留。 | 从收藏P点击加购，原空车出现P一件，收藏仍有P。 | 约 |
| B-UI-05 | 下单前确认 | 提交前说明按当前价格和整项缺货剔除规则，确认后才发送建单请求。 | 点击提交后取消确认框，不发送POST /api/b/orders且订单数不变。 | 约 |
| B-UI-06 | 成功与缺货反馈 | 显示成功单号、总金额、缺货商品名称原因，并提供订单清单入口。 | 部分缺货结算同时出现单号和缺货提示，入口可找到该单明细。 | 澄1/澄2/约 |
| B-UI-07 | 订单与按钮 | 订单页显示中文状态、各明细、小计、总额及事件，操作按钮匹配角色阶段。 | 客户shipped无取消按钮，卖家ready有发货按钮；金额与接口一致。 | 课B.1/约 |
| B-UI-08 | 登录与空态 | 客户未登录访问购物车、收藏、订单时提示登录；空数据有明确提示。 | 退出后访问三页提示登录；空车、空收藏和无订单页面无脚本异常。 | 约 |

### 4.11 输入边界、持久化与可靠性（12 条）

| 编号 | 需求点 | 详细要求 | 简明验证：操作 → 预期结果 | 来源 |
|---|---|---|---|---|
| B-NFR-01 | 客户会话 | 客户侧业务必须验证有效会话，其他条件合法时未认证为401。 | 不带Cookie发送合法购物车GET和合法收藏POST，均401且不写数据。 | 约 |
| B-NFR-02 | 写请求安全头 | 所有B POST必须携带 X-Requested-With: ShopApp。 | 合法Cookie和JSON但去掉此头为403，补回后进入业务处理。 | 约 |
| B-NFR-03 | 来源检查 | 若POST带Origin则必须等于服务端协议与Host，拒绝外站Origin。 | Origin=https://example.invalid为403，实际同源Origin可正常调用。 | 约 |
| B-NFR-04 | 媒体类型 | POST必须使用application/json，可带charset。 | 同一合法JSON用text/plain为415；application/json;charset=UTF-8正常。 | 约 |
| B-NFR-05 | JSON对象 | 正文必须为合法JSON对象；移除接口也须提交{}。 | 移除接口分别发{}、[]、损坏JSON，应为200、400、400。 | 约 |
| B-NFR-06 | 正文长度 | B POST正文非空且不超过3 MiB，空正文或超限拒绝。 | 其他头合法，发0字节或3 MiB+1字节正文均为413。 | 约 |
| B-NFR-07 | 统一错误 | 业务和请求边界错误返回对应状态码及JSON error字段，页面可显示错误文字。 | quantity=0为400且含error；页面对应请求失败后显示提示。 | 约 |
| B-NFR-08 | 持久化 | 购物车、收藏、订单、明细、事件持久化到SQLite，重启后保留，预留可重算。 | 使用同一数据库重启、重新登录，查询上述数据和reserved与重启前相同。 | 约 |
| B-NFR-09 | 事务回滚 | 批量转移、建单、状态处理失败不能残留部分更新。 | 转移或下单将非法项放末尾；另在测试库制造第二明细库存不足后发货，失败后各项库存、订单状态与操作前一致。 | 约 |
| B-NFR-10 | 唯一与关联约束 | 车与收藏按客户+商品唯一，订单按客户+request_key唯一，表间保持外键关联。 | 检查BSchema的主键、唯一键、外键；重复加购收藏及成功重放后无重复行。 | 约 |
| B-NFR-11 | 兼容A | B用独立订单表并共享商品客户，原A及基线业务继续可用。 | 集成后执行原A专项和基线回归，检查日志；核对A/B共享库存。 | 继A/约 |
| B-NFR-12 | 失败重试 | 页面下单遇网络错误后，选择与数量未变时沿用request_key重试，防重复建单。 | 使首次成功下单响应丢失，再点击提交；检查同key、同单号，无重复预留。 | 约 |

## 5. 验证执行与证据映射

### 5.1 历史专项与本版需求的关系

| 历史分组 | 主要场景 | 本版对应需求范围 |
|---|---|---|
| B01 | 登录、购物车数量合并修改、隔离、非法数量 | B-CART、B-NFR-01 |
| B02 | 收藏去重、批量转移及移除 | B-FAV、B-MOVE |
| B03 | 一单多商品、金额、预留、同key重试及冲突 | B-ORDER、B-STOCK-02 |
| B04 | 部分缺货、全缺货、重复选择 | B-ORDER-02、10～14 |
| B05 | 五阶段、跳级、重复操作、归属和取消截止 | B-STATE、B-CANCEL |
| B06 | 发货前取消释放、卖家发货后取消恢复 | B-CANCEL、B-STOCK |
| B07 | 并发最后一件、调低库存受限 | B-STOCK-07、09 |
| B08 | A预留使B缺货 | B-STOCK-08的一个方向 |
| B09 | 重启数据及联系快照保留 | B-ORDER-09、B-NFR-08 |
| B10 | 客户统计、筛选和卖家认证 | B-QUERY |
| B11 | 三个发货前阶段取消、批量回滚、售罄及补回 | B-CANCEL、B-MOVE-08、B-STOCK-06 |
| B12 | 浏览器登录、加购、结算、订单与卖家管理 | B-UI 的主流程 |

映射表示历史脚本包含相关场景，不表示整组下所有新编号都已覆盖。新增边界如100项选择上限、全部非法类型、分页上下限、缺失安全头、外站Origin、正文超限、A/B反向预留、响应丢失重试等，需按第4节补做逐条验证。

- [历史挑战 B 验证脚本](https://github.com/LittleChenLiya/online-shopping-mvp/blob/main/06_系统测试/payload/tests/challenge-b-acceptance.mjs)
- [历史挑战 B 输出](https://github.com/LittleChenLiya/online-shopping-mvp/blob/main/docs/review-04/庄云月第四次汇报成果物/验证证据/challenge-b-results.txt)
- [历史截图与证据目录](https://github.com/LittleChenLiya/online-shopping-mvp/tree/main/docs/review-04/庄云月第四次汇报成果物/验证证据)

测试脚本当前归档于 06_系统测试/payload/tests，运行它之前应在仓库根目录完成 Maven 构建，使 backend-java/target/online-shopping.jar 存在，并安装项目要求的 Node、Playwright 和 Edge。默认运行会按脚本创建临时数据库与服务，退出时清理其临时目录；不应把其默认输出目录当成已经在当前仓库存在的证据。

历史日志记录的是旧集成版本的12组检查。本次只重整文档、核对源码和文档一致性，没有重新执行全部105条需求验收，不将旧日志标为本次执行结果。

### 5.2 简明评审演示清单

| 演示 | 准备与操作 | 必须看到的结果 |
|---|---|---|
| 购物车合并 | P先加2再加1，用set改5 | 一条P，数量依次2、3、5 |
| 收藏与批量转移 | P已收藏，车有P、Q、R，转P、Q | 收藏去重，车只剩R |
| 异常回滚 | R合法且未收藏，另一ID不在车，整批转收藏 | 400，R仍在车且未新增收藏 |
| 多商品与总金额 | P两件、Q一件，单价1990分 | 一个单号、两明细、总额5970分 |
| 部分缺货 | P需3仅余2，Q有货 | 单中仅Q，P出现在rejected并留车 |
| 全缺货 | 所选都缺货 | created=false，无新单，车不清空 |
| 正常五阶段 | 下单→confirm→ready→ship→complete | 状态和事件依次变化，仅发货扣库存 |
| 双方取消 | 客户分别在ready、shipped取消；卖家在shipped、completed取消 | 依次成功、409、成功恢复、409 |
| 身份与并发 | 乙取消甲订单；两客户抢最后一件 | 404；最多一单成功 |
| 重试与重启 | 成功单同key重放；同数据库重启再查 | 原单号、无重复预留；历史与库存保留 |

### 5.3 验收记录格式

按需求编号记录，每条至少留“操作/请求、实际结果、证据、结论”；失败或未测不能填通过。

| 需求编号 | 测试数据与请求/页面步骤 | 实际状态码与数据 | 证据链接 | 结论与执行时间 |
|---|---|---|---|---|
| B-CART-02 | 示例：同款先加2后加3 | 待执行 | 待补 | 待执行 |
| B-MOVE-08 | 示例：有效项在前，非法项在后 | 待执行 | 待补 | 待执行 |

## 6. 原版本编号兼容与实现追踪

| 原编号 | 原主题 | 本版细化范围 |
|---|---|---|
| B-R01 | 收藏商品 | B-FAV-01～09 |
| B-R02 | 加入购物车 | B-CART-01～12 |
| B-R03 | 批量转收藏 | B-MOVE-01～09 |
| B-R04 | 勾选下单 | B-ORDER-01～09、15～17 |
| B-R05 | 缺货处理 | B-ORDER-10～14 |
| B-R06 | 订单清单与快照 | B-ORDER-06～09、B-QUERY-01 |
| B-R07 | 订单流转 | B-STATE-01～11 |
| B-R08 | 客户取消 | B-CANCEL-01～04、08～09 |
| B-R09 | 商家取消 | B-CANCEL-05～09 |
| B-R10 | 可靠性 | B-STOCK、B-QUERY、B-NFR |

页面行为另由 B-UI-01～08 明确。新编号供逐项验收，旧编号保留作历史引用，不再只用“B-R10通过”覆盖所有安全、并发和持久化细节。

| 实现部件 | 责任及接口支撑 |
|---|---|
| BCollection | 7个购物车与收藏接口业务，选择集和数量校验 |
| BOrders | 多商品建单、快照、幂等、状态、取消、查询及客户统计 |
| BController | /api/b HTTP路由、Cookie及参数适配 |
| Catalog | A/B共享可用库存、商品状态与当前信息 |
| BSchema、Database | 6张B表、约束、索引、持久化与写事务 |
| RequestGuard、Input、ApiErrors | 请求边界、严格字段校验和错误响应 |
| challenge-b.js | 预选、批量操作、下单确认、结果和订单页面 |

- [BController 接口实现](https://github.com/LittleChenLiya/online-shopping-mvp/blob/main/backend-java/src/main/java/cn/course/shopping/challenge/BController.java)
- [BCollection 预选业务](https://github.com/LittleChenLiya/online-shopping-mvp/blob/main/backend-java/src/main/java/cn/course/shopping/challenge/BCollection.java)
- [BOrders 订单业务](https://github.com/LittleChenLiya/online-shopping-mvp/blob/main/backend-java/src/main/java/cn/course/shopping/challenge/BOrders.java)
- [第四次成果总览](https://github.com/LittleChenLiya/online-shopping-mvp/wiki/第4次评审成果总览)
