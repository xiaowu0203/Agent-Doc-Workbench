# ADR-0005：Offline Experiment 契约

- 状态：已采纳
- 日期：2026-09-26
- 适用版本：v0.2.0 起

## 背景

Phase 3 已提供 Dataset/TestCase、确定性 Evaluator、标准 Metric、人工反馈和 Safe Replay。它能够对冻结输入重新执行和评价，但还不能表达“同一批用例在基线配置和候选配置下分别执行、重新评价并比较”的实验身份，也没有冻结实验应有用例/指标矩阵、候选配置、报告版本和人工结论。

离线实验必须复用现有 Task、AgentExecution、EvaluationRun、Token 账本和隔离机制，不能形成第二套执行状态机。它还必须证明候选执行只改变声明的变量，不能修改生产 Agent 配置，不能把历史 Result 当作本次基线，也不能因失败或劣化自动影响生产流量。

## 决策

v0.2.0 首版只开放 Prompt 变体。一个 Experiment 固定一个已发布且未归档的 DatasetVersion，包含一个 baseline 和一至十九个 candidate；总 Variant 数不得超过二十。前端实验页留给后续阶段，本阶段只提供后端 API。

每个 Variant 对应一条完整、独立的 EvaluationRun：

```text
Experiment
  ├── frozen manifest
  ├── BASELINE Variant ── EvaluationRun ── CaseAttempt ── REPLAY Task
  └── CANDIDATE Variant ─ EvaluationRun ── CaseAttempt ── EXPERIMENT Task
```

- baseline 必须通过来源执行快照新建 `REPLAY + ISOLATED` Task，不复用来源 Task 的输出、Trace、Result 或 Metric。
- candidate 必须通过来源冻结业务输入和不可变候选配置新建 `EXPERIMENT + ISOLATED` Task。
- 两侧使用本次 manifest 冻结的同一批 EvaluatorVersion 重新评价，各自拥有独立 Task、AgentExecution、Trace、Token 账本、候选产物和评价结果。
- Experiment 分组只使用 `Experiment → Variant → EvaluationRun` 正式关系，不使用 `root_task_id` 推断。
- Experiment 不修改生产 Agent，不自动创建或合并 ChangeRequest，不写 DocumentVersion 或草稿，也不自动分配线上流量。

### 数据集准入与 manifest

创建 Experiment 时一次性加载 DatasetVersion 的全部启用绑定并完成结构校验。只有以下条件全部成立才允许持久化 Experiment、Variant 和 manifest：

1. DatasetVersion 已发布、未归档且属于请求 Space。
2. 所有启用 TestCaseVersion 属于同一 Space、同一 Agent。
3. 所有来源 execution snapshot 均为 schema v3，且配置哈希一致。
4. 每个用例均有合法的来源输入身份、冻结文档版本与内容哈希、EvaluatorVersion 绑定。
5. candidate 数量为 1～19，variant key 在 Experiment 内唯一，变体类型全部为 `PROMPT`。

结构校验必须收集并返回全部不一致用例及原因，不能遇到第一项错误即停止；失败时不产生 Experiment 或部分 manifest。Replay 准入是动态条件，不属于创建时的结构一致性：preflight 展示每个用例当前的 ReplayEligibility，启动时重新检查，任一用例不合格就整体拒绝启动并保留 `CREATED` Experiment，不静默排除用例。

manifest 是预检和报告分母的权威输入，采用显式 schema 并整体计算稳定 SHA-256。首版 schema 至少冻结：

- DatasetVersion ID，以及按 DatasetVersion 绑定顺序排列的启用 TestCaseVersion ID；
- 每个用例的来源 Task ID、输入 schema/hash、冻结文档 ID/版本/内容哈希；
- 每个用例绑定的 EvaluatorVersion ID 与版本哈希；
- 共同来源 execution snapshot schema/hash；
- Metric 目录契约版本及有序快照/hash；目录项按 `metricKey` 排序，包含 key、类型、单位、币种单位标记、方向、来源和适用 evaluatorKey。

manifest 不能从实际创建成功的 CaseAttempt 反推。启动和报告生成均重新计算并核对 manifest hash；历史 manifest 不使用当前 Dataset 绑定或当前 Metric 目录重新解释。

