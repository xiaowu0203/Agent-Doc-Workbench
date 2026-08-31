# 数据库设计说明

> 建表 SQL 见 `backend/auth-service/src/main/resources/db/migration/V1__init.sql` 及后续 V2/V3 增量迁移。原 V1—V16 已合并为唯一的 v0.1 初始化基线，Flyway 由 auth-service 统一托管。
> 本文档与迁移 SQL 同步维护，业务口径变更须同时更新两侧。

## 整体说明

- **项目**：Agent‑Doc‑Workbench v0.1
- **数据库**：MySQL 5.7，单库 `agent_doc_workbench`；统一 **InnoDB + utf8mb4（utf8mb4_unicode_ci）**
- **主键 / 删除**：主键统一雪花 ID（BIGINT）；业务表统一逻辑删除（`deleted` 0/1）；`audit_log` 审计日志**只允许 INSERT，禁止 UPDATE / DELETE，无逻辑删除**
- **关联方式**：表间关联均为**逻辑外键（普通索引）**，不建物理外键约束（与全库既有风格一致）
- **时间口径**：统一 **Asia/Shanghai 东八区自然日**；DB 连接参数 `serverTimezone=Asia/Shanghai`
- **版本迁移**：当前完整基线为 `V1__init.sql`；基线重新执行后视为不可变历史，新增 / 变更一律使用 V2 及更高版本增量脚本

## 表清单速览（V1 基线 24 张，Phase 5 增加 5 张权限相关表）

| 表 | 归属域 | 一句话职责 |
| --- | --- | --- |
| `user` / `oauth2_client` | 认证 | 用户账号；OAuth2 客户端凭证（Agent Client‑Credentials 模式） |
| `platform_role` / `user_platform_role` | 认证 | 平台角色定义及用户平台角色绑定 |
| `space` / `member` | 空间 | 工作空间；成员与角色（所有者 / 编辑者 / 观察者） |
| `permission` / `space_role` / `space_role_permission` | 空间权限 | 权限标识符目录、空间角色及角色权限绑定 |
| `document` / `document_version` / `change_request` | 文档 | 树形文档 + 正式 / 草稿双模式；版本快照；变更审批流 |
| `model` | 模型 | 模型配置（厂商 / model_key / 预估价格 / 加密 API Key） |
| `agent` | Agent | Agent 实例；系统提示词、执行限制与 `model_id` 关联模型 |
| `task` | 任务 | 任务主表；三层 Token 预算熔断 |
| `token_usage_detail` | 统计 | Token 调用明细【真相源】，无条件落库 |
| `token_usage` | 统计 | 历史日聚合表（折线图，截止昨日） |
| `token_daily_snapshot` | 统计 | 当日快照表（今日卡片，仅 UI 展示） |
| `audit_log` | 审计 | 全链路审计，只 INSERT 不可篡改；V6 增加 `task_id` 关联 |
| `agent_execution` | Agent | Agent Service 中的 A2A 执行快照、状态和 Token 用量 |
| `a2a_task_store` | A2A | A2A Task 协议状态及加密载荷持久化 |
| `a2a_push_config` | A2A | A2A Push Notification 配置及加密载荷持久化 |
| `skill` / `skill_version` / `agent_skill` | Skill | Skill 元数据、不可变版本包以及 Agent 当前绑定关系 |
| `mcp_server` / `agent_mcp_binding` | MCP | 空间级外部 MCP 配置以及 Agent 工具白名单绑定 |
| `agent_execution_model_call` / `agent_execution_tool_call` | 执行审计 | 逐轮模型调用与逐次工具调用的脱敏审计记录 |

## 表分工

