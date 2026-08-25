# Phase 3 交接文档（2026-08-24）

> 用途：供新会话（Agent / 协作者）快速了解 Phase 3「Agent 与任务」实现状态。
> 生成依据：当前仓库 `phase-3` 分支（基于已合并 Phase 2 的 main）；
> 规划来源：`docs/development-plan.md`（Phase 3）与《Agent-Doc-Workbench 项目完整开发规划文档》（2.3 Agent 协作能力 / 2.4 Token 成本管控）。
> 状态：Phase 3 已完成，计划于 2026-08-26 通过 PR 合并 `main`；真实网站闭环验证统一放在 Phase 7。

## 一、项目一句话

面向个人/小团队的 **Agent 活文档协作 Web 工作台**：文档是 Agent 任务的唯一协作载体，
Agent 禁止直接改写正式文档，所有修改生成 Diff 变更请求，经人工审批后合并，支持版本回滚、
Token 预算熔断与全链路审计。v0.1 仅实现单 Agent，多 Agent 编排后置 v0.2。

## 二、Phase 2 已完成（已合并入 main）

- **空间 / 成员**：空间 CRUD（创建者自动 OWNER）+ 成员角色（OWNER/EDITOR/VIEWER，最后一名 OWNER 不可移除）
- **文档核心**：文档 CRUD / 树形目录（移动防环）/ 草稿-正式双模式 / 归档-回收站-恢复
- **版本快照**：编辑保存自动生成版本（version_no 递增）、版本列表/详情/对比（简化文本级）、**回滚=生成新版本不删历史**
- **Diff 审批闭环**：ChangeRequest 提交（结构化 changes[] + baseVersion）→ 审批队列（通过/拒绝/退回）→ **合并**（Feign 经网关调 document-service 应用变更，透传审批人 JWT，基线版本校验防并发覆盖 40900）→ 自动生成版本快照
- **文档片段读取**：`GET /api/document/documents/{id}/fragments?start=&length=`（控 Token，供 MCP Agent 按需读取）
- **鉴权与身份连续（模型 B）**：业务服务用 Spring Security Resource Server 从 `Authorization` 自行解析 JWT（`CommonSecurityAutoConfiguration`，`agent-doc.security.jwks-url` 配置），身份唯一来源 SecurityContext，业务代码经 `AuthUtils` 读取（无 X-User-* 自定义头、无 UserContext 体系）；`@RequireLogin` 注解驱动（无注解接口匿名可达），无效/过期 token 由 Security 层直接 401；服务间 Feign 调用经网关 + 透传用户 JWT（`AuthHeaderForwardInterceptor`），目标服务解析同一 JWT 取操作人（合并接口校验 EDITOR 角色，已实测 updatedBy=审批人）
- **common 新增**：common-feign-spring-boot-starter（@EnableFeignClients 扫 `com.agentdoc.common.feign`）+ 契约 `DocumentFeign`（MergeRequestDTO/MergeResultVO/ChangeItemDTO 落位 common-core feign 包）+ `PageUtils.toPage()` + `PageVO` 统一分页
- **Flyway V4 / V5**：`change_request.base_version`；`document.parent_id` 改为可空并将根目录存量 `0` 迁移为 `NULL`（本机库 V1-V5 已执行）
- **端到端实测 20/20 通过**：建空间→建文档→编辑版本→回滚→提交变更→审批→合并→冲突 40900→片段读取→归档恢复；网关 401 / 业务服务直连无效 Token 401 验证通过

## 三、当前目录结构（Phase 2 增量）