### Prompt 候选配置身份

候选配置由 agent-service 所有并保持不可变，evaluation-service 只保存引用和证明字段。创建 candidate 时，调用方提交新的 Agent Prompt；agent-service 使用来源 v3 中冻结的 Skill 目录和现有统一 Prompt 构建层生成完整最终 `systemPrompt`，再派生完整 canonical v3 snapshot。Runtime 不得临时拼接候选 Prompt，也不得读取当前生产 Agent 配置重建候选。

允许变化的 v3 字段路径只有 `snapshot.systemPrompt`；`promptHash` 是快照外的派生身份。模型与参数、Skill 版本和路由结果、工具白名单、MCP 非秘密配置及运行限制必须沿用来源冻结值。候选配置必须保存：

- 来源与候选的完整 snapshot schema/hash；
- 实际变化的 `prompt_diff_field_paths`；
- 两侧移除 `snapshot.systemPrompt` 后相等的 `snapshot_without_prompt_hash`；
- 受限访问的候选 Agent Prompt、最终 system prompt 和 canonical v3 snapshot；
- 所属 Space、Agent、创建者和创建时间。

创建、启动、Runtime 恢复和报告生成都必须验证这组证明。出现非白名单 diff、跨 Space/Agent、来源失效、hash 篡改或必要 Credential 无法受控解析时 fail-closed。普通 Experiment/Variant 查询只暴露配置引用、schema、hash 和 diff 路径，不返回 Prompt 正文、完整快照或凭证。

### 数据模型与迁移

Phase 4 使用新的 V27 迁移，不修改已发布的 V1～V26。迁移保持 MySQL 5.7 兼容，不依赖 `CHECK`、递归 CTE 或数据库 JSON 类型语义。

- `experiment`：Space、DatasetVersion、状态、创建请求幂等身份、manifest schema/hash、授权 Token 上限、失败原因、审计主体与时间、人工结论及其引用的报告 revision。
- `experiment_variant`：Experiment 内唯一 key、`BASELINE/CANDIDATE` 角色、`PROMPT` 类型、冻结配置引用、来源/候选 snapshot hash、Prompt diff 证明和 EvaluationRun 关联。配置字段创建后不可修改。
- `experiment_report`：单调递增 revision、生成时选中的 Run/CaseAttempt/Result/Metric/反馈身份、逐用例与聚合结果、计算 schema/hash。`(experiment_id, revision)` 唯一且历史版本不可改写。

`evaluation_case_attempt.replay_task_id` 在回退窗口内保留。V27 新增可空且唯一的 `execution_task_id`，已有非空 `replay_task_id` 分批回填到新列；新代码只读取 `execution_task_id`。Replay 写入时暂时同步两列，Experiment 只写新列。草稿 Attempt 在 Task 下发前允许两列均为空；MySQL 唯一索引允许多个 NULL。旧列只在回退窗口关闭后的后续迁移中清理，不保留永久双读。

`execution_artifact` 继续归属于 Task/AgentExecution，不增加 experiment_id。反向追溯通过 `execution_artifact.task_id → evaluation_case_attempt.execution_task_id → evaluation_run.id → experiment_variant.evaluation_run_id → experiment.id` 完成。

### 状态与失败语义

Experiment 状态固定为：

```text
CREATED → STARTING → RUNNING → COMPLETED
                      │        COMPLETED_WITH_ERRORS
                      │        FAILED
                      ├──────→ PAUSED → RUNNING
                      └──────→ CANCEL_PENDING → CANCELED
```

- `CREATED`：manifest 与 Variant 已冻结，尚未启动；preflight 失败仍停留此状态。
- `STARTING`：已原子接受启动请求，正在创建完整 Variant Run 集合。
- `RUNNING`：所有 Variant 已有关联 Run，至少一条未终态。
- `PAUSED`：派发、鉴权或对账遇到可恢复阻塞；只有显式恢复才能继续。
- `COMPLETED`：所有 Variant Run 终态且没有执行、评价或证据缺失。
- `COMPLETED_WITH_ERRORS`：所有 Variant Run 终态，但存在执行失败、评价失败、取消或缺失；仍生成完整缺失矩阵和报告。
- `FAILED`：无法形成完整 Variant Run 集合且不可恢复，或实验级契约已经不可满足；不伪造最终报告。
- `CANCEL_PENDING/CANCELED`：分别表示取消处理中和全部可取消工作已收敛。

