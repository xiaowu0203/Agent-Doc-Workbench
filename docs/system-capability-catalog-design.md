# 系统能力库与空间安装架构设计

> 状态：实施中；后端阶段一、二代码已完成，待集成环境验收
> 适用范围：系统 Skill 库、Agent 模板、MCP 模板、空间安装和统一系统能力中心  
> 核心原则：系统级可复用不等于跨空间直接执行；任务执行始终以空间实例、空间权限和不可变快照为边界。

## 1. 背景与目标

当前 Agent、Skill 和 MCP 均强制归属一个 Space。隔离清晰，但相同能力需要在不同空间重复创建、上传和配置。

本设计增加平台维护的系统能力目录，同时保留空间自定义能力：

- 平台超级管理员维护系统能力。
- 空间管理员显式安装、升级、停用和卸载系统能力。
- 空间成员只能使用当前空间已安装且启用的能力。
- 系统能力不能扩大用户、Agent 或任务对空间文档的权限。
- 系统能力更新不会静默改变既有空间的运行行为。
- MCP 凭证默认由空间独立保存，不跨空间共享。
- 历史任务继续通过执行快照审计和复现。

## 2. 领域模型

### 2.1 Skill：共享不可变版本，空间固定安装版本

`skill` 同时承载系统 Skill 与空间 Skill，通过作用域区分：

- `scope_type=SYSTEM`：`space_id` 为空，由平台超级管理员管理。
- `scope_type=SPACE`：`space_id` 非空，沿用当前空间权限管理。

新增 `space_skill_installation`：

| 字段 | 说明 |
| --- | --- |
| `space_id` | 安装目标空间 |
| `skill_id` | 系统 Skill ID |
| `skill_version_id` | 当前固定的已发布版本 |
| `enabled` | 当前空间是否启用 |
| `installed_by` | 安装人 |
| `created_at/updated_at` | 安装与更新时间 |

约束：

- 只能安装 `SYSTEM`、`ACTIVE` 的 Skill。
- 只能固定该 Skill 下 `PUBLISHED` 的版本。
- 同一空间同一系统 Skill 只能安装一次。
- 升级必须显式选择新版本，不能自动跟随 latest。
- 空间 Agent 可以绑定同空间 Skill，或当前空间已安装的系统 Skill 版本。
- 卸载前必须确认没有空间 Agent 绑定；不做级联删除。

### 2.2 Agent：系统模板，安装生成空间实例

新增独立的 `agent_template` 与 `agent_template_version`，不让任务直接引用模板。

模板版本保存：

- 名称、描述、系统提示词。
- 默认模型 ID、Skill 选择模式和 Router 模型。
- 默认 Token 预算、工具白名单、最大迭代次数和超时。
- 引用的系统 Skill 版本和 MCP 模板。
- 不保存空间文档范围，不保存空间 MCP 凭证。

现有 `agent` 继续作为空间执行实例，并新增来源字段：

| 字段 | 说明 |
| --- | --- |
| `template_id` | 来源模板，可空；空间自定义 Agent 为空 |
| `template_version_id` | 安装时采用的模板版本，可空 |

安装过程在一个事务中创建空间 Agent，并复制模板配置。空间管理员可以设置模型、预算、文档范围等空间配置。安装后 Agent 独立运行，模板发布新版本时只提示可升级，不静默覆盖。

升级过程必须提供差异预览；后端根据旧模板版本和当前实例判断空间是否修改过字段。首版不做复杂三方自动合并，发生冲突时要求空间管理员确认最终配置。

### 2.3 MCP：共享模板，空间连接独立持有凭证

新增 `mcp_template`：

- `server_key`、展示名称、说明。
- 默认公网 HTTPS Endpoint。
- 认证类型与 Query 参数名。
- 配置版本、状态。
- 最近一次平台级工具发现结果可作为目录展示信息。
- 不保存供普通空间共同使用的认证凭证。

现有 `mcp_server` 继续作为空间连接，并新增：

| 字段 | 说明 |
| --- | --- |
| `template_id` | 来源系统模板，可空 |
| `template_version` | 安装时采用的模板配置版本，可空 |

安装 MCP 模板时，空间管理员必须按模板认证类型填写本空间凭证；`NONE` 无需凭证。安装后仍执行当前的地址校验、加密存储、连接测试、工具发现和 Agent 绑定流程。

平台托管的共享凭证不纳入首版。后续如增加，必须另行设计空间授权范围、平台配额、密钥轮换和停用影响。

## 3. 权限与可见性

### 3.1 系统能力

- 创建、修改、发布、停用系统 Skill、Agent 模板和 MCP 模板：仅 `PLATFORM_SUPER_ADMIN`。
- 登录用户可读取处于可发布状态的系统能力目录；首版不对匿名用户开放。
- 系统能力的管理审计使用平台审计语义，`space_id` 为空。

### 3.2 空间安装

- 安装、升级、启用、停用、卸载 Skill：复用 `skill:manage`。
- 安装 Agent 模板：复用 `agent:manage`，安装后的绑定继续要求 `agent:bind-skill` / `agent:bind-mcp`。
- 安装 MCP 模板：复用 `mcp:manage`。
- 使用能力仍需对应空间读权限和任务创建权限。

系统目录可见不代表可执行。任务创建接口只返回当前空间的 Agent 实例，不返回 `agent_template`。

## 4. 数据库迁移

新增 `V19__system_capability_catalog.sql`，不得修改已执行的 V1～V18：

