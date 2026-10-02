# 工作台读接口配置与验证

页面和数据安全边界见 [Engineering Workbench 契约](workbench-engineering-ux-contract.md)。这些接口只读取既有执行/评估数据，不增加新的执行或报告存储。

## Trace 配置

Task 的 `JAEGER_QUERY_URL` 为 Jaeger 内部查询根地址，默认空（返回 `NOT_CONFIGURED`）。本机示例为 `http://127.0.0.1:16686`。地址不含凭证、query 或 fragment，不能来自用户请求；内部端口仍需部署侧访问控制。适配器不转发用户 Authorization，不跟随重定向，不返回原始 Jaeger JSON。

读取限制为 2 秒连接、5 秒响应、2 MiB 响应、10000 原始 Span、2000 展示节点。超过展示上限时 `truncated=true`，统计仍包含本 Task 可见的全量节点；累计服务耗时可能重叠，不是独占耗时。时间与耗时统一为微秒。

`GET /api/task/tasks/{id}/trace-view` 先经 task:read 授权，只查询 Task 持久化 Trace。`spans` 保留当前 Task 分支及必要共享祖先，动态名称转为稳定名称；属性使用 key/valueType/value 的白名单标量列表，不含原始日志、SQL、URL、Prompt、正文或凭证。缺失/过期/后端故障返回可用性码，未知统计为空，不改变 Task 状态。

返回不包含可点击的 Jaeger URL。前端深链仍需单独显式启用、Task 权限和运维确认等价租户隔离，不能因此读接口可用而自动开放。

Jaeger 的 `int64` 属性可为 JSON 整数或规范十进制字符串；白名单整数属性统一输出 `valueType=LONG` 和十进制字符串 `value`，避免长 ID 精度丢失。浮点数、越界值和非规范字符串不投影。

## 报告只读巡检

执行 `backend/evaluation-service/scripts/Inspect-ExperimentReports.ps1`，连接参数支持环境变量 DB_URL/DB_USERNAME，凭证只经 DB_PASSWORD 或 MYSQL_PWD 提供。脚本在只读事务中返回 schema/JSON/hash 异常计数，不导出报告或修复数据，不是线上 HTTP 接口。

显式设置 `VERIFY_STORED_REPORTS=true` 可运行 `ExperimentReportStoredCompatibilityTest` 对真实 v1 存量做只读字段/hash 校验。空库不会被当作历史兼容通过；普通测试默认跳过该数据库检查。

报告降级只保留 revision/schema/hash/审计身份和兼容码，正文及 selectedRecordIds 为空；完整性异常一小时去重告警。普通查询、降级或告警后端故障均不得重算、覆盖历史报告或修改生产 Agent。