Variant 不建立独立持久化状态机，其进度由关联 EvaluationRun 和派发记录派生。只在所有 Variant 均有关联 Run 且全部终态时生成最终报告。

稳定原因码分为三组；API 和持久化均保存 code，不以异常消息充当机器契约：

| 类别 | 原因码 |
| --- | --- |
| 结构/配置 | `DATASET_VERSION_NOT_PUBLISHED`、`DATASET_VERSION_ARCHIVED`、`CASE_SPACE_MISMATCH`、`CASE_AGENT_MISMATCH`、`SOURCE_INPUT_IDENTITY_MISMATCH`、`SOURCE_SNAPSHOT_SCHEMA_MISMATCH`、`SOURCE_SNAPSHOT_HASH_MISMATCH`、`EVALUATOR_BINDING_INVALID`、`VARIANT_LIMIT_EXCEEDED`、`UNSUPPORTED_VARIANT_TYPE`、`CANDIDATE_CONFIG_INVALID`、`CANDIDATE_CONFIG_TAMPERED`、`MANIFEST_MISMATCH` |
| 启动/编排 | `SOURCE_NOT_REPLAYABLE`、`CREDENTIAL_UNAVAILABLE`、`BUDGET_CONFIRMATION_REQUIRED`、`BUDGET_LIMIT_EXCEEDED`、`AUTHORIZATION_EXPIRED`、`DISPATCH_UNAVAILABLE`、`RECONCILIATION_BLOCKED`、`ORCHESTRATION_FAILED`、`CANCEL_REQUESTED`、`BUDGET_OVERRUN` |
| 结果/报告 | `EXECUTION_FAILED`、`EVALUATION_FAILED`、`EVALUATION_SKIPPED`、`TASK_CANCELED`、`EVIDENCE_MISSING`、`METRIC_CONTRACT_MISMATCH`、`EVALUATOR_VERSION_MISMATCH`、`REPORT_INPUT_INCOMPLETE` |

结构校验响应使用 `testCaseVersionId + code`；动态 Replay 预检在 Experiment 层使用 `SOURCE_NOT_REPLAYABLE`，同时原样保留 task-service 返回的 `ReplayEligibility.reasonCode` 作为 `detailReasonCode`，不能把 `SOURCE_NOT_TERMINAL`、`INPUT_SNAPSHOT_INVALID` 等既有细分原因压平。Experiment、Variant、用例和指标层可以各自保存适用的 code。`DISPATCH_UNAVAILABLE`、`AUTHORIZATION_EXPIRED` 与 `RECONCILIATION_BLOCKED` 复用现有 EvaluationRun 暂停语义。新增或改变既有码语义属于 API 契约变更，不得复用旧码表达新含义。

### 编排、幂等与预算

Experiment、EvaluationRun 和 CaseAttempt 先在本地事务中记录草稿，再进行跨服务调用。跨服务调用和模型执行不放入长数据库事务。

- 创建请求以 `clientRequestKey` 和规范化请求 hash 幂等：同 key 同 hash 返回原 Experiment，同 key 异 hash 返回冲突。
- 每个 Task 的派生键固定为 `experiment:{experimentId}:variant:{variantId}:case:{testCaseVersionId}:attempt:{attemptNo}`。
- Task 请求 hash 覆盖来源 Task ID、来源输入身份、Variant ID、冻结配置 hash 和 attemptNo；同键异内容拒绝。
- evaluation-service 在调用 task-service 前事务性分配并持久化 attemptNo。网络重试、响应丢失、恢复和对账复用原 attemptNo；只有用户显式重试才分配新 attemptNo。
- 并发启动以 `CREATED → STARTING` 原子转换收敛；重复启动返回当前资源，manifest 或预算确认内容不一致返回冲突。
- 部分派发失败保留诊断和已成功关联，恢复只补缺失项，不重跑已完成项。

