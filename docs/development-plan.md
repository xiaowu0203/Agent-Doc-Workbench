# 开发路线图（v0.1 → 开源发布）

> 基于《Agent‑Doc‑Workbench 项目完整开发规划文档》与技术栈定稿（docs/tech/）
>
> 更新时间：2026‑08‑26・状态：Phase 0 / 1 / 2 / 3 已完成，Phase 4 Skill 管理待启动

## 总体节奏

```plaintext
Phase 0 工程基建 → Phase 1 后端地基 → Phase 2 文档核心 → Phase 3 Agent 与任务
→ Phase 4 Skill 管理 → Phase 5 细粒度权限 → Phase 6 前端
→ Phase 7 闭环联调 → Phase 8 开源发布准备
```

估算：整体约 6-8 周，Phase 4-8 约 3-5 周（单人兼职）；每阶段产出可独立验证、交付。

------

## Phase 0：工程基建（1‑2 天）

**目标**：搭建可复现的开发环境与仓库骨架

-  初始化 Git 仓库 + `.gitignore` + Apache‑2.0 LICENSE + 根 README
-  `docker‑compose.yml` 本地一键启动：MySQL 5.7 / Redis 5.0.14.1 / RabbitMQ 3‑management / MinIO / Nacos 3.2.2
-  后端 Maven 多模块骨架：`common` 聚合工程（common-core 与技术 starter） + gateway-service / auth-service / document-service / task-service / agent-service
-  前端脚手架：Vite + Vue 3 + TypeScript (strict) + Pinia + Vue Router + Element Plus + ESLint + Prettier + Husky + Vitest
-  `.env.example`、开发环境变量模板

**验收**：Compose 配置可解析并支持一键启动全部中间件；前后端均可启动并展示默认页；前端 Git 提交钩子已配置。

## Phase 1：后端地基（3‑5 天）

**状态**：已完成（2026‑08‑22，已合并入 main）——common 拆分 5 子模块（common‑core + web/springdoc/mybatis‑plus/redis 四个 starter）、auth 鉴权闭环（注册/登录/刷新/登出/me + JWT RS256 + JWKS）、gateway 路由/鉴权/限流（自定义 ProjectRedisRateLimiter）/OpenAPI 聚合、14 张表（含 model 与 Token 统计三表架构）、代码规范整改（pojo 分层 / enums・annotation・constant 包 / @Schema / PageParam / 类转换收敛实体类）；后端 11 模块编译 + 全部测试通过，端到端实测（注册→登录→me→刷新→登出、JWKS、OpenAPI 聚合、限流 429）通过。交接见 `docs/PHASE1-HANDOFF.md`。

**目标**：公共能力与鉴权闭环

-  `common` 模块：统一响应体、全局异常、雪花 ID、上下文工具、鉴权工具；拆分 `common-security-spring-boot-starter` 统一承载 JWT Resource Server 与任务能力令牌验签
-  数据库设计定稿：14 张表——user / oauth2_client / space / member / document / document_version / change_request / agent / task / model / token_usage / token_usage_detail / token_daily_snapshot / audit_log（逻辑删除、雪花 ID、UTF8MB4；Token 统计三表架构与 model 设计见 `docs/database-design.md`）
-  `auth‑service`：注册登录、JWT (RS256) 签发与校验、Spring Authorization Server、Refresh Token 机制
-  `gateway‑service`：路由、JWT 校验、跨域、限流
-  SpringDoc OpenAPI 接入

**验收**：注册→登录→带 Token 调业务接口全链路可用；OpenAPI 文档可访问。

## Phase 2：文档核心（4‑6 天）

