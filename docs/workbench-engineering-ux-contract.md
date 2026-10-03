# Engineering Workbench 页面与查询契约

> 适用版本：v0.2.0；2026-09-29 冻结设计契约。
> 本文定义待实现的页面与接口边界，不表示相关功能已经全部实现或发布。

执行、隔离、遥测与账本、快照、离线实验、终态恢复分别遵循 [ADR-0001～ADR-0006](adr/README.md)。工作台读模型复用这些业务身份与规则，不新增独立 Run 表、报告存储或前端状态机。

## 1. 页面、权限与设计

沿用现有 Vue 3/Element Plus 布局与样式，参考[目标页面图集](ui-mockups/v0.2.0/README.md)。“洞察”分组新增一个“评估与实验”入口，以页内导航切换资源。

| 页面 | 路由 | 读取权限 |
| --- | --- | --- |
| Evaluator 列表/详情 | `/spaces/:spaceId/evaluation/evaluators[/:evaluatorId]` | `evaluation:read` |
| TestCase 列表/详情 | `/spaces/:spaceId/evaluation/test-cases[/:testCaseId]` | `evaluation:read` |
| Dataset 列表/详情 | `/spaces/:spaceId/evaluation/datasets[/:datasetId]` | `evaluation:read` |
| EvaluationRun 列表/详情 | `/spaces/:spaceId/evaluation/runs[/:runId]` | `evaluation:read` |
| Experiment 列表/详情 | `/spaces/:spaceId/evaluation/experiments[/:experimentId]` | `evaluation:read` |

方括号表示可选的详情段，不是实际 URL 字符。Task 使用原路由，详情展示 root/parent、lineage/mode、执行快照 schema/hash、Trace 与隔离产物。关联 Task/Agent/Document 跳转仍需对应资源 read 权限，不能从评估权限推导授权。

- 管理资源、版本/绑定、报告重算与结论：`evaluation:manage`。
- 创建/恢复/取消评估，重试，提交 Feedback，启动/取消实验：`evaluation:run`。
- 创建 Prompt candidate：`evaluation:manage + agent:manage`。
- Task 恢复诊断/人工恢复：按 ADR-0006 的 `task:read/task:terminate` 与必要的 `document:edit`。

各权限正交。无 read 权限时菜单不显示、深链拒绝，服务端仍逐入口校验。Experiment 首版不新增名称字段，以 ID、DatasetVersion 和 variantKey 识别。配置编辑采用 JSON 编辑区，前端只做语法校验，后端裁决领域契约。

`GET /api/task/tasks/{id}/replay-eligibility` 是只读准入查询，按来源 Task 解析 Space 并要求 `task:read`；核验冻结文档版本沿用 Document 服务的 `document:read` 校验，缺少该权限时返回 403，不扩大文档访问权限。查询不创建 Task、签发 Capability 或发送消息。创建 Replay 仍要求 `task:read + task:create + evaluation:run`。从任务详情沉淀测试用例需要当前 Space 的 `task:read + document:read + evaluation:read + evaluation:manage`、`COMPLETED + LIVE` 来源及动态准入通过，不从 Catalog 管理权限推导执行权限。

## 2. 归档准入矩阵

下表以既有业务校验为基线；主资源 active 表示 `archived=false`。历史读取均需要正常权限，不能以归档为由重写或删除历史结果。

| 主资源/版本状态 | 新建另一版本 | 新绑定该版本 | 新 Run/Experiment 使用 | 历史读取 |
| --- | --- | --- | --- | --- |
| Dataset active / DRAFT | 允许 | 不适用 | 拒绝 | 允许 |
| Dataset active / PUBLISHED | 允许 | 不适用 | 允许，启用用例仍需准入 | 允许 |
| Dataset active / ARCHIVED | 允许 | 不适用 | 拒绝 | 允许 |
| Dataset archived / 任意 | 拒绝 | 不适用 | 拒绝 | 允许 |
| TestCase active / DRAFT | 允许 | 拒绝 | 拒绝 | 允许 |
| TestCase active / PUBLISHED | 允许 | 允许 | 允许，来源仍需 ReplayEligibility | 允许 |
| TestCase active / ARCHIVED | 允许 | 拒绝 | 拒绝 | 允许 |
| TestCase archived / 任意 | 拒绝 | 拒绝 | 拒绝 | 允许 |
| Evaluator active / DRAFT | 允许 | 拒绝 | 不可执行 | 允许 |
| Evaluator active / PUBLISHED | 允许 | 允许 | 通过 TestCaseVersion 绑定执行 | 允许 |
| Evaluator active / ARCHIVED | 允许 | 拒绝 | 新评估拒绝 | 允许 |
| Evaluator archived / PUBLISHED | 拒绝 | 拒绝 | 已发布 TestCaseVersion 的既有绑定可以继续使用 | 允许 |
| Evaluator archived / DRAFT 或 ARCHIVED | 拒绝 | 拒绝 | 拒绝 | 允许 |

