# ADR-0001：Run-compatible Execution 模型

- 状态：已采纳
- 日期：2026-09-14
- 适用版本：v0.2.0 起

## 背景

v0.1.0 已经形成 `task → agent_execution → model_call/tool_call` 链路。`task` 同时承载用户可见的执行请求、状态、预算、Capability、A2A 标识和业务结果；`agent_execution` 承载运行时状态、不可变配置快照以及模型/工具调用审计。数据库通过 `agent_execution.workbench_task_id` 唯一约束维持一项 Task 对应一次 AgentExecution。

Replay、离线评估和实验要求同一来源工作能够产生多次互不覆盖、可独立核算的执行。直接照搬长期路线图中的抽象并新增 `run` 表，会与现有 Task 的职责大面积重叠；把一个 Task 改为对应多个 AgentExecution，又会同时破坏任务状态、A2A 幂等、Capability、Token 账本、ChangeRequest 来源和现有查询契约。

## 决策

v0.2.0 不新增独立 `run` 表，也不把 `agent_execution` 改造成同一 Task 下的一对多记录。

一次 Run-compatible Execution 由一对记录组成：

```text
Run identity = task.id

Task
  └── AgentExecution (1:1)
        ├── ModelCall (1:N)
        └── ToolCall  (1:N)
```

- `Task` 是业务执行信封，也是公开的 Run 身份、权限与预算边界。
- `AgentExecution` 是 Agent Runtime 的物理执行明细和不可变快照载体。
- 每次重跑、审批重改、Replay 或实验变体都创建新的 Task 与 AgentExecution，不覆盖来源记录。
- 多次执行通过显式血缘组成一个逻辑工作组，而不是复用同一个 Task 状态机。
- v0.1.0 的 `task.id`、Task API、`workbench_task_id` 幂等键和一对一唯一约束保持兼容。

Phase 1 使用 V23 及后续新增迁移，为 Task 补齐最小血缘和执行语义：

- `root_task_id`：逻辑工作组的根 Task；根记录指向自身。
- `parent_task_id`：直接来源 Task，沿用现有字段。
- `lineage_type`：`ORIGINAL`、`RERUN`、`REVIEW_REWORK`、`REPLAY`、`EXPERIMENT`、`LEGACY_UNKNOWN`。
- `execution_mode`：`LIVE` 或 `ISOLATED`。

两列职责不同：`lineage_type` 解释“为什么产生这次执行”，`execution_mode` 决定“允许产生哪些副作用”。合法组合固定为：

- `ORIGINAL`、`RERUN`、`REVIEW_REWORK` → `LIVE`；
- `REPLAY`、`EXPERIMENT` → `ISOLATED`；
- `LEGACY_UNKNOWN` → `LIVE`，且仅允许由历史迁移写入。

任何不在上述集合中的组合必须在领域创建入口和调度入口 fail-closed。项目仍兼容 MySQL 5.7，不能把可能被旧版本忽略的 `CHECK` 约束当作唯一防线；所有 Task 写入口使用同一个领域校验，并用迁移后完整性 SQL 和集成测试兜底。Phase 1 对外 DTO 不暴露 `execution_mode`，运行时只允许新建前三种 `LIVE` 组合，`REPLAY`、`EXPERIMENT` 与 `LEGACY_UNKNOWN` 均不得由新业务请求写入。

由于来源 Task 与 AgentExecution 保持一对一，Replay 通过 `parent_task_id` 可以精确找到来源执行，不重复增加 `source_execution_id`。只有未来该一对一不变量被新 ADR 替代时，才重新评估独立执行来源字段。

### V23 历史回填

V23 必须在停止 Task、Agent 与 Document 写入的维护窗口内按以下顺序迁移，不支持新旧服务混跑；不能只给新增列设置默认值：

