# 架构决策记录

本目录记录已经采纳、会长期影响代码和数据契约的架构决策。ADR 解释“为什么这样设计”，实现细节仍以对应设计文档、数据库迁移和代码为准。

## 状态说明

- `提议`：尚未形成约束，可以继续讨论。
- `已采纳`：后续实现必须遵守；变更时新增 ADR 替代，不直接改写原决策历史。
- `已替代`：由更新的 ADR 接管，并保留原文作为决策历史。
- `已废弃`：不再适用，但不删除历史记录。

## 决策索引

| ADR | 状态 | 决策 |
| --- | --- | --- |
| [ADR-0001](0001-run-compatible-execution-model.md) | 已采纳 | 不新增独立 Run 表，以 Task 与 AgentExecution 组成一次 Run-compatible Execution |
| [ADR-0002](0002-replay-side-effect-isolation.md) | 已采纳 | Replay 强制隔离副作用，写工具只捕获候选产物 |
| [ADR-0003](0003-telemetry-audit-ledger-boundaries.md) | 已采纳 | OTel、执行快照、业务审计和 Token 账本各自保持独立真相边界 |
| [ADR-0004](0004-execution-snapshot-v3-canonicalization.md) | 已采纳 | Execution Snapshot v3 使用稳定 canonical envelope、UTF-8 排序和不可变 schema |
| [ADR-0005](0005-offline-experiment-contract.md) | 已采纳 | Offline Experiment 使用冻结 manifest 与 Prompt 候选配置完成隔离的 baseline/candidate 对比 |
