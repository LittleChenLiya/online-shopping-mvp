# 谢相逸：前端与接口对接

负责人：谢相逸 / LittleChenLiya。当前前端仍为HTML/CSS/JavaScript，由Java服务同源提供，无需安装Node或Python运行。

| 文件 | 职责 |
|---|---|
| index.html | 页面入口；依次加载api.js、app.js |
| style.css | 页面、表单、交易确认框与响应式样式 |
| api.js | 统一fetch封装、JSON请求头、安全标识、同源Cookie和错误处理 |
| app.js | #shop/#buy/#products/#trades/#account的页面渲染与交互 |

前后端以docs/java-migration/API契约.md协作。价格输入是元字符串；响应price_cents为整数分。页面只提交操作意图，商品状态和权限以Java后端为准。用户文本通过esc转义，避免作为HTML执行。

从仓库根目录启动Java应用后访问http://127.0.0.1:8000。Maven打包时把这四个资源复制进JAR，不需要手动复制第二份。修改前端后重新构建。

自查：买家无需登录；卖家未登录跳转登录表单；发布图片预览；冻结确认可取消；快速切页不显示过期请求；窄屏无横向溢出；完整交易与改密流程正常。Java接口自动验收由庄云月负责，前端负责人共同复核浏览器结果。