1. 先以可空列增加 `root_task_id`、`lineage_type`、`execution_mode`，避免 DDL 默认值掩盖历史语义。
2. 使用原始 SQL（包括逻辑删除的 ChangeRequest）预检：重复 `rework_task_id`、重改关系缺失或冲突、父缺失、自环和循环。`CHANGE_REQUEST_REWORK_CREATED` / `TASK_RETRY` 审计仅作为交叉核验；它与 `rework_task_id` 矛盾时按异常处理，不能静默标成 `RERUN`。
3. 先识别异常种子，再向所有后代传播 `LEGACY_UNKNOWN`。父缺失、自环或关系冲突以异常节点自身为 anchor；循环以环内最小 `task.id` 为确定性 anchor。受影响节点的 `root_task_id` 指向沿自身父链最先遇到的异常 anchor；若该 anchor 是循环成员则归一为环内最小 ID。这样即使同一父链存在多个异常种子也有唯一结果。报告必须列出异常种子、原因、anchor 和全部受影响后代，后代不得再作为正常 `RERUN` 处理。
4. 对剩余正常图回填：所有记录的 `execution_mode = LIVE`；无父节点为 `ORIGINAL` 且根指向自身；`task.id = change_request.rework_task_id` 为 `REVIEW_REWORK`；其余有父记录的节点为 `RERUN`。v0.1.0 只有重跑与审批重改两个受支持入口会写入 `parent_task_id`。
5. 正常子记录沿 `parent_task_id` 迭代回溯并继承最早根记录。实现必须兼容 MySQL 5.7，不使用递归 CTE。
6. 完成回填后校验合法组合、根可达性、一对一不变量与 Token 关联，再把三个新列收紧为 `NOT NULL`。

维护窗口之前必须先在目标数据副本或生产只读连接执行同一组预检 SQL，提前暴露脏数据；不能等 Flyway 执行到一半才第一次发现。历史数据若完全缺少关系与审计证据，迁移无法凭空判断人工修改的真实意图，该限制必须进入预检报告。

建议索引为：

- `idx_task_root_created(root_task_id, created_at, id)`：按逻辑工作分页列出执行。
- `idx_task_parent(parent_task_id)`：查找直接派生执行。

不同时创建单列 `root_task_id` 索引，因为它已是复合索引最左列；`lineage_type` 是低基数字段，只有出现独立高频过滤查询并经 `EXPLAIN` 证明需要时再增加索引。

### Phase 1 输入快照契约

Phase 1 必须先补齐未来 Replay 所需的稳定输入身份，但不提前开放 Replay API：

- Task 增加 `document_version_snapshot`、`document_content_sha256`、`input_snapshot_schema_version` 和 `input_snapshot_hash`。
- 输入快照覆盖任务级业务输入身份：用户指令、Space/目标文档 ID 与类型、冻结的文档版本与内容哈希、读取范围、关注区域、Token 预算、血缘类型与执行模式。原始 Task 在创建时冻结当前正式版本；`RERUN` 复制来源 Task 的冻结版本；`REVIEW_REWORK` 在新 Task 创建时冻结当时的当前正式版本，并把审批意见加入输入。审批所针对的旧版本仍由 ChangeRequest 保留用于审计，但执行不能读取未冻结的“当前版本”。
- AgentExecution 增加 `execution_snapshot_schema_version`。执行快照只覆盖运行时解析后的配置：系统 Prompt、模型及参数、Skill 版本与路由结果、工具定义、MCP 非秘密配置和运行限制；不得包含 Task/Execution/A2A ID、时间戳、状态、Token 结果，也不重复包含任务输入。
- v0.1.0 的 `execution_snapshot_hash` 已包含 `workbenchTaskId`、用户指令等字段，记为 legacy schema v1。Phase 1 必须以 schema v2 实现上述新边界，不能把旧哈希直接宣称为可 Replay 的配置身份。
- Capability 在 `ISOLATED` 模式下必须同时绑定固定文档版本与内容哈希，不能只绑定 Document ID。
- Phase 1 尚未提供 capture-only 产物前，所有入口必须拒绝调度 `ISOLATED` Task；不能以“模式字段已存在”为由宣称 Replay 可用。