启动请求必须明确提交计划授权 Token 上限。所有计划 Task 的冻结 Token 预算之和超过授权值时以 `BUDGET_LIMIT_EXCEEDED` 拒绝启动。该校验是启动门禁，不是严格的运行时总量熔断；在途调用可能导致实际消耗超出授权值。检测到超额后尽力取消活跃项，保存 `BUDGET_OVERRUN`，报告分别展示授权、实际和超额值。成本只读取 AgentExecution 的权威 Token 账本与冻结价格，不从 Trace 推算，也不跨币种相加。

### 报告与人工结论

报告以 manifest 构造应有用例/指标矩阵，不能用已产生的 CaseAttempt 反推分母。两侧均缺失的 metricKey 仍必须出现在报告中并带原因。报告逐用例保存 baseline/candidate 原值、差值或方向、证据引用、有效性和缺失原因；聚合至少包含执行成功率、质量、Token、成本、延迟和人工反馈覆盖。

- 执行成功率以 manifest 全部应执行用例为分母；执行失败和证据缺失分别计数。
- 布尔质量指标展示 true 数与有效样本数；数值指标展示两侧各自有效样本均值、各自分母、配对差值和配对分母；字符串指标只展示原值。
- Token 展示合计和有效样本均值；延迟展示逐用例值和有效样本均值；费用按币种分别展示，禁止跨币种合并。
- Evaluator 错误、跳过、取消和证据缺失是 missing/failure，不能写成数值 0。
- 人工反馈复用不可变的 `evaluation_feedback`，只选本次 Run/Attempt/Task/Execution 的反馈；多次反馈并列保留，按用例与 `MANUAL/CHANGE_REQUEST` 来源统计覆盖，不合成为质量分。

所有 Run 首次终态时自动生成 revision 1。后续重试只更新当前评估投影，不自动改写或新增历史报告；所有 Run 再次终态后，有权限者可显式重算。相同 manifest、选中记录 ID 和计算 schema/hash 返回已有 revision，输入变化才事务性分配下一 revision。人工结论固定引用一个 report revision，只记录 `ACCEPTED`、`REJECTED` 或 `INSUFFICIENT_EVIDENCE` 及理由，不触发生产配置变更。

### API、DTO 与权限

公共资源路径固定为 `/api/evaluation/experiments`。首版写入 DTO 冻结如下；实现可增加 Bean Validation 注解，但不得改变字段语义：

| DTO | 字段 |
| --- | --- |
| `ExperimentCreateDTO` | `clientRequestKey`、`datasetVersionId`、`candidateVariants` |
| `PromptCandidateCreateDTO` | `variantKey`、`agentPrompt`；类型由入口固定为 `PROMPT`，不接受调用方伪造其他类型 |
| `ExperimentStartDTO` | `authorizedTokenBudget` |
| `ExperimentReportRecalculateDTO` | `clientRequestKey` |
| `ExperimentDecisionDTO` | `reportRevision`、`decision`、`reason` |

baseline 从 DatasetVersion 的来源身份隐式创建，调用方不能上传 baseline 输出、历史 Result 或任意 snapshot。查询 VO 至少返回 Experiment/Variant ID、状态或派生进度、manifest/config hash、Run 关联、预算汇总、稳定原因码和审计身份；普通 VO 不返回 Prompt 正文或完整 snapshot。

首版读模型按下表冻结；逐用例报告和聚合指标的嵌套 VO 在 P4-04 只能细化类型，不能丢失这里规定的身份、分母和缺失语义：

| VO | 必备字段 |
| --- | --- |
| `ExperimentVO` | `id`、`spaceId`、`datasetVersionId`、`status`、`manifestSchemaVersion`、`manifestHash`、`authorizedTokenBudget`、`actualTokenUsage`、`budgetOverrun`、`failureCode`、创建/运行/取消/决策审计身份与时间 |
| `ExperimentVariantVO` | `id`、`experimentId`、`variantKey`、`role`、`type`、`candidateConfigRef`、`sourceSnapshotHash`、`candidateSnapshotHash`、`promptDiffFieldPaths`、`snapshotWithoutPromptHash`、`evaluationRunId`、派生进度 |
| `ExperimentPreflightVO` | `experimentId`、`caseCount`、`variantCount`、`plannedTaskCount`、`plannedTokenBudget`、`eligible`、`issues` |
| `ExperimentPreflightIssueVO` | `testCaseVersionId`、`code`、`detailReasonCode` |
| `ExperimentReportRevisionVO` | `experimentId`、`revision`、`schemaVersion`、`contentHash`、`generatedBy`、`generatedAt` |
| `ExperimentReportVO` | revision 身份、manifest hash、选中的 Run/Attempt/Result/Metric/反馈 ID、逐用例矩阵、聚合结果、授权/实际/超额 Token、不可比与缺失原因 |