```
backend/
├── common/
│   ├── common-core/                    # + feign 包（DocumentFeign 完整客户端契约：@FeignClient+HTTP 注解 / MergeRequestDTO / MergeResultVO / ChangeItemDTO）
│   │                                   # + enums.ChangeOp + pojo.vo.PageVO
│   ├── common-security-spring-boot-starter/ # + CommonSecurityAutoConfiguration（Resource Server）
│   │                                       # + TaskCapabilityVerifier / TaskCapabilityAuthenticationFilter
│   │                                       # + config.SecurityVerifyProperties
│   ├── common-web-spring-boot-starter/ # + web.TraceIdFilter
│   │                                   # + security.PermissionInterceptor（@RequireLogin 注解驱动）
│   ├── common-springdoc-spring-boot-starter/
│   ├── common-mybatis-plus-spring-boot-starter/  # + utils.PageUtils
│   ├── common-redis-spring-boot-starter/
│   └── common-feign-spring-boot-starter/         # 新增：@EnableFeignClients 扫描装配 + 默认 AuthHeaderForwardInterceptor（透传用户 JWT）
├── gateway-service/                    # JWT 门禁 + traceId；透传原始 Authorization，不注入身份头
├── auth-service/                       # + Flyway V4__change_request_base_version.sql / V5__document_parent_id_nullable.sql
├── document-service/
│   ├── controller/  SpaceController / MemberController / DocumentController（含 /merge 服务间端点）/
│   │                DocumentVersionController
│   ├── service/     SpaceService / MemberService / SpacePermissionService /
│   │                DocumentService（含 mergeForFeign / readFragment）/ DocumentVersionService
│   └── enums/       SpaceRole / DocType / DocStatus
└── task-service/
    ├── controller/  ChangeRequestController（submit/list/approve/reject/return/merge）
    ├── service/     ChangeRequestService（状态机 + Feign 合并，注入 common.feign.DocumentFeign）
    ├── mapper/      DocumentRefMapper（跨域只读 document 表投影）
    └── enums/       ChangeRequestStatus / ChangeRequestType
```

## 四、Phase 3 任务清单（当前实现状态）

**目标**：独立 Agent Server + Workbench MCP Server + A2A 1.0 远程任务协议。

- ✅ 新增 `agent-service`，独立持有 Agent、Model、Prompt 和 AgentExecution。
- ✅ `task-service` 作为 A2A Client，通过 RabbitMQ 异步分发并接收标准 Push Notification。
- ✅ `agent-service` 使用官方 A2A Java SDK 暴露 Send/Get/List/Cancel/Stream/Subscribe/Push Config。
- ✅ `agent-service` 使用 Spring AI 调用 OpenAI 兼容模型，并按任务创建 MCP Streamable HTTP Client。
- ✅ `task-service` 作为 Workbench MCP Server，暴露任务上下文、文档片段读取和变更提案三个工具。
- ✅ Task Capability 同时约束 A2A 与 MCP 的 task、agent、space、document 和 action 范围。
- ✅ Agent 修改统一生成 ChangeRequest，正式内容不由 Agent 直接落库。
- ✅ 旧 `McpAgentRuntime`、Mock Runtime 及 task-service 内的 Agent/Model 所有权已移除。
- ✅ A2A TaskStore 与 PushNotificationConfigStore 已使用 MySQL 持久化，协议载荷 AES-GCM 加密，服务重启可恢复。
- ✅ task-service 已接入定时 A2A Get Task 状态对账；按任务使用 Redis 锁，避免多实例重复对账。

完整设计和协议入口见 `docs/agent-server-a2a-mcp-design.md`。

### Phase 3 配置

- `TASK_CAPABILITY_KEY`：Base64 编码的 16/24/32 字节 AES 密钥，用于加密任务能力令牌。
- `AGENT_CONFIG_KEY`：Base64 编码的 16/24/32 字节 AES 密钥，用于加密模型 API Key。
- `AGENT_SERVICE_URL` / `AGENT_PUBLIC_URL`：Agent Service 内部调用地址和 Agent Card 公网地址。
- `TASK_CALLBACK_URL` / `MCP_SERVER_URL`：A2A 回调地址和 Workbench MCP Streamable HTTP 地址。
- `A2A_RECONCILE_DELAY_MS` / `A2A_RECONCILE_STALE_SECONDS` / `A2A_RECONCILE_BATCH_SIZE`：任务状态对账周期、无心跳阈值和单批扫描数量。
- `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY`：auth-service 的 RSA JWT 密钥对；任务能力 JWT 与登录 JWT 共用该密钥，由 auth-service 统一签发。
- RabbitMQ：`agent-doc-workbench.task.execute` 队列，失败消息进入 `agent-doc-workbench.task.dead`。

