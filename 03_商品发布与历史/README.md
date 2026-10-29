# 王暾：Java商品接口与组长协调

负责人：王暾 / wangtun773。源码在backend-java/src/main/java/cn/course/shopping/product/，旧products.py归档legacy/python-stage1/。

| 类/方法 | 职责 |
|---|---|
| ProductController.publish/history | POST/GET /api/products；均需卖家Cookie |
| ProductService.publish | 事务内检查会话、校验输入、确保无未售商品后发布 |
| ProductService.history | 按id降序返回全部商品及状态/成交时间 |
| ProductService.price | BigDecimal精确转整数分，拒绝浮点输入和非法金额 |
| ProductService.image | 校验PNG/JPEG/WebP Data URL、Base64、文件头及2MB上限 |

19.90元保存为1990分，避免二进制浮点误差。在售active和冻结frozen均占唯一名额，只有sold后才能发布下一件。应用写事务BEGIN IMMEDIATE与数据库唯一索引共同防止并发重复发布；失败整体回滚。

商品已售时不删除记录，以便F05历史查询。图片文件头校验不等于完整图像解码。

组长协调接口冻结、审查与真实进度核算，不代替其他成员实际提交。验收关注SystemAcceptanceTest的concurrentPublish、limitsDoNotCreateProduct、fullWorkflowAndHistory等。

## 个人成果展示

[王暾：接口清单、模型协作过程与页面图片](../docs/personal-results/王暾-商品接口与模型协作.md)。包含基线2个接口、挑战A卖家商品与分类6个接口，以及职责和库存协作边界。

