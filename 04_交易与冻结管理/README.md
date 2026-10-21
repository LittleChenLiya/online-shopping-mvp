# 黄扬轩：Java交易接口

负责人：黄扬轩 / aixuan1206。源码在backend-java/src/main/java/cn/course/shopping/trade/，旧trades.py归档legacy/python-stage1/。

TradeController负责GET /api/trades和POST /api/trade，两者均需卖家会话。TradeService.list查询当前商品、全部意向及交易事件；TradeService.update执行状态转换。

| action | 前置条件 | 商品/意向结果 |
|---|---|---|
| freeze | active，intent_id属于本商品且pending | frozen，选中意向selected，保存selected_intent_id |
| failure | frozen且已选意向 | active，清空所选编号，所选意向pending |
| success | frozen且已选意向 | sold，记录成交时间，选中意向completed，其余closed |

每次合法操作插入trade_events，商品/意向/事件在同一SQLite写事务内更新。禁止在售直接成交、重复冻结、已售恢复、跨商品选择意向。未知action为400，缺失商品404，状态冲突409。

与前端使用统一freeze/success/failure名称。庄云月负责concurrentFreezeSelectsOneBuyer、conflictingTradeResultsAreConsistent、rejectedOperationDoesNotChangeState和完整流程回归。
