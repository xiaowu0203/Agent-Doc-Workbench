# Agent 多 MCP 技术架构设计

> 状态：后端已实现，待 Phase 6 前端接入与真实外部 MCP 端到端验证

## 1. 目标与边界

每个 Agent 始终保留 Workbench 内置 MCP，并可通过 `externalMcpEnabled` 开关启用零到十个外部 MCP。外部 MCP 配置归属空间，可被同空间多个 Agent 复用。本阶段仅支持 Streamable HTTP、`NONE` 和 `BEARER` 认证，不支持服务端启动本地 stdio 进程。

## 2. 数据模型

- `agent.external_mcp_enabled`：Agent 级总开关，创建和更新时必填。
- `mcp_server`：保存 `serverKey`、展示名、HTTPS 地址、认证类型、加密令牌、配置版本和状态。
- `agent_mcp_binding`：Agent 与 MCP Server 多对多绑定，包含远端原始工具名白名单。
- `agent_execution.external_mcp_snapshot_json`：保存 Server ID、`serverKey`、配置版本、地址哈希、认证类型和绑定白名单，不保存地址与令牌明文。
- `agent_execution_tool_call`：增加 `tool_source_key` 和 `mcp_server_id`，定位工具来源。
- `agent_execution_model_call`：按轮记录实际模型参数、消息/响应哈希、字节数与状态，不保存逐轮正文或任何密钥。

MCP、逐轮模型审计与 Skill 激活描述非空约束均已纳入 `V1__init.sql` 完整基线。当前没有 Agent 旧数据，因此总开关使用 `NOT NULL` 且不提供数据库默认值。
原有 `agent.mcp_config` 继续保留但不再读取，避免在未单独确认数据迁移与删除策略前执行破坏性变更；新的唯一生效来源是 `mcp_server` 与 `agent_mcp_binding`。

## 3. API

```text
POST   /api/agent/mcp-servers
POST   /api/agent/mcp-servers/search
GET    /api/agent/mcp-servers/{id}
PUT    /api/agent/mcp-servers/{id}
DELETE /api/agent/mcp-servers/{id}

GET /api/agent/agents/{agentId}/mcp-bindings
PUT /api/agent/agents/{agentId}/mcp-bindings
```

认证令牌只写不回显。更新时空令牌表示保留已有密文；切换到 `NONE` 会清除密文。`serverKey` 创建后不可修改，因为它参与模型工具名和 Skill 权限契约。

## 4. 工具命名和权限

Workbench MCP 保留原始工具名。外部 MCP 暴露给模型的名称固定为：

```text
{serverKey}__{remoteToolName}
```

例如 `github__create_issue`。Skill 的 `allowed-tools` 和 Agent 工具白名单必须使用该规范名称；绑定白名单保存 MCP Server 返回的原始工具名。最终可见权限是 Skill、Agent 和绑定三层白名单的交集。所有来源的最终工具名必须全局唯一。

## 5. 执行流程

1. 短事务锁定 Agent，批量冻结启用的绑定和 MCP 配置。
2. 执行记录落库前写入脱敏 MCP 快照。
3. Runtime 打开 Workbench MCP。
4. 仅对本次存在有效工具权限的外部 MCP 做运行时地址安全校验，并使用虚拟线程并行握手。
5. 合并内置 MCP、外部 MCP 和 Skill 本地工具，校验名称唯一后持久化实际工具定义。
6. 每轮模型调用保存实际参数和脱敏消息/响应摘要；工具调用审计记录来源键和 MCP Server ID。
7. 执行结束按逆序关闭全部会话；任一外部 MCP 初始化失败则本次执行失败，不静默降级。

## 6. 安全约束

- 外部地址必须为公网 HTTPS，禁止用户信息、查询参数和 URL 片段。
- 创建、更新和每次连接前解析 DNS，拒绝环回、私网、链路本地、任意地址和组播地址。
- MCP Server 必须属于 Agent 所在空间且处于启用状态。
- Bearer Token 使用现有 Agent AES-GCM 配置密钥加密，VO、日志和执行快照不得回显。
- 外部配置只能来自数据库冻结快照，不能从任务请求动态注入外部地址。
- 当前采用 fail-fast：任何被选中外部 MCP 握手失败都会终止执行，保证工具集合与执行快照一致。

## 7. Phase 6 前端规范

Agent 创建和编辑页提供“启用外部 MCP”开关。开启后展示当前空间 MCP 配置多选列表及每项工具白名单。MCP 配置页展示连接状态、认证是否已配置和配置版本，认证令牌仅提供重新填写入口，不显示原值。