绑定目标必须同 Space、PUBLISHED 且父资源未归档；发布时重新检查目标与父资源状态。绑定的全量替换仅用于 DRAFT，PUBLISHED/ARCHIVED 内容只读；PUBLISHED 可归档。主资源归档不自动取消既有 Run，其恢复、重试与未执行评价继续按入口动态校验。

Dataset/TestCase 主资源归档后，既有 PUBLISHED 版本不能用于**新运行**；这与历史运行可读是两个条件。Evaluator 主资源归档禁止新绑定/新发布引用，但不撤销已发布 TestCaseVersion 的既有 PUBLISHED EvaluatorVersion 绑定。归档 EvaluatorVersion 则使该版本不再满足新评价准入。

## 3. 最小查询接口

所有列表沿用 `PageParam`：`pageNum=1`、`pageSize=10`、上限 100，返回 `PageVO(records,total,pageNum,pageSize)`。Service 同步接收 SearchParam，默认按创建时间、ID 倒序，不在列表展开完整报告或证据。

| 接口 | 查询条件/返回 | 权限 |
| --- | --- | --- |
| `POST /api/evaluation/runs/search` | 必填 `spaceId`；可选 status、datasetVersionId、singleTestCaseVersionId、experimentVariantId、createdFrom/To、startedFrom/To | `evaluation:read` |
| `POST /api/evaluation/experiments/search` | 必填 `spaceId`；可选 status、datasetVersionId、createdFrom/To、startedFrom/To | `evaluation:read` |
| `GET /api/evaluation/dataset-versions/{id}/cases` | 绑定 ID、目标 Case/版本 ID、名称、版本号/状态、顺序、enabled | 从版本解析 Space 后校验 `evaluation:read` |
| `GET /api/evaluation/test-case-versions/{id}/evaluators` | 绑定 ID、Evaluator/版本 ID、名称、版本号/状态、evaluatorKey、顺序及绑定级 expectedJson | 同上 |
| `POST /api/evaluation/case-runs/{id}/attempts/search` | PageParam；Attempt 按 attemptNo/ID 倒序，批量附带本页 Result 摘要 | CaseRun → Run → Space 归属一致后校验 `evaluation:read` |
| `GET /api/evaluation/task-links/{taskId}?spaceId=...` | 通过 executionTaskId 查询唯一 Attempt，沿 CaseRun、Run、TestCaseVersion、Variant、Experiment 的正式关系返回身份；未绑定返回 null | 所声明 Space 的 `task:read + evaluation:read`；查询与每段关系均验证同 Space |

新增 Run/Experiment 搜索的时间边界均包含端点，from 不得晚于 to。Run 摘要包含版本名称/号、状态/暂停原因、取消意图、总/完成/异常 Case 数与时间；Experiment 摘要包含版本名称/号、状态、Variant/关联 Run 数、授权 Token、失败码与人工结论身份。缺失名称显示不可用，不捏造当前配置。

Attempt 摘要包含 replayTaskId/executionTaskId、失败阶段/码、脱敏说明、时间、`currentCaseAttempt`。Result 摘要包含 ID、EvaluatorVersion 名称/号、evaluationAttemptNo、`currentEvaluationResultAttempt`、状态、score、summaryCode、Trace/Span ID 和时间。每个 Attempt 中每个 EvaluatorVersion 的最大评价尝试号为当前结果；score 只是便捷展示值。Metric/Evidence/Feedback 按需加载，不返回 Result 私有 detailsJson。

绑定级 expectedJson 是既有编辑载荷的一部分，详情读模型应保留以支持 DRAFT 无损编辑；它仅包含受验证的评估预期配置，不得作为 Prompt、文档正文或秘密的存储通道。读取和更新均受管理页面/后端既有数据安全约束。

名称、进度、绑定与结果批量加载，不在循环内 SQL/RPC。日志只记录非敏感身份、状态、时间条件存在性、页码/大小、结果数和 Trace ID，不打印完整查询请求。