1. **user / oauth2_client**：用户与 OAuth2 客户端凭证，Agent 使用 Client‑Credentials 模式鉴权。
2. **platform_role / user_platform_role**：平台级角色及用户绑定；当前用于平台超级管理员，不写入 Space 成员关系。
3. **space / member / space_role / space_role_permission**：工作空间、成员角色、角色权限绑定；每个 Space 默认创建 OWNER、EDITOR、VIEWER，只有 OWNER 受保护。
4. **permission**：全局权限标识符目录；权限码由后端协议和迁移脚本固化，空间角色引用已有权限码。
5. **document / document_version / change_request**：树形文档、正式 / 草稿双模式；文档版本快照；Agent 变更审批流。
6. **model**：Agent Service 的模型配置，维护厂商、model_key、展示名、窗口大小、计价单价和加密 API Key（**仅预估，不作为结算依据**）。
7. **agent**：Agent Service 中的 Agent 实例；保存系统提示词、执行限制和配置版本，`model_id` 关联 model 表（逻辑外键）。
8. **task**：Agent 任务主表；三层 Token 预算（任务 / Agent / 空间）全部基于**Token 数量**做熔断；**熔断逻辑完全不依赖任何统计报表表**（计数来源见「开放问题」）。
9. **token_usage_detail【真相源】**：每次 MCP 调用插入一条原始明细，保存 input/output/cached token、调用时间、model_id、预估费用；所有统计、重算全部以此表为准。
10. **token_usage【历史日聚合表】**：每日凌晨定时聚合**昨日以及更早完整自然日**；用于前端 7/30 天消耗折线图；**不包含今日数据**；联合唯一索引 `dimension+obj_id+usage_date`。
11. **token_daily_snapshot【当日快照表】**：存储当日统计快照；支持系统自动快照、用户手动异步触发快照；页面【今日消耗卡片】读取本表最新快照（同 `space_id + snapshot_date` 取 `created_at` 最大一条）；**只做 UI 展示，不用于业务熔断**。
12. **audit_log**：全链路审计记录，不可篡改；任务执行、重试、熔断和失败均可关联任务。
13. **agent_execution**：保存一次 A2A 执行的配置快照、状态、结果摘要和 Token 用量，使用 `workbench_task_id` 保证幂等。
14. **a2a_task_store / a2a_push_config**：由 agent-service 使用 AES-GCM 加密保存官方 A2A SDK 的任务和推送配置载荷，服务重启后可恢复协议状态。
15. **skill / skill_version / agent_skill**：管理 Skill 稳定标识、不可变版本、对象存储信息和 Agent 绑定；执行时冻结实际候选与选择结果。
16. **mcp_server / agent_mcp_binding**：管理空间级外部 MCP、加密认证令牌、配置版本和 Agent 绑定白名单。
17. **agent_execution_model_call / agent_execution_tool_call**：按执行内序号保存模型和工具调用状态、耗时、哈希与字节数，不保存 Prompt、响应、工具参数、结果或密钥明文。

## model 表对业务数据渲染的影响

新增 model 表后，业务渲染从「无模型概念」变为「处处关联模型」：

- **Agent 管理**：`agent.model_id` → Agent 列表 / 表单 / 详情渲染关联模型（厂商 + display_name），不再显示裸 ID；创建 Agent 需先选模型
- **任务执行**：任务通过 A2A 下发到 agent-service，由 Agent Runtime 使用 `model.model_key` 调用模型；Workbench 数据与变更操作通过 MCP 访问 task-service。
- **Token 明细**：`token_usage_detail.model_id` JOIN `model` 渲染"厂商 / 模型名"（可附单价）；聚合表与快照表**不含模型维度**，折线图 / 今日卡片为汇总口径
- **模型管理页**：model 表支撑模型 CRUD（新增 / 启用禁用 / 预估单价录入）
- **设计边界**：聚合维度 `dimension` 仅 空间 / 文档 / 任务 / Agent 四维，**无模型维度**；若需"按模型统计消耗"（如按模型折线、模型排行）需 v0.2 扩展聚合与快照结构

## 关键业务约束

1. Agent 的系统配置和模型 API Key 由 agent-service 在应用层 AES-GCM 加密存储，数据库禁止明文保存密钥；A2A Task/Push Config 载荷同样加密保存。
2. model 表保存模型元数据和加密 API Key，不管控供应商真实可用性；`model_key` 是供应商模型标识。
3. 所有人民币金额全部为**预估参考**，真实消费以 MCP 服务商账单为准；熔断只使用 Token 数量，绝对不使用预估金额。
4. `token_usage_detail` 明细是唯一真相源；聚合表 `token_usage` 损坏可通过明细全量重建。
5. 折线图只读取 `token_usage`，数据截止**前一天**；今日消耗从 `token_daily_snapshot` 快照获取；今日数据不参与折线图，避免图表抖动。
6. v0.1 暂不实现 `model_pricing` 计价子表，峰谷 / 缓存复杂计价放到 v0.2 迭代。
7. Skill 版本发布后不可覆盖；Agent 执行只使用准备阶段冻结的 Skill、Prompt、工具和 MCP 快照。
8. 外部 MCP Bearer Token 使用 AES-GCM 加密保存，执行快照和调用审计不得持久化秘密明文。
9. 模型与工具调用审计采用追加写入和稳定哈希，用于执行核验，不承担正文恢复职责。