1. `skill.space_id` 改为可空，增加 `scope_type`，现有数据回填为 `SPACE`。
2. 调整 Skill 唯一约束，使系统和空间名称分别唯一。
3. 新增 `space_skill_installation`。
4. 新增 `agent_template`、`agent_template_version` 及模板 Skill/MCP 引用表。
5. `agent` 增加可空的模板来源字段。
6. 新增 `mcp_template`。
7. `mcp_server` 增加可空的模板来源与版本字段。
8. 为目录查询、空间安装查询和升级检查建立组合索引。

迁移只回填结构和来源标识，不自动把既有空间资源提升为系统资源。

## 5. API 资源

系统能力使用独立资源路径，避免现有空间 API 出现大量作用域分支。

```text
# 系统 Skill 目录与安装
POST   /api/agent/system-skills/search
POST   /api/agent/system-skills
PUT    /api/agent/system-skills/{id}
POST   /api/agent/system-skills/{id}/versions
POST   /api/agent/system-skill-versions/{id}/publish
GET    /api/agent/spaces/{spaceId}/skill-installations
POST   /api/agent/spaces/{spaceId}/skill-installations
PUT    /api/agent/spaces/{spaceId}/skill-installations/{id}
DELETE /api/agent/spaces/{spaceId}/skill-installations/{id}

# Agent 模板与空间实例安装
POST   /api/agent/agent-templates/search
POST   /api/agent/agent-templates
PUT    /api/agent/agent-templates/{id}
POST   /api/agent/agent-templates/{id}/versions
POST   /api/agent/agent-template-versions/{id}/publish
POST   /api/agent/spaces/{spaceId}/agent-installations
POST   /api/agent/agents/{agentId}/template-upgrades

# MCP 模板与空间连接安装
POST   /api/agent/mcp-templates/search
POST   /api/agent/mcp-templates
PUT    /api/agent/mcp-templates/{id}
POST   /api/agent/spaces/{spaceId}/mcp-installations
```

首版接口可以按实施阶段逐步开放，但资源语义保持稳定。

## 6. 执行链与快照

- Task 仍只保存空间 `agent.id`，现有“Agent 与文档同空间”校验不变。
- Agent 模板只参与安装和升级，不进入运行时查询。
- 系统 Skill 通过 `space_skill_installation` 获得空间使用权；执行准备阶段同时验证安装状态和固定版本。
- MCP Runtime 仍只读取空间 `mcp_server`，不直接读取 `mcp_template`。
- Agent 执行快照继续保存实际 Agent 配置、SkillVersion、MCP 连接配置版本和工具定义。
- 快照可增加模板/系统 Skill 来源 ID 和版本，用于追踪，但不能以模板当前状态替代实际执行快照。
- Token、工具调用和预算全部计入任务所属空间。

## 7. 生命周期规则

- 系统能力已被安装后禁止物理删除，只允许停用或废弃。
- 停用阻止新的空间安装；已安装实例是否继续可用由操作时明确选择，首版默认不影响已安装实例。
- Skill 已安装版本保持可读，不能因目录下架而删除对象。
- Agent 模板和 MCP 模板升级必须显式触发。
- 空间卸载只删除安装关系或空间实例，不删除系统定义。
- 系统与空间 `server_key` 在一个 Agent 的最终工具集合内仍必须唯一，冲突时拒绝绑定，不做隐式覆盖。

## 8. 前端信息架构

后端四个阶段完成后再进入前端：

1. Skill 页面增加“已安装 / 系统 Skill 库 / 空间自定义”。
2. Agent 页面增加“空间 Agent / 系统模板”，模板通过安装向导生成空间 Agent。
3. MCP 页面增加“空间连接 / 系统模板”，安装时填写空间凭证并执行连接测试。
4. 平台管理区域增加统一“系统能力中心”，集中维护系统 Skill、Agent 模板和 MCP 模板。

任务创建页保持现有交互，只展示当前空间可执行的 Agent 实例。

## 9. 实施顺序与完成条件

### 阶段一：系统 Skill 库与空间安装

- V19 中完成 Skill 作用域和安装关系。
- 系统 Skill 管理、版本发布、空间安装/升级/卸载 API 完成。
- Agent 绑定和执行快照支持已安装系统 Skill。
- 原空间 Skill API 和历史数据回归通过。

### 阶段二：Agent 模板与空间实例

- 模板及不可变版本完成。
- 安装事务能够生成可执行空间 Agent，并绑定允许的 Skill/MCP。
- 任务创建和 Runtime 不直接依赖模板。
- 模板升级具备明确冲突处理和审计。

### 阶段三：MCP 模板与空间独立凭证

- 系统模板不持久化空间凭证明文或密文。
- 安装生成独立 `mcp_server`，凭证加密、地址校验、连接测试与工具发现复用现有实现。
- Agent 仍只绑定空间 MCP 连接。

### 阶段四：统一系统能力中心

- 完成系统目录聚合查询和平台统计。
- 后端四阶段验收后再开发前端页面。

## 10. 测试重点

- 平台管理员与普通用户的系统管理权限隔离。
- 空间 OWNER/EDITOR/VIEWER 的安装和读取边界。
- 未安装系统 Skill 不能绑定或执行。
- 已安装 Skill 固定版本，不随系统 latest 静默变化。
- Agent 模板安装后不能读取其他空间文档。
- MCP 模板安装后各空间凭证相互隔离且不回显。
- 系统能力停用、空间卸载、名称冲突和工具名冲突。
- 历史任务快照在模板升级或下架后仍可读取。
- 原有空间 Agent、Skill、MCP 全链路兼容。