Task 关联查询仅返回任务、来源任务、用例/版本、CaseRun/Attempt、Run、Experiment/Variant 身份，不读取其他业务域表，不根据 rootTaskId 推断所属实验。缺失父记录、跨 Space 或不一致的 Run/Variant 反向关系拒绝返回；请求不改变执行或评估状态。

Task 现有搜索增加可选 `executionMode`，来源选择器用 `COMPLETED + LIVE`。选择后单独调用现有 ReplayEligibility；不能对每行调用、不能将列表可见解释为动态准入已通过。“沉淀为测试用例”还需 `evaluation:manage`、当前 Space 归属及最终准入。

## 4. 调用审计与 Trace

Task 详情增加数据库中 Task traceId；AgentExecution 脱敏详情增加 traceId/spanId。隔离产物复用独立 `/execution-artifacts` 查询；反向实验关联使用正式外键关系，不能根据 rootTaskId 猜测。

`GET /api/task/tasks/{id}/trace-view` 先解析 Task/Space 并校验 `task:read`，只查询 Task 持久化的合法 Trace ID，不接收任意 traceId 或 URL。基础设施适配层封装 Jaeger 查询并输出稳定白名单 VO。

投影包含 availabilityCode、partial/truncated、业务身份、开始/结束时间与耗时、总 Span/错误/取消/重试计数、服务分布及节点。节点包含 spanId/parentSpanId、稳定名称、service、span kind、起止/耗时、受控状态和白名单属性；覆盖 Gateway/MQ/Task/A2A/Agent/Skill/MCP/GenAI/工具/结果处理。JDBC/Redis/HTTP 技术细节可默认折叠，总数、错误和关键路径仍可见。

可用性首版契约：

| availabilityCode | 含义 |
| --- | --- |
| `AVAILABLE` | 存在可用投影；不完整/截断由独立标志表达 |
| `NO_TRACE` | Task 没有 Trace 关联 |
| `INVALID_TRACE_ID` | 关联不是合法 OTel Trace ID，禁止查询 |
| `NOT_CONFIGURED` | 查询后端未配置 |
| `NOT_FOUND_OR_NOT_SAMPLED` | 查询无数据，不能断言一定是未采样 |
| `RETENTION_WINDOW_ELAPSED` | 查询无数据且关联已超过 14 天窗口；这是窗口判断，不是确证删除 |
| `BACKEND_UNAVAILABLE` | 超时或后端不可用 |
| `PAYLOAD_INVALID` | 返回无法安全解释的结构 |
| `PAYLOAD_TOO_LARGE` | 返回超过受控读取上限 |
| `UNRELATED_TRACE` | 无法安全确认 Trace 的 Task 关联 |

没有可用投影时统计字段为不可用，不能用 0 表示未知。查询返回错误或截断不改变 Task 业务状态。Trace 同时含多个 Run 时，不展示其他 Task 专属分支；只保留本 Task 分支与必要共享技术祖先。

只返回 ADR-0003 和 Collector 策略允许的最小诊断属性，不透传 Resource/log 全量属性、SQL、URL/请求头、异常栈、Prompt、工具参数/响应或正文。框架动态 Span 名可能含 SQL/URL，必须映射为受控名称。

OTel 保留 14 天。超过窗口仍可查看业务审计、快照 hash、权威 Token 账本和结果；遥测不可用是正常状态。可选 `VITE_JAEGER_UI_URL` 深链默认关闭，只有运维显式确认 Jaeger 等价访问控制/Space 隔离且当前用户有 task:read 时启用。工作台本身不依赖深链。

## 5. 报告 v1 与兼容降级

普通报告响应保留现有 revision/schema/hash/manifest/calculationInputHash/审计身份，新增 `compatible` 与 `compatibilityCode`。正文和 selectedRecordIds 改为公开强类型嵌套 VO，序列化字段形状及持久化内容不变。

- `selectedRecordIds`：runIds、attemptIds、resultIds、metricIds、evidenceIds、feedbackIds。
- `report`：expectedCaseCount、variantCount、cases、cells、summaries、comparisons、metricEvidence、feedback、feedbackCoverage、authorizedTokenBudget、actualTokenUsage、budgetOverrun。
- `cells` 仍使用 `{value,evidenceIds}`；value 保留身份、numeric/boolean/string 原值、unit、missingReason。
- summaries 保留 expected/valid/missing、true/false 分母及按单位统计；comparisons 保留 paired/incomparableCurrency/improved/worsened/unchanged 和配对差值。
- Case、MetricEvidence、Feedback、coverage 的既有字段保持；不能用裸 JsonNode 或 `any` 跳过 schema。