## 熔断与 Token 计数策略（已定稿）

- **任务级熔断**：任务执行中**本地累计** Token 用量，实时更新 `task.tokens_used` 做熔断判断；任务结束 / 异常时用 `token_usage_detail` 明细 `SUM` **对账补偿修正** `task.tokens_used`。
- **空间级 / Agent 级熔断**：v0.1 直接实时 `SUM(token_usage_detail)` 判断；v0.2 引入 Redis 计数器优化性能。
- **明细落库**：`token_usage_detail` **无条件落库**（每次 MCP 调用一条），作为唯一真相源，聚合 / 对账 / 重跑全部以它为准。
- 熔断只使用 Token 数量，不使用预估金额（见「关键业务约束」）。

## 定时 & 异步任务说明（已定稿）

1. **凌晨历史日聚合**：Spring `@Scheduled` + Redisson 分布式锁（防多实例重复执行）从 `token_usage_detail` 聚合**昨日完整日期**写入 `token_usage`，不处理当日；**聚合逻辑必须幂等**（`uk_dim_obj_date` 唯一键，用「先删该日期再插入」或 `INSERT ... ON DUPLICATE KEY UPDATE`，禁止裸 INSERT）；v0.2 集群部署后迁移到 XXL‑Job 调度。聚合任务跨零点时**顺带生成 `token_daily_snapshot` 收尾快照**。
2. **今日快照（懒加载异步触发，不做全局定时轮询）**：访问今日消耗页面时异步触发快照生成，同一 `space_id` 节流最小间隔 **3min**；用户可手动刷新快照。
3. **后台管理**：支持重跑指定日期聚合，从明细重建 `token_usage`。

## 数据流

- **任务执行 / 明细流**：任务经 A2A 下发 → agent-service 调用模型并通过 MCP 访问 Workbench → 每次模型调用**无条件插入** `token_usage_detail` → 任务级 `task.tokens_used` 本地累计熔断，任务结束 / 异常用明细 SUM 对账补偿 → 空间 / Agent 级熔断实时 SUM 明细。
- **统计展示流**：`token_usage_detail` → 每日凌晨聚合 → `token_usage`（折线图，昨日及以前）；访问今日页面懒加载 / 手动刷新 → `token_daily_snapshot`（今日卡片）；聚合表损坏可由明细全量重建。

## 页面表现

- 📈 消耗折线图：数据源 `token_usage`，时间范围 N 天前～昨日，数据稳定无抖动。
- 📊 今日消耗卡片：读取 `token_daily_snapshot` 当天最新一条（`space_id + snapshot_date` 取 `created_at` 最大）；**无快照时提示用户手动刷新**；提示"今日为快照数据，折线统计截止昨日，金额仅为预估"。

## 查询索引

- `token_usage (space_id, usage_date)`：折线图按空间 + 日期范围查询（`idx_tu_space_date`）。
- `token_daily_snapshot (space_id, snapshot_date, created_at)`：今日卡片取最新快照（`idx_snap_space_date_created`）。
- MySQL 5.7 无降序索引，最新快照查询靠 `ORDER BY created_at DESC` 反向扫描。
- `token_usage_detail` 增长较快，v0.2 需定归档 / 冷备策略（预留）。

## 迁移版本演进

| 版本 | 内容 |
| --- | --- |
| `V1__init.sql` | 合并原 V1—V16 的完整 v0.1 数据库基线，包括用户、空间、文档、任务、Agent、模型、Token 统计、Skill、外部 MCP 与执行审计结构 |

所有环境重建并执行该基线后，`V1__init.sql` 不再修改；后续变更一律新增 V2 及更高版本迁移。
