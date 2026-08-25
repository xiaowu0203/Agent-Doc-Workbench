# Phase 4 衔接文档：Skill 管理（2026-08-26）

> 用途：Phase 3 合并 `main` 后，作为 `phase-4` 分支和后续协作者的启动基线。
>
> 上游依据：`docs/PHASE3-HANDOFF.md`、`docs/agent-server-a2a-mcp-design.md`、`docs/development-plan.md`。

## 一、阶段目标

Phase 4 在现有 Agent、A2A、MCP 和任务快照能力之上，增加可复用、可版本化的业务 Skill 包：

1. Skill 不是一段简单提示词，而是包含 `SKILL.md` 和资源目录的完整文件包。
2. Skill 可以创建版本、发布、停用，并绑定到 Agent。
3. Agent 执行任务时固定使用已发布的 Skill 版本，并把 Skill 快照写入执行记录。
4. 两种 Agent Runtime 使用同一份 Skill 解析、提示词组合和工具约束逻辑。

Phase 4 不实现 Phase 5 的细粒度 RBAC，也不允许 Skill 脚本在 Agent 进程内任意执行。

## 二、Phase 3 已提供的基础

- `agent-service` 独立持有 Agent、Model、Prompt、AgentExecution 和 Runtime。
- `SpringAiAgentExecutionRuntime` 与 `SpringAiAlibabaAgentExecutionRuntime` 可配置切换。
- 两种 Runtime 共用 `TaskScopedMcpTools`，MCP 调用携带任务 Capability。
- `AgentExecutionApplicationService` 已负责加载 Agent/Model、创建执行快照、调用 Runtime 和发送 A2A 事件。
- `agent.config_version`、`agent_execution.system_prompt_snapshot`、`model_snapshot` 和 `prompt_hash` 已具备快照基础。
- Gateway 已将 `/api/agent/**` 转发到 `agent-service`。
- MinIO 已在 `docker-compose.yml` 中提供，但后端尚未接入 MinIO SDK 和对象存储抽象。
- Flyway 已存在 V1-V12；Phase 4 数据库变更从 V13 开始，禁止修改已执行迁移。

## 三、Skill 包约定

上传包解压、归一化后，根目录必须包含 `SKILL.md`：

```text
document-review/
├── SKILL.md              # 必须：名称、描述、使用说明和执行约束
├── references/           # 可选：业务规范、知识材料
├── assets/               # 可选：模板、示例和静态资源
└── scripts/              # 可选：脚本文件；Phase 4 只存储，不直接执行
```

约束：

- `SKILL.md` 使用 UTF-8 Markdown；名称和描述可由 YAML Front Matter 解析。
- 发布版本不可覆盖，只能创建新版本；任务始终引用明确的已发布版本。
- `references/` 只允许读取当前任务快照内的文本资源，并限制单文件与累计大小。
- `assets/` 在 Phase 4 只参与存储、下载和导出，不自动注入模型上下文。
- `scripts/` 在 Phase 4 不执行；后续如需执行，必须通过受控 MCP 工具或独立沙箱。

## 四、模块职责

### agent-service

- Skill 元数据、版本、Agent 绑定关系和对象存储接入。
- Skill 包上传、校验、发布、下载、停用和版本查询。
- 创建任务执行记录时解析 Agent 绑定的 Skill 版本并生成快照。
- 将 Skill 指令组合进系统提示词，向 Runtime 提供受控资源读取能力。

### task-service

- Workbench MCP Server 和任务 Capability 逻辑保持不变。
- MCP 工具仍是实际业务动作执行入口，Skill 不能绕过任务、空间、文档和 action 校验。

### document-service / auth-service

- Phase 4 继续复用现有空间角色校验和 JWT 身份链路。
- 细粒度权限标识符、角色权限绑定和 `@RequirePermission` 改造留到 Phase 5。

## 五、建议数据模型

### `skill`

- Skill 稳定身份：`id`、`space_id`、`name`、`description`、`status`、`created_by`。
- 常规业务表，继承逻辑删除与审计时间字段。

### `skill_version`

- 不可变版本：`skill_id`、`version_no`、`status`、`storage_key`、`sha256`、`package_size`。
- 保存解析后的元数据、允许的 MCP 工具名、发布时间和发布人。
- 已发布版本禁止修改包内容和哈希。

### `agent_skill`

- Agent 与 Skill 版本的绑定：`agent_id`、`skill_id`、`skill_version_id`、`enabled`。
- v0.1 不增加复杂优先级；提示词按稳定顺序组合，避免结果随查询顺序变化。
- 绑定、解绑或切换版本时递增 `agent.config_version`。

### `agent_execution`

- 新增 Skill 快照字段，至少保存 Skill ID、版本号、包哈希、工具白名单和最终指令哈希。
- 快照用于审计与复现，不在任务重试时重新解析“最新版本”。

## 六、对象存储