任务能力签发接口为 `/api/auth/internal/task-capabilities`，要求普通用户认证上下文；task-service 只在创建任务时远程调用一次，用户 `Authorization JWT` 由现有 Feign 拦截器透传，任务执行与 document-service 访问均通过 auth-service JWKS 本地验签。

## 五、Phase 2 → Phase 3 交接点（已就绪的复用资产）

1. **审批链路可直接复用**：ChangeRequestService.submit（proposedBy 已支持 Agent ID 语义，sourceTaskId 字段 Phase 3 由任务提交时填充）
2. **合并接口已通**：`common.feign.DocumentFeign` 即完整 Feign 客户端（统一入口，`@FeignClient` + HTTP 注解均标注在契约上），task-service 直接注入调用（经网关）；document-service 侧 `/api/document/documents/merge` 已实现并校验 EDITOR 角色；冲突 40900 语义已验证；合并操作人身份 = 审批人（JWT 透传，已实测 updatedBy 一致）
3. **片段读取接口已通**：Workbench MCP Tool 按需调用 `readFragment`，单次读取长度受服务端常量限制
4. **权限校验 Feign 契约已就绪**：document 提供 `checkSpacePermission`，task 侧 Agent 配置、任务创建与执行链路复用；合并接口继续由 document 侧校验 EDITOR 角色
5. **Token 统计三表实体已建**：ModelEntity / TokenUsageDetailEntity / TokenDailySnapshotEntity；聚合/快照/对账逻辑（凌晨聚合 + 3min 节流快照 + 任务级本地累计 + 明细 SUM 对账）见 `docs/database-design.md`
6. **Agent 表结构已迁移**：Agent 增加系统提示词、配置版本和执行限制；Model 增加加密 API Key；新增 `agent_execution` 快照表

## 六、关键架构约束（必须遵守，沿用 Phase 1/2）

1. **线程模型**：Gateway WebFlux；Auth/Document/Task 统一 Spring MVC + MyBatis-Plus，严禁混用
2. **模块职责**：document = 空间/成员/文档/版本；task = 任务/A2A Client/MCP Server/审批/Token/审计；agent = Agent/Model/A2A Server/Spring AI Runtime/MCP Client
3. **Redis 键前缀**：`agent-doc-workbench:`（RedisKeyConstants），禁止裸键；限流走自定义 ProjectRedisRateLimiter
4. **BaseEntity 两层**：常规表继承 BaseLogicDeleteEntity；流水表（token_usage_detail / token_daily_snapshot / audit_log）继承 BaseEntity
5. **审计日志只允许 Insert**，业务层禁止 Update / 物理 Delete
6. **MQ 异步化**：MQ 消费者只负责 A2A 分发，不阻塞等待模型执行；结果通过 A2A Push 回调同步
7. **协议边界**：Agent 任务控制使用 A2A；Agent 获取 Workbench 数据和提交操作使用 MCP，禁止混用
8. **文档安全**：Agent 变更统一创建 ChangeRequest，必须经过人工审批后才能进入正式文档
9. **服务间调用（统一入口 + 身份连续）**：Feign 客户端**只允许定义在 common-core 的 `com.agentdoc.common.feign` 包**（`@FeignClient` + HTTP 注解直接标注在契约接口上），业务服务禁止自建 FeignClient，直接注入契约接口调用；用户业务调用统一经网关（9090）并透传用户 JWT。A2A/MCP 任务调用使用 Task Capability Bearer，不依赖用户上下文，也不引入共享静态令牌。common-core 的 openfeign 依赖为 optional（不污染 gateway/auth 依赖树）
10. **代码规范**：pojo 分层 / DTO-VO 全大写后缀 / 类转换收敛实体类 / enums+constant 包 / 禁止魔法值 / 字段 @Schema / PageParam+PageUtils 分页 / Lombok