| API | 权限 |
| --- | --- |
| `POST /api/evaluation/experiments` | `evaluation:manage`；提交候选 Prompt 还需 `agent:manage` |
| `GET /{id}`、`GET /{id}/preflight`、`GET /{id}/variants`、`GET /{id}/reports`、`GET /{id}/reports/{revision}` | `evaluation:read` |
| `POST /{id}/start`、`PUT /{id}/cancel`，以及通过既有 Run API 恢复/重试 | `evaluation:run` |
| `POST /{id}/reports/recalculate`、`POST /{id}/decision` | `evaluation:manage` |

所有入口先校验资源所属 Space，再校验权限。创建者、运行者、取消者、重算者和决策者分别审计，不能以运行者代替决策者。内部调用继续使用绑定 EvaluationRun、Space 和 Task 集合的窄权限 WorkerCapability；只扩展为合法的 `EXPERIMENT + ISOLATED` Task，不放宽到 LIVE Task。

### 隔离与回退

Experiment 沿用 ADR-0002 的四层隔离：固定文档版本读取、Workbench 写操作 capture-only、外部 MCP 默认拒绝、终态不提交真实草稿。候选产物只进入 `execution_artifact`。任何一层未生效都必须拒绝执行。

部署顺序固定为 V27、能兼容旧 Replay 数据的服务版本、最后开放 Experiment 创建和调度。回退前必须：

1. 关闭 Experiment 新建和派发，取消或等待全部 Experiment Task/EvaluationRun 终态，核对活跃数为零。
2. 使用目标旧版本演练所有查询、扫描器和工作线程，确认只处理 Replay 或显式过滤 `replay_task_id IS NOT NULL`，不会把旧列为空的 Experiment Attempt 当成 Replay。

若第二项未通过，不得直接回退旧二进制，也不得删除 Experiment 审计数据；应先部署兼容过滤版本或隔离相应记录。无法识别 `EXPERIMENT` 执行模式的更早版本不属于直接回退目标。

## 影响

### 正面影响

- baseline 与 candidate 在相同冻结输入和 EvaluatorVersion 下重新执行、重新评价，比较身份明确。
- Prompt 是唯一变量且有可验证的 hash 证明，生产 Agent 配置保持不变。
- 报告分母、缺失值、成本和人工结论可复核，重试不会改写历史报告。
- 编排复用 Phase 3 的 EvaluationRun、隔离、取消、对账和 Token 权威账本，不增加平行执行体系。

### 代价

- 首版只支持同 Agent、同来源 execution snapshot 的 DatasetVersion，已有数据集可能需要新建版本。
- 跨服务编排需要幂等键、暂停/恢复与对账，不能依靠单一数据库事务。
- Prompt 候选配置需要 agent-service 新增受限、不可变的持久化身份。
- V27 在回退窗口内需要同步写入 Replay 的新旧 Task 关联列。

## 未采用方案

### 直接复用来源历史 Result 作为 baseline

拒绝。历史输出可能使用不同 EvaluatorVersion，也不能与本次 candidate 形成同一次实验的独立成本、Trace 和证据。

### 修改生产 Agent 后执行 candidate

拒绝。它会引入并发竞态、污染线上配置，也无法证明实验使用的确切配置身份。

### 从实际 Attempt 反推 manifest 和报告分母

拒绝。部分派发失败会让缺失用例消失，使成功率和质量聚合失真。

### 首版支持任意模型、Skill 或 Router 变体

拒绝。缺少逐类规范化、权限与可比性契约；先用 Prompt 唯一变量验证闭环。

### 将总 Token 授权声明为严格费用封顶

拒绝。当前批量创建后直接派发且存在在途调用；没有执行层的实验累计门禁就无法提供该保证。