- 建议 Bucket：`agent-doc-workbench-skills`。
- 建议对象键：`skills/{spaceId}/{skillId}/{versionNo}/{sha256}.zip`。
- MySQL 只保存元数据和对象键，不保存完整压缩包或大段资源内容。
- 上传流程必须校验 Zip Slip、绝对路径、`..`、符号链接、文件数量、解压总大小和压缩比。
- 对象写入成功但数据库事务失败时需要清理孤儿对象；数据库发布成功前不得向 Runtime 暴露该版本。
- MinIO 连接信息使用环境变量，凭证不得写入仓库或业务表明文。

## 七、运行时接入点

推荐流程：

```text
AgentExecutionApplicationService
  → 加载 Agent / Model
  → SkillSnapshotService 按 agentId + configVersion 固定 Skill 版本
  → PromptService 组合平台提示词、Agent 提示词和 Skill 指令
  → 写入 AgentExecution Skill / Prompt 快照
  → AgentExecutionRuntime 执行
  → 本地 Skill 资源工具 + TaskScopedMcpTools
```

需要保证：

- Skill 解析和提示词组合位于 Runtime 之外或抽成共用组件，两种 Runtime 不得分别实现一套。
- 建议提供只读的本地工具 `skill_list_resources`、`skill_read_resource`，只允许访问当前执行快照中的 `references/`。
- Skill 声明的 MCP 工具白名单用于减少模型可见工具；真正的安全边界仍是 task-service 的 Capability 校验。
- Prompt 组合顺序固定为：平台规则 → Agent 系统提示词 → Skill 指令；必须限制总字符数和 Token 占用。
- Skill 绑定变化必须递增 Agent 配置版本，模型缓存和 Runtime 选择逻辑不得受影响。

## 八、建议 API

- `POST /api/agent/skills`：创建 Skill 元数据。
- `GET /api/agent/skills`：按空间分页查询。
- `GET /api/agent/skills/{skillId}`：查询 Skill 详情。
- `PUT /api/agent/skills/{skillId}`：修改未发布的展示信息。
- `POST /api/agent/skills/{skillId}/versions`：上传新版本包。
- `GET /api/agent/skills/{skillId}/versions`：查询版本列表。
- `POST /api/agent/skills/{skillId}/versions/{versionId}/publish`：发布不可变版本。
- `POST /api/agent/skills/{skillId}/disable`：停用 Skill，禁止新绑定和新任务使用。
- `PUT /api/agent/agents/{agentId}/skills`：整体替换 Agent 的 Skill 版本绑定。
- `GET /api/agent/agents/{agentId}/skills`：查询 Agent 当前绑定。

上传、发布、停用和绑定操作在 Phase 4 继续要求当前用户具备对应空间的 OWNER 权限；Phase 5 再替换为 `skill:manage` 等权限标识符。

## 九、开发顺序与验收

1. **迁移与实体**：从 V13 新增 Skill 表和执行快照字段。  
   验证：Flyway 可从已有 V12 正常升级，唯一索引和版本不可变约束生效。
2. **包校验与对象存储**：接入 MinIO、上传下载、哈希和安全解压校验。  
   验证：合法包成功；路径穿越、超限、缺少 `SKILL.md` 和非法编码被拒绝。
3. **Skill 生命周期 API**：创建、上传版本、发布、停用、查询。  
   验证：已发布版本不可覆盖，停用后不可创建新绑定。
4. **Agent 绑定**：绑定明确版本并递增 `agent.config_version`。  
   验证：跨空间绑定、未发布版本和已停用 Skill 被拒绝。
5. **执行快照与 Runtime**：统一组合提示词、提供资源只读工具、限制 MCP 工具可见范围。  
   验证：自定义 Runtime 和 Alibaba Runtime 使用相同 Skill 版本、提示词哈希和工具集合。
6. **审计与测试**：记录上传、发布、停用、绑定和执行快照。  
   验证：核心单元测试、上下文测试和 Maven 模块测试通过。

Phase 4 最终演示链路：

```text
创建 Skill → 上传目录包 → 发布版本 → Agent 绑定版本
→ 创建任务 → 固定 Skill 快照 → Runtime 加载 Skill
→ 读取受控 reference → 调用允许的 Workbench MCP 工具 → 完成任务
```

## 十、阶段边界与后续衔接

Phase 4 不包含：

- 自定义角色、角色绑定权限标识符和接口权限注解迁移（Phase 5）。
- 前端 Skill 管理页面（Phase 6；Phase 4 先交付后端 API）。
- Skill 脚本任意执行、依赖安装和网络访问；需要单独的沙箱安全设计。
- 全站前后端 E2E（Phase 7）。

向 Phase 5 交接时需要提供：Skill 管理接口清单、现有 OWNER 校验位置、建议权限标识符（至少 `skill:read`、`skill:manage`、`agent:bind_skill`）以及覆盖这些入口的测试清单。