## 七、环境与运行

- JDK 21（`C:\Program Files\Java\jdk-21`）；命令行 JAVA_HOME 需显式切换（默认 JDK8）
- 中间件：本机 MySQL 5.7 / Redis 5.0.14.1（Docker 不可用）；RabbitMQ 本机服务可用（Phase 3 用到）
- 常用命令（backend 目录，**构建需全权限执行**）：
  ```bash
  $env:JAVA_HOME='C:\Program Files\Java\jdk-21'
  ./mvnw test                                         # 全模块测试
  ./mvnw install '-Dmaven.test.skip=true'             # 改 common 后必做
  ./mvnw spring-boot:run '-pl' auth-service          # 单服务启动（先启动 auth，Flyway 迁移）
  ```
- 服务端口：Gateway 9090 / Auth 8081 / Document 8082 / Task 8083 / Agent 8084
- **注意**：全链路验证走网关 9090（`/api/document/**`、`/api/task/**`）；服务间 Feign 调用也经网关（`agent-doc.feign.gateway-url`），靠透传用户 JWT 保持身份连续

## 八、已知坑与注意事项

- **Maven 双仓库**：IDEA 用系统 Maven（`D:\maven\...`），命令行 mvnw 用 `~/.m2`；新 artifact 解析失败先查仓库
- **DSH 沙箱**：Maven 构建/文件删除需全权限（danger-full-access）；否则 `target/maven-status` 写入被拒
- **Flyway**：已存在 V1-V12，不可修改已执行迁移（checksum）；Phase 4 新表/列一律从 V13 开始追加
- **Windows curl/脚本中文**：PowerShell 5.1 读 UTF-8 无 BOM 脚本会乱码，脚本保持纯 ASCII 或转 GBK
- **业务错误语义**：业务失败统一 HTTP 200 + Result.code（如 40900 冲突）；服务间调用的目标接口（如 document 的 `/merge`）需把业务异常转 HTTP 状态码（ResponseStatusException，见 DocumentController.toStatusException）供 Feign 客户端按状态识别
- **Feign 契约即客户端（统一入口）**：`com.agentdoc.common.feign` 的接口直接标注 `@FeignClient` 与 HTTP 方法注解（`@PostMapping` 等），业务服务注入使用即可；**不要**在业务服务内新建 FeignClient 接口（会分散契约、两处维护）
- 本机中间件版本：MySQL 5.7（JSON 类型可用、无降序索引）/ Redis 5.0.14.1

## 九、Phase 4 前瞻：Skill 管理

- Phase 4 独立建设可版本化 Skill 包，目录至少包含 `SKILL.md`，可选 `references/`、`assets/` 和受控 `scripts/`。
- Skill 元数据和版本关系由 MySQL 保存，包本体进入 MinIO；当前仓库只有 MinIO 基础设施配置，尚未接入后端 SDK。
- Agent 绑定明确的已发布 Skill 版本；任务创建执行记录时保存不可变 Skill 快照，禁止运行中跟随最新版本漂移。
- 两种 Agent Runtime 必须共用 Skill 解析、提示词组合、资源读取和工具过滤逻辑。
- `scripts/` 在 Phase 4 只保存、不直接执行；细粒度 RBAC 留 Phase 5，前端 Skill 页面留 Phase 6。
- 详细启动基线、数据模型、接口和验收清单见 `docs/PHASE4-HANDOFF.md`。
