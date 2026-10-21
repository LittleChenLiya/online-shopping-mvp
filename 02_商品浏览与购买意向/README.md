# 余昕越：Java买家接口

负责人：余昕越 / ygccbpkn12345。当前源码在backend-java/src/main/java/cn/course/shopping/buyer/，旧buyer.py已归档legacy/python-stage1/。

| 类/方法 | 职责 |
|---|---|
| BuyerController.current | GET /api/product；无需登录 |
| BuyerController.submit | POST /api/intents；无需登录 |
| BuyerService.current | 查询active/frozen唯一商品，使用公开字段白名单 |
| BuyerService.submit | 校验编号/姓名/电话/备注，在写事务里确认商品active后保存意向 |

购买意向仅表示线下联系意愿，不代表付款，不自动冻结。买家不建立账号；姓名电话与意向记录关联。公开接口不返回买家信息或selected_intent_id。

电话去空格、括号和横线后，允许单个前导加号及6～20位数字。同商品规范化电话唯一。重复、冻结或已售状态返回409；字段格式错误400。

与曾大君的Database共享事务工具；与黄扬轩交易模块共用商品/意向状态；谢相逸按接口契约调用。SystemAcceptanceTest中的validationAndDuplicateIntent、concurrentDuplicateIntent等用例由庄云月维护。