快照哈希统一采用：对显式 schema 的对象键按字典序排列；数组按各领域规定的稳定键排序，只有顺序本身具有语义时保留原顺序；保留显式 `null`；按 UTF-8 编码后计算 SHA-256，输出小写十六进制。schema 版本参与哈希输入，任何字段、排序或空值规则变化都必须升级 schema 版本。Replay 身份以 `(input_snapshot_schema_version, input_snapshot_hash, execution_snapshot_schema_version, execution_snapshot_hash)` 四元组校验；Experiment 应保留输入身份但形成新的执行快照身份。

文档服务是 Phase 1 的显式交付方：除提供创建时的版本与 SHA-256 外，还必须提供按版本读取的内部契约。Workbench 读工具执行时只能读取 Task 冻结的版本并校验内容哈希；版本不存在或哈希不一致时任务失败并报告输入冲突，禁止退回读取当前版本冒充同一输入。

### 一对一不变量防护

- 数据库唯一键 `uk_agent_execution_workbench_task(workbench_task_id)` 是最终一致性防线，Phase 1 不得删除或放宽。
- 创建路径必须把唯一键冲突解释为幂等竞争并读取已有执行，不能创建第二条记录。
- 查询路径继续按唯一结果读取；出现多行应失败并告警，不得静默选择最新一条。
- V23 升级测试和发布门禁必须执行完整性查询，验证每个 `workbench_task_id` 最多一条 AgentExecution。
- 不增加冗余 `source_execution_id`：重复外键同样可能与来源 Task 发生分叉，不能替代唯一约束和完整性校验。

语义区分如下：

| 类型 | 输入与配置 | 副作用模式 |
| --- | --- | --- |
| `ORIGINAL` | 创建时冻结文档版本与输入；执行开始时冻结当前 Agent 配置 | `LIVE` |
| `RERUN` | 复制来源任务输入与冻结文档版本，使用当前 Agent 配置 | `LIVE` |
| `REVIEW_REWORK` | 审批意见 + 重改创建时冻结的当前正式文档版本，使用当前 Agent 配置 | `LIVE` |
| `REPLAY` | 来源执行的冻结输入和快照 | `ISOLATED` |
| `EXPERIMENT` | 来源冻结输入 + 显式候选变体，解析后形成新的不可变快照 | `ISOLATED` |
| `LEGACY_UNKNOWN` | 仅表示迁移发现的异常历史血缘；禁止新建 | `LIVE` |

`RERUN` 与 `REPLAY` 不得混为同一个操作：前者回答“用当前配置再做一次”，后者回答“基于可追溯的冻结输入受控复现”。

Phase 1 不预留 `experiment_group_id` 或 `variant_key`。`root_task_id` 只表示工作血缘，不承担实验分组语义；Phase 4 在 Dataset、Experiment、Variant 的生命周期和权限确定后，通过正式关系表或字段建模。提前增加无所有者、无约束的可空列既不能消除后续迁移，也容易形成孤立概念。

## 影响

### 正面影响

- 保留 v0.1.0 的 API、A2A、Capability、取消、预算、审批和查询主链路。
- 每次执行都有独立状态、成本、Trace、快照和业务结果，不会污染历史记录。
- Phase 1 只需增量补齐 Task 血缘与模式，不需要搬迁模块或建立重复聚合。

### 代价

- “一个逻辑任务的多次运行”需要按 `root_task_id` 聚合多个 Task，概念上不是单行一对多。
- Replay 必须新增从历史 AgentExecution 快照恢复上下文的入口，不能继续调用当前配置捕获流程。
- `parent_task_id` 已被重跑和审批重改共用，必须增加 `lineage_type` 才能消除歧义。
- V23 需要执行 MySQL 5.7 兼容的迭代回填和异常检测，不能只做一次简单 `UPDATE`。

## 未采用方案

### 新增独立 Run 表

拒绝。现有 Task 已经拥有 Run 的大部分状态和外部契约，新表会造成双状态机、双预算边界和迁移期双写。

### 一个 Task 对应多个 AgentExecution

拒绝作为 v0.2.0 方案。它要求重写 Task 单值字段、A2A 幂等键、Task Capability、Token 账本和 ChangeRequest 来源，风险超过本版本闭环所需。

### 将 Task 全量重命名为 Run

拒绝。仅改善术语，却会造成 API、数据库、前端和文档的大面积无收益迁移。
