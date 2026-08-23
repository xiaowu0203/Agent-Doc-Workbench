# Phase 3 交接文档（2026-08-22）

> 用途：供新会话（Agent / 协作者）快速进入状态，开始 Phase 3「Agent 与任务」开发。
> 生成依据：当前仓库 `phase-2` 分支（Phase 2「文档核心」已完成并端到端实测通过，待合并入 main）；
> 规划来源：`docs/development-plan.md`（Phase 3）与《Agent-Doc-Workbench 项目完整开发规划文档》（2.3 Agent 协作能力 / 2.4 Token 成本管控）。

## 一、项目一句话

面向个人/小团队的 **Agent 活文档协作 Web 工作台**：文档是 Agent 任务的唯一协作载体，
Agent 禁止直接改写正式文档，所有修改生成 Diff 变更请求，经人工审批后合并，支持版本回滚、
Token 预算熔断与全链路审计。v0.1 仅实现单 Agent，多 Agent 编排后置 v0.2。

## 二、Phase 2 已完成（本分支，待合并）

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
│   ├── common-web-spring-boot-starter/ # + config.CommonSecurityAutoConfiguration（Resource Server）
│   │                                   # + config.SecurityVerifyProperties + web.TraceIdFilter
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

## 四、Phase 3 任务清单（摘自 development-plan.md）

**目标**：单 Agent MCP 接入 + Token 预算熔断（预计 5-7 天）

- agent 业务模块：Agent 配置、MCP 凭证加密存储（AES，`agent.mcp_config`）、权限范围（空间/目录/文档）、工具白名单
- task-service：任务状态机（PENDING→RUNNING→COMPLETED/TERMINATED/FAILED），RabbitMQ 异步消费
- `AgentRuntime` 抽象 + `McpAgentRuntime` 实现（Spring AI MCP Client，Workbench 主动调用外部 Agent）
- 变更输出 → ChangeRequest 转换：**正式文档入审批队列（复用 Phase 2 已建链路）、草稿文档直写**
- Token 预算：任务级上限 + 空间全局预算 + 熔断 + 四维度用量统计（空间/文档/任务/Agent）
- 审计日志：操作主体（人/Agent）、操作类型、关联任务、不可篡改（audit_log 表已建，Insert-only）

**验收**：对接一个真实/模拟 MCP Agent，走通「下发任务→Agent 读取片段→产出变更→熔断/预算生效」链路。

## 五、Phase 2 → Phase 3 交接点（已就绪的复用资产）

1. **审批链路可直接复用**：ChangeRequestService.submit（proposedBy 已支持 Agent ID 语义，sourceTaskId 字段 Phase 3 由任务提交时填充）
2. **合并接口已通**：`common.feign.DocumentFeign` 即完整 Feign 客户端（统一入口，`@FeignClient` + HTTP 注解均标注在契约上），task-service 直接注入调用（经网关）；document-service 侧 `/api/document/documents/merge` 已实现并校验 EDITOR 角色；冲突 40900 语义已验证；合并操作人身份 = 审批人（JWT 透传，已实测 updatedBy 一致）
3. **片段读取接口已通**：`readFragment` 供 MCP 工具调用（工具白名单需在 Phase 3 接入）
4. **权限校验 Feign 契约待补**（Phase 3 明确要求）：document 需提供「空间成员角色校验」Feign 接口（如 `checkSpacePermission(spaceId, userId, minRole)`），task 侧任务创建等场景复用（合并接口的角色校验已就绪：document 侧直接 SpacePermissionService 校验）
5. **Token 统计三表实体已建**：ModelEntity / TokenUsageDetailEntity / TokenDailySnapshotEntity；聚合/快照/对账逻辑（凌晨聚合 + 3min 节流快照 + 任务级本地累计 + 明细 SUM 对账）见 `docs/database-design.md`
6. **Agent 表结构已就绪**：`agent`（含 `model_id` 关联、`mcp_config` AES 加密预留）、`model` 表；Agent 实体已含 modelId

## 六、关键架构约束（必须遵守，沿用 Phase 1/2）

