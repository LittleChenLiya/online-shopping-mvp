# 庄云月：Java系统测试与跨层验收

负责人：庄云月 / Ricee1129。维护backend-java/src/test/java/cn/course/shopping/、本目录和测试执行脚本。

原test_acceptance.py/run_tests.py及历史results已归档legacy/python-stage1/06_系统测试/。当前测试是Java JUnit，不调用Python服务；历史通过/失败记录不作为Java结论。

## 执行

在根目录双击本目录“运行Java测试.bat”，或：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-jar.ps1
```

跨平台可运行mvn clean verify。第一条通过随机端口真实HTTP、隔离临时SQLite执行JUnit，生成results-java的XML/JSON/报告/源码指纹；第二条运行实际打包JAR、重启并核对数据和修改后密码的持久化。

SystemAcceptanceTest覆盖F01～F10、权限、输入、请求防护、并发、状态一致性与静态资源限制；PasswordCompatibilityTest用标准及独立实现向量验证旧密码格式和Unicode。

结果只代表当前本机源码及执行环境。自动测试不代替成员课堂演示；浏览器验收记录、未执行的Docker/CI边界见docs/java-migration/迁移说明.md。

## 与开发成员协作

接口冻结前补充正常/异常场景；接口变更后更新测试；缺陷记录须包含请求、预期、实际响应和复现步骤。本人实际运行、Codex辅助运行与CI自动执行应分别记录，不把辅助操作记为成员本人执行。

[测试成果与模型协作](测试成果与模型协作.md)：20项Java用例、浏览器及JAR检查、挑战A专项用例、复现步骤和模型辅助测试说明。