**状态**：已完成（2026‑08‑22 开发 + 2026‑08‑23 模型 B 架构收尾）——空间/成员/文档/版本/Diff 数据闭环全部落地并端到端实测通过（20/20）：
- **common**：**模型 B 安全**——`common-security-spring-boot-starter` 装配 Spring Security Resource Server（`CommonSecurityAutoConfiguration`：permitAll 注解驱动 + JWT 解析 + 401 JSON）与任务能力 JWT 验签组件，业务服务配置 `agent-doc.security.jwks-url` 即自行解析身份（无 X-User-* 自定义头、无 UserContext 体系，业务代码经 `AuthUtils` 读 SecurityContext）；`PageParam.toPage()` 落位为 mybatis‑plus starter 的 `PageUtils.toPage()`；新增 `PageVO` 统一分页响应；新增 **common‑feign‑spring‑boot‑starter**（首个跨服务调用，兑现规范 11，Feign 契约即客户端统一入口 + JWT 透传默认装配）
- **document‑service**：空间 CRUD（创建者自动 OWNER）+ 成员角色管理（OWNER/EDITOR/VIEWER，最后一名 OWNER 不可移除）+ 文档 CRUD/树形目录/移动防环/归档回收站 + 草稿/正式双模式 + 版本快照（编辑自动生成/列表/详情/对比/回滚生成新版本不删历史）+ 文档片段读取接口（控 Token）+ 合并端点 `POST /api/document/documents/merge`（服务间调用，SecurityContext 解析身份 + EDITOR 校验，业务异常转 HTTP 状态码供 Feign 识别）
- **task‑service**：ChangeRequest 审批队列（提交/分页查询/通过/拒绝/退回，结构化 changes[] + baseVersion）+ 审批合并闭环（Feign 经网关调 document 应用变更，透传审批人 JWT 保持身份连续，基线版本校验防并发覆盖 40900）+ 3 个新实体（Model / TokenUsageDetail / TokenDailySnapshot）
- **Flyway V4 / V5**：`change_request` 新增 `base_version`；`document.parent_id` 改为可空并将根目录从 `0` 迁移为 `NULL`；本机库已执行 V1‑V5
- 端到端实测：注册→登录→建空间→建文档→编辑版本→回滚→提交变更→审批→合并→冲突 40900→片段读取→归档恢复 全链路通过；网关 401 / 直连无效 token 401（Security 层）/ 合并操作人=审批人（SecurityContext 解析）均验证
- 交接见 `docs/PHASE3-HANDOFF.md`；计划见 `docs/PHASE2-PLAN.md`

**目标**：空间 / 文档 / 版本 / Diff 数据闭环

-  空间、成员角色（所有者 / 编辑者 / 观察者）、树形目录、文档 CRUD
-  草稿 / 正式双文档模式：正式文档禁止 Agent 直改
-  Markdown 存储 + 版本快照（合并变更自动生成版本）+ 一键回滚
-  ChangeRequest 模型与审批队列接口（结构化 changes []，防并发覆盖）
-  文档片段读取接口（按需加载、控 Token）

**验收**：REST API 全量文档管理 + 版本回滚演示可用。

## Phase 3：Agent 与任务（5‑7 天，已完成）

**目标**：独立 Agent Server + Workbench MCP Server + A2A 任务协议 + Token 预算熔断

-  `agent-service`：Agent/Model 配置、提示词快照、A2A Server、Spring AI Runtime 和 MCP Client
-  `task‑service`：任务状态机、RabbitMQ 异步消费、A2A Client、A2A 回调/对账和 Workbench MCP Server
-  A2A 负责任务下发、查询、取消和 Push；MCP 负责 Agent 读取 Workbench 数据和提交变更
-  A2A Task/Push Config 协议状态使用 MySQL 持久化，载荷加密保存
-  变更输出 → ChangeRequest 转换：正式文档入审批队列
-  Token 预算：任务级上限 + 空间全局预算 + 熔断 + 四维度用量统计（空间 / 文档 / 任务 / Agent）
-  审计日志：操作主体（人 / Agent）、操作类型、关联任务、不可篡改

**验收**：核心 A2A/MCP 适配、MySQL A2A 持久化和任务状态对账已实现并通过核心测试；完整 LLM、协议互操作和端到端基础设施闭环仍需在部署环境验证。

## Phase 4：Skill 管理（3‑5 天）

**目标**：在已有 Agent / 任务能力之上，提供可复用、可版本化的业务 Skill 包。

- Skill 以可版本化的目录包管理，不限制为单段文本；目录至少包含 `SKILL.md`，可选 `references/`、`assets/` 和受控 `scripts/`。
- MySQL 保存 Skill 元数据、工作空间归属、版本、状态、校验和与绑定关系；MinIO 或文件存储保存 Skill 包本体。
- 支持创建、上传、校验、草稿、发布、停用、版本查看和回滚；Agent 配置保存 `skillId + version`，任务创建时生成不可变 Skill 快照。
- Agent Runtime 在任务启动时加载 Skill 指令和允许的参考资料；Skill 声明的 MCP 工具必须与 Agent、用户和任务 Scope 取权限交集。
- `scripts/` 不允许在 Agent 进程中任意执行；需要执行时通过受控 MCP 工具或隔离沙箱完成，并记录审计。

**验收**：可上传并发布一个包含 `SKILL.md`、参考资料和资源文件的 Skill 包；Agent 能绑定指定版本，任务执行时能加载 Skill 快照并调用受限 MCP 工具。

## Phase 5：细粒度权限（3‑5 天）

**目标**：将当前基于角色的校验升级为“角色绑定权限标识符，用户通过角色获得权限”。