### 5.1 v1 字段形状

外层保持 `experimentId/revision/schemaVersion/manifestHash/calculationInputHash/selectedRecordIds/report/contentHash/generatedBy/generatedAt`，仅增加兼容标志。下表逐项对应当前报告生成器，不改变持久化 JSON 的字段名、嵌套层级或值类型。

| 路径 | 冻结字段 |
| --- | --- |
| `selectedRecordIds` | runIds、attemptIds、resultIds、metricIds、evidenceIds、feedbackIds，均为 ID 数组 |
| `report.cases[]` | variantKey、runId、testCaseVersionId、caseRunId、attemptId、taskId、attemptStatus、failureCode |
| `report.cells[]` | value、evidenceIds（ID 数组） |
| `report.cells[].value` | testCaseVersionId、metricKey、evaluatorVersionId、variantKey、runId、metricId、numericValue、booleanValue、stringValue、unit、missingReason |
| `report.summaries[]` | variantKey、metricKey、expectedCount、validCount、missingCount、trueCount、falseCount、numericTotalsByUnit、numericMeansByUnit、missingReasons |
| `report.comparisons[]` | candidateVariantKey、metricKey、pairedCount、incomparableCurrencyCount、improvedCount、worsenedCount、unchangedCount、meanCandidateMinusBaseline、meanCandidateMinusBaselineByCurrency |
| `report.metricEvidence[]` | metricId、evidenceId、evidenceType、businessId、contentHash |
| `report.feedback[]` | id、variantKey、testCaseVersionId、sourceType、sourceBusinessId、label、score |
| `report.feedbackCoverage[]` | variantKey、sourceType、coveredCaseCount |

身份与计数保留整数类型，numericValue/score/均值与总值保留十进制数，booleanValue 为布尔值，其余描述字段为字符串。numericTotalsByUnit/numericMeansByUnit/meanCandidateMinusBaselineByCurrency 为字符串键到十进制数的映射，missingReasons 为原因码到整数计数的映射。可空字段仍可空，不补默认值；列表顺序及空值序列化沿用现有生成器，不能为强类型改造重新计算或改写历史内容。

### 5.2 兼容状态与校验顺序

| compatibilityCode | compatible | 正文 |
| --- | --- | --- |
| `SUPPORTED` | true | 校验通过的 v1 强类型正文 |
| `SCHEMA_MISSING` | false | null |
| `SCHEMA_INVALID` | false | null |
| `SCHEMA_UNSUPPORTED` | false | null |
| `PAYLOAD_INVALID` | false | null |
| `CONTENT_HASH_MISMATCH` | false | null |

按 schema 缺失/非法/不支持 → 正文及选中 ID 结构 → 原始 reportJson UTF-8 SHA-256 的顺序判定；降级时仍返回 revision、持久化 hash 与兼容码，report/selectedRecordIds 均为空，不暴露未知原始 JSON。hash 校验直接使用持久化原文，不能通过 VO 重序列化计算历史 hash；不推断 schema、不回填、不改写 report/hash，完整性异常去重告警。

强类型改造前执行离线只读巡检，统计 `experiment_report.report_schema_version` 分布、空/非法/未知 schema、JSON 错误与 hash 不匹配，结果只保存计数。该巡检在实现批次中执行，不是新 HTTP 接口。使用历史 v1 fixture 做字段快照和 hash 测试。

## 6. 展示与验证边界

0、false、空字符串和 missing 分别呈现；分母取冻结 manifest，跨币种不合计，无有效样本不绘图。人工结论引用明确 revision，可选择证据不足，不能修改生产配置。重试确认区分仅重评与重跑模型及其 Token 消耗。

活动详情轮询采用单请求、3～5 秒间隔、隐藏暂停/回前台刷新、失败 5/10/20/30 秒退避、终态及卸载停止；不新增全局缓存框架或 WebSocket/SSE。

后续实现需验证权限与跨 Space、版本冻结、批量查询、Trace 白名单/降级、历史报告 shape/hash、恢复 ADR 的安全/幂等和真实端到端流程。本文不承担上线验收记录；功能、测试与真实数据验收完成前不标记已发布。