1. **线程模型**：Gateway WebFlux；Auth/Document/Task 统一 Spring MVC + MyBatis-Plus，严禁混用
2. **模块职责**：document = 空间/成员/文档/版本/Diff + 权限校验 Feign（Phase 3 补）；task = Agent 实例/MCP 客户端/异步任务/变更审批/Token 用量/审计日志
3. **Redis 键前缀**：`agent-doc-workbench:`（RedisKeyConstants），禁止裸键；限流走自定义 ProjectRedisRateLimiter
4. **BaseEntity 两层**：常规表继承 BaseLogicDeleteEntity；流水表（token_usage_detail / token_daily_snapshot / audit_log）继承 BaseEntity
5. **审计日志只允许 Insert**，业务层禁止 Update / 物理 Delete
6. **MQ 异步化**：MCP 调用全部异步，禁止同步阻塞 HTTP 调外部 Agent（Phase 3 生效）
7. **v0.1 禁止**：A2A、流水线、编排引擎（数据库可预留字段，不写业务代码）
8. **文档安全**：正式文档禁止 Agent 直改；Agent 改动统一 ChangeRequest 审批（Phase 2 链路已就绪）；草稿文档 Agent 可直写
9. **服务间调用（统一入口 + 身份连续）**：Feign 客户端**只允许定义在 common-core 的 `com.agentdoc.common.feign` 包**（`@FeignClient` + HTTP 注解直接标注在契约接口上），业务服务禁止自建 FeignClient，直接注入契约接口调用；调用统一**经网关（9090）**，**用户 JWT 透传是 common-feign starter 默认装配**（`AuthHeaderForwardInterceptor`，任何服务依赖 starter 即自动具备，无需各自实现）；目标服务经 **Spring Security Resource Server 自行解析 JWT**，操作人身份从 SecurityContext 读取（`AuthUtils`）、**不可伪造**（合并接口已按此实现并校验 EDITOR 角色）。无用户上下文的系统级调用（定时任务 / MQ 消费者，Phase 3 再议）另行设计，**不引入共享静态令牌**。common-core 的 openfeign 依赖为 optional（不污染 gateway/auth 依赖树）
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
- 服务端口：Gateway 9090 / Auth 8081 / Document 8082 / Task 8083
- **注意**：全链路验证走网关 9090（`/api/document/**`、`/api/task/**`）；服务间 Feign 调用也经网关（`agent-doc.feign.gateway-url`），靠透传用户 JWT 保持身份连续

## 八、已知坑与注意事项

- **Maven 双仓库**：IDEA 用系统 Maven（`D:\maven\...`），命令行 mvnw 用 `~/.m2`；新 artifact 解析失败先查仓库
- **DSH 沙箱**：Maven 构建/文件删除需全权限（danger-full-access）；否则 `target/maven-status` 写入被拒
- **Flyway**：已执行 V1-V5 不可修改（checksum）；新表/列一律 V6+ 增量脚本
- **Windows curl/脚本中文**：PowerShell 5.1 读 UTF-8 无 BOM 脚本会乱码，脚本保持纯 ASCII 或转 GBK
- **业务错误语义**：业务失败统一 HTTP 200 + Result.code（如 40900 冲突）；服务间调用的目标接口（如 document 的 `/merge`）需把业务异常转 HTTP 状态码（ResponseStatusException，见 DocumentController.toStatusException）供 Feign 客户端按状态识别
- **Feign 契约即客户端（统一入口）**：`com.agentdoc.common.feign` 的接口直接标注 `@FeignClient` 与 HTTP 方法注解（`@PostMapping` 等），业务服务注入使用即可；**不要**在业务服务内新建 FeignClient 接口（会分散契约、两处维护）
- 本机中间件版本：MySQL 5.7（JSON 类型可用、无降序索引）/ Redis 5.0.14.1

## 九、Phase 4 前瞻（Phase 3 需为其预留）

- 前端 8 页（登录/工作空间/文档树编辑/Agent 配置/任务创建/Diff 审批/版本历史/Token 用量审计）
- 审批页依赖：ChangeRequest 队列查询（支持 spaceId/documentId/status 过滤）+ changes[] 结构化展示 + approve/reject/return/merge 操作（均已就绪）
- 版本历史页依赖：版本列表/详情/对比（已就绪）
- 文档编辑页依赖：detail（含 content）+ update（自动版本）+ fragments（已就绪）