- 保留当前空间成员角色模型，增加权限目录、角色-权限关系和用户/空间成员-角色关系。
- 权限使用稳定标识符，例如 `space:read`、`document:edit`、`agent:manage`、`task:create`、`change_request:approve`、`skill:manage`、`audit:read`。
- 将现有 `OWNER / EDITOR / VIEWER` 映射为默认角色权限集合，保证历史数据和已有接口平滑迁移。
- 接口在认证得到用户身份后，根据资源所属空间加载用户角色，校验角色是否包含目标权限标识符；无权限统一返回 403。
- 增加统一权限授权服务，并按场景使用 Spring Security `@PreAuthorize` 或业务层显式校验；不恢复已删除的自研 `@RequirePermission`。资源归属、空间范围和 Agent 能力令牌仍需继续校验。
- 人类用户 API 使用 RBAC 权限标识符；Agent/MCP 继续使用任务 Capability，不能用用户角色直接替代 Agent 能力范围。

**验收**：权限目录、角色权限绑定、空间成员角色绑定和接口权限校验可用；无权限访问返回 403；已有角色和接口行为兼容。

## Phase 6：前端（5‑7 天）

**目标**：完成核心业务页面，并接入 Skill 和细粒度权限管理。

-  登录页（账号 + OAuth2 入口）
-  工作空间首页（统计卡片、最近文档 / 任务）
-  文档树 + 编辑页（ProseMirror + prosemirror‑markdown 双向转换，正式 / 草稿模式徽标）
-  Agent 配置页（模型、提示词、Skill、MCP 连接、权限、白名单）
-  任务创建页（执行模式、Token 预算、熔断开关）
-  Diff 审批页（diff‑match‑patch + prosemirror‑changeset，增删高亮、批注、接受 / 拒绝 / 退回）
-  版本历史页（时间线、对比、回滚）
-  Token 用量与审计日志页（图表 + 明细表）
-  Skill 管理页（目录包上传、版本、发布、停用、工具白名单）
-  工作空间角色与权限页（角色、权限标识符、成员绑定）

**验收**：原 8 个核心页面加 Skill 管理、角色与权限管理 2 个页面，共 10 页可交互；UI 与效果图一致，Skill 和权限配置可以通过页面完成。

## Phase 7：闭环联调（3‑5 天）

**目标**：跑通完整人机协作闭环（规划文档第五章 8 步流程）

-  前后端联调：建空间→建文档→配 Agent→下发任务→Diff 审批→合并→版本 / 审计
-  Skill 链路：上传 Skill 包→发布版本→绑定 Agent→创建任务快照→Runtime 加载 `SKILL.md` / references→调用受限 MCP 工具
-  权限链路：创建权限标识符→绑定角色→用户加入空间获得角色→接口按权限标识符校验→无权限返回 403
-  集成测试：后端 Mockito + 前端 Vitest + Playwright e2e 核心链路
-  按 UI 效果图走查还原度，修复视觉 / 交互差异
-  边界场景：熔断、任务终止、并发变更、权限越权
-  Skill 边界场景：版本不可变、包路径穿越、超大文件、非法类型、工具越权、脚本未经授权执行
-  权限边界场景：跨空间访问、角色权限撤销、接口漏标权限、旧角色数据兼容、Agent 能力令牌不能冒充用户权限

**验收**：完整演示脚本可一键走查；核心链路有自动化测试覆盖。

## Phase 8：开源发布准备（2‑3 天）

**目标**：合规、干净的 v0.1 开源版本

-  CONTRIBUTING、安全说明、架构说明
-  敏感信息审计：无硬编码凭证 / 密钥，MCP 凭证走环境变量 / 加密存储
-  Skill 包安全说明：上传限制、解压路径校验、脚本沙箱、版本快照与审计
-  权限目录说明：权限标识符命名规范、默认角色权限矩阵、接口权限标注规范
-  依赖许可证检查（Apache‑2.0 兼容）
-  v0.1 tag + CHANGELOG + 发布说明

**验收**：GitHub 公开仓库可 clone 即跑，README 双语指引可用。

------

## 依赖与风险

- 基础设施依赖较重（Nacos/MySQL/RabbitMQ/Redis/MinIO）：Phase 0 用 Docker Compose 一次解决
- MCP 生态变化快：Phase 3 以官方 MCP Java SDK + Spring AI 为基线，接口隔离
- v0.1 串行单 Agent：任务队列用 RabbitMQ 单消费者，天然满足「同一时间仅一个任务」
- 双文档 / 审批是核心差异：Phase 2‑3 优先保证数据模型正确，不做过度设计
