# ADR-0002：Replay 副作用隔离

- 状态：已采纳
- 日期：2026-09-14
- 适用版本：v0.2.0 起

## 背景

生产任务的 Workbench MCP 不只有读取能力：`workbench_propose_changes` 会创建正式文档 ChangeRequest，`workbench_apply_draft_changes` 会写入草稿暂存，任务终态还会提交或丢弃暂存内容。外部 MCP 工具是否只读目前也没有统一元数据。

如果 Replay 或离线实验直接复用生产 Task Capability，同一历史输入会重复创建审批单、修改草稿、触发未知外部操作，评估结果也会污染真实业务数据。仅在 Prompt 中要求模型“不要写”不是安全边界。

## 决策

所有 `REPLAY` 和 `EXPERIMENT` 执行必须使用 `ISOLATED` 模式；用户正常创建、重跑和审批重改必须使用 `LIVE` 模式，不允许调用方覆盖。模式由服务端根据血缘类型确定、持久化并进入 Capability 与执行快照。非法组合在创建与调度两处 fail-closed，具体合法矩阵见 ADR-0001。

### 工具策略

| 能力 | `LIVE` | `ISOLATED` |
| --- | --- | --- |
| 获取任务上下文 | 读取当前任务上下文 | 读取来源执行冻结的上下文 |
| 读取文档片段 | 只读取 Task 创建时冻结并校验哈希的指定文档版本 | 只读取来源执行冻结的文档版本或冻结内容 |
| 提交正式文档变更 | 创建 ChangeRequest | 不写业务表，捕获为候选变更产物 |
| 应用草稿变更 | 写入任务隔离的草稿暂存 | 不写文档，捕获为候选草稿产物 |
| 终态提交/丢弃草稿 | 正常执行 | 不调用文档服务 |
| 外部 MCP | 按 Agent 与绑定白名单执行 | v0.2.0 首版全部禁用 |

隔离执行产生的候选变更必须关联当前 Task、来源 Task/AgentExecution 和执行快照哈希，但不得伪装成 ChangeRequest 或正式 DocumentVersion。后续 Evaluator 可以读取这些产物，人工确认也不能绕过现有 ChangeRequest 流程直接合并正式文档。

### 强制位置

副作用隔离必须同时在四层生效：

1. 创建 Task Capability 时移除生产写入动作并写入执行模式声明。
2. 构建工具集合时替换 Workbench 写工具为 capture-only 适配器，外部 MCP 默认拒绝。
3. Workbench MCP 与 ChangeRequest 创建入口再次校验持久化 Task 模式，防止伪造客户端或遗漏工具过滤后写入正式审批单。
4. task-service 终态处理在 `ISOLATED` 下禁止调用草稿提交/丢弃和其他业务写入；候选产物只进入隔离产物存储。

任何一层缺失都不能视为隔离完成。

### 冻结输入

Replay 的任务输入与运行配置分别由 ADR-0001 的 input snapshot 和 execution snapshot 表示，并以各自 schema 版本与哈希组成四元身份。用户指令、目标文档与冻结版本、读取范围和关注区域属于任务输入；系统 Prompt、模型参数、Skill 版本与选择结果、工具定义以及 MCP 非秘密配置属于运行配置。当前 v0.1.0 未冻结文档版本/正文，也没有从 AgentExecution 快照恢复 Runtime 的路径；实现 Replay 前必须补齐，不能把读取当前文档称为 Replay。

### 非 MCP 副作用

- v0.2.0 当前不执行 Skill 包内脚本；`ISOLATED` 不得改变这一边界。
- 代码执行、外部 HTTP、消息发送、文件写入或未来新增的 effectful capability 默认拒绝，只有先进入统一能力目录、声明副作用类别并实现对应隔离适配器后才能开放。
- Prompt 约束、工具描述和“只读”自声明都不能代替服务端授权与隔离。

`ISOLATED` 只表示受平台注册入口的业务副作用隔离，不是操作系统级安全沙箱，也不授权执行任意代码。

### 候选产物最小模型

候选产物在 Phase 3 启用 Replay 前落到独立的 `execution_artifact`，不复用 `change_request` 或 `document_version`。Phase 1 只固化以下 schema，不提前建表：

| 字段 | 说明 |
| --- | --- |
| `id` | 产物 ID |
| `space_id`、`task_id`、`execution_id` | 当前隔离执行的权限和归属 |
| `source_task_id` | Replay/Experiment 来源 Task |
| `sequence_no`、`source_tool_call_id` | 执行内稳定序号，以及可选的来源工具调用 |
| `artifact_type` | 首版仅 `CHANGE_PROPOSAL`、`DRAFT_CHANGES`、`RESULT_SUMMARY` |
| `schema_version` | 产物载荷 schema 版本 |
| `payload_json`、`payload_sha256` | 结构化候选内容及稳定哈希 |
| `created_at` | 创建时间 |

约束如下：

- 产物不可变，只允许 Runtime 内部追加；同一执行按 `(execution_id, sequence_no)` 唯一，重复写入必须校验载荷哈希一致。
- 读取复用所属 Space 的 `TASK_READ` 权限，并校验 Task/Execution 归属；普通用户不能跨 Space 读取。
- Phase 3 提供独立只读查询 `GET /api/task/tasks/{taskId}/execution-artifacts`；它不复用 ChangeRequest 查询或审批 API。
- Task 逻辑删除不级联删除候选产物；它与执行审计采用相同保留期。v0.2.0 不设置自动 TTL，避免评估结果失去复现证据；容量治理进入后续保留策略专项。
- 现有 ChangeRequest 审批接口不得接受 `execution_artifact.id`。未来若支持“转为正式提案”，必须走独立显式命令，重新校验当前权限、文档状态和 `baseVersion`，再创建新的 ChangeRequest。
- 在该存储和四层隔离全部完成前，系统必须 fail-closed，拒绝执行 `ISOLATED` Task。

## 影响

- Replay 不会创建真实审批单、修改文档或触发默认未知的外部副作用。
- capture-only 工具保留模型的工具调用形态，使文档变更质量仍可被评价。
- v0.2.0 首版禁用全部外部 MCP Replay；只读元数据、审核主体和第三方可信度作为后续独立决策，不在首版隐式实现。
- `ISOLATED` 不是安全沙箱，不授权执行 Skill 脚本或任意代码。

## 未采用方案

### Replay 只禁用所有写工具

不作为最终方案。它安全但会改变依赖变更工具的 Agent 行为，使文档任务评价失真；可作为 capture-only 适配器完成前的受限降级。

### 复制生产数据库或真实文档到临时环境

不作为 v0.2.0 MVP。环境复制成本和秘密治理复杂度过高，当前只需要固定输入读取与候选产物捕获。
