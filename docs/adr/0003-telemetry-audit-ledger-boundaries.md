# ADR-0003：遥测、审计、快照与 Token 账本边界

- 状态：已采纳
- 日期：2026-09-14
- 适用版本：v0.2.0 起

## 背景

v0.1.0 已有自定义 `traceId`、追加型业务审计、AgentExecution 快照、模型/工具调用明细和 Token 用量表。v0.2.0 将引入 OpenTelemetry，但 OTel 数据可能被采样、过期或导出失败，不能承担权限追责、费用核算或执行复现。

如果多个记录体系没有明确真相边界，后续会出现“Trace 成功但业务状态未落库”“Token 在两处重复计费”或“遥测属性泄露 Prompt/密钥”等问题。

## 决策

四类记录各自负责不同事实，不互相替代：

| 记录体系 | 权威职责 | 不负责 |
| --- | --- | --- |
| AgentExecution 快照 | 复现一次执行实际使用的输入、模型、Prompt、Skill、工具、MCP 非秘密配置和关键哈希 | 业务审批追责、监控告警 |
| 业务审计日志 | 人或 Agent 对业务资源执行了什么动作，以及审批状态变化 | 完整调用耗时、模型内部遥测 |
| Token 权威账本 | 每次执行的 Token、估算标记、计费依据和可重新核算成本 | Span 展示值、质量评价 |
| OpenTelemetry | 跨服务传播、延迟、错误、重试和运行阶段等遥测事实 | 快照、审批审计、账单和业务最终状态 |

### 关联标识

- `run.id` 使用 `task.id`。
- `execution.id` 使用 `agent_execution.id`。
- 一次 Run 建立根 Trace；同步调用使用 W3C Trace Context 传播，MQ、A2A 回调等异步边界使用传播上下文或 Span Link。
- Task、AgentExecution、Token 明细和业务审计应保存可用的 `trace_id` 或稳定关联键；Trace 属性同时携带非敏感的 `run.id` 与 `execution.id`。
- 现有 `X-Trace-Id` 可在兼容期用于日志检索，但不能代替 W3C `traceparent`。

### Token 记账

- v0.2.0 的 `token_usage_detail` 粒度明确为“每个 AgentExecution 一条聚合用量”，不是每次 MCP 调用或每轮 ModelCall 一条；只要终态执行已经产生可用 Token，成功、失败或取消都必须幂等记账。
- V23 为 `token_usage_detail` 增加非空 `execution_id` 并建立唯一键 `uk_token_usage_execution(execution_id)`；重复 A2A 回调通过数据库唯一约束和幂等读取不得重复插入。
- AgentExecution 保存模型返回或本地估算的执行用量，Task Service 负责业务账本和预算聚合。
- 账本直接保存 `model_config_version`、`input_price_per_million`、`output_price_per_million`、`currency`、`pricing_schema_version` 和 `pricing_captured_at`。首版 `currency = CNY`，但仍显式落库；不使用不存在历史价格表支撑的虚假 `price_version`。
- 单价在 AgentExecution 准备阶段随模型配置一起冻结，并通过内部 Token 用量投影传给 Task Service；Task Service 不再为了历史执行账单读取当前 Agent/模型价格。
- `estimated_cost` 只由该行价格快照与 Token 数据计算，不从后续可变的当前模型价格反推。计价公式变化必须升级 `pricing_schema_version`。
- Phase 1 在写新账本时填充当前 `TraceContext` 的关联 ID；Phase 2 接入 W3C 上下文后再统一为 OTel trace ID。历史账本没有可信 Trace 时保留 `trace_id = NULL`，不伪造关联；查询和账单展示必须把空值显示为“不可用”，该列不得因 V23 回填而收紧为非空。OTel 中的 Token 仍只是观测副本。

历史 Token 行通过 `task_id = agent_execution.workbench_task_id` 回填 `execution_id`。若发现一个 Task 无执行、对应多个执行或已有重复 Token 行，V23 必须让迁移验证失败并输出异常数据，不允许任意去重或选择一条继续。

同一组只读完整性 SQL 必须在生产维护窗口前预执行，至少检查 Task ↔ AgentExecution 一对一、Token 无执行、同一 execution 的重复候选行以及价格字段可回填性。Flyway 保留 fail-closed 校验作为最后防线，但不应成为第一次发现生产脏数据的时点。

若后续需要按 ModelCall 计量，新增独立模型调用用量表并以 `(execution_id, sequence_no)` 唯一；不得在当前聚合表中混用两种粒度。

### 数据最小化

Span、Metric 和普通日志默认只记录标识、类型、计数、耗时、状态与稳定哈希。以下内容不得作为普通遥测属性导出：

- Task Capability、MCP Token、模型密钥和认证头。
- 完整 Prompt、用户指令、文档正文、工具参数与工具结果。
- 外部 MCP 的秘密配置或带查询凭证的 URL。

Metric 只能使用集中维护的低基数标签白名单，例如稳定的服务、操作、执行模式、血缘类型、状态和受控错误分类；禁止使用 `task.id`、`run.id`、`execution.id`、用户/空间/文档 ID、自由文本、URL、Prompt 哈希或任意工具名作为 Metric 标签。Trace 使用独立属性白名单，可以携带排障所需的 `run.id` 与 `execution.id`，但仍不得携带正文、指令、工具参数、凭证或未经审查的动态属性。

Phase 2 必须提供统一的结构化日志脱敏处理器，对认证头、Token/secret/key/password 类字段及带凭证查询参数做集中遮蔽；仅靠开发约定或文档提醒不能作为验收完成条件。业务审计中确需保留的非秘密上下文仍受访问权限和保留策略约束。

需要问题诊断时仍通过受权限保护的业务查询读取脱敏审计和快照，不能提高全局遥测敏感度。

## 当前差距与实施顺序

1. RabbitMQ 当前只传 `taskId`，没有传播 Trace Context。
2. A2A 与回调尚未统一 W3C 上下文。
3. `token_usage_detail` 有 `trace_id` 但当前转换流程未赋值，也没有 `execution_id` 和唯一幂等键。
4. Token 明细当前在任务完成时写入一次 AgentExecution 聚合记录，字段注释中的“每次 MCP 调用”与真实语义不一致。
5. 业务状态落库和外部遥测导出必须解耦；关闭或故障的 OTel 不得影响任务执行。

Phase 1 先补齐稳定 Run/Execution 关联键和账本幂等条件；Phase 2 再接入 OTel 传播与领域 Span。不得为了提前展示 Trace 而反转这个顺序。

### W3C 异步传播方案

- RabbitMQ 业务消息体继续只保存 `taskId`，避免把基础设施上下文混入领域 DTO。
- 发布端通过 AMQP Message Headers 注入 `traceparent`、可选 `tracestate` 与受控 `baggage`；消费端提取后创建消费 Span。
- A2A 请求和回调通过 HTTP Headers 传播相同 W3C 上下文。
- 定时对账等没有可用父上下文的补偿任务创建新 Trace，并通过稳定的 `run.id`、`execution.id` 关联；只有保存了有效 SpanContext 时才建立 Span Link，不根据数据库中的普通字符串伪造父子关系。
- 自定义 `X-Trace-Id` 在兼容期保留给日志检索，Phase 2 完成后由 OTel trace ID 驱动，不作为第二套独立链路协议。

## 未采用方案

### 只保留 OTel，不再保存模型/工具调用记录

拒绝。采样和保留期会导致审计与复现证据缺失。

### 以 AgentExecution Token 字段直接替代业务账本

拒绝。AgentExecution 是运行时事实，业务账本还需要价格依据、空间聚合、预算和幂等记账语义。
