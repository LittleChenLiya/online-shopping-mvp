# 第一阶段兼容性样本

legacy-stage1.sqlite是合成测试数据，不是正式数据库。由仓库提交6975140的原始database.py和security.py（现归档legacy/python-stage1）通过Python 3.13.0标准库SQLite/PBKDF2生成。

样本：商品41“旧版手作杯”、价格1990分、frozen、selected_intent_id=81；意向81为selected、82为pending；一条freeze事件。固定卖家测试密码为“卖家🔒密码”，保存原Python实现生成的随机盐哈希。

LegacyDatabaseCompatibilityTest仅复制样本到JUnit临时目录后操作，验证Java初始化不会覆盖旧密码、可登录、读取旧中文数据及冻结状态、继续成交并保持多表一致。运行该Java测试不需要Python。不要把样本当作正式数据使用。
