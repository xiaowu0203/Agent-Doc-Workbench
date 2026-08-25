# Phase 2 交接文档（2026-08-21）

> **状态更新（2026-08-22）**：Phase 2「文档核心」已完成并端到端实测通过（20/20），
> 交付与交接详见 `docs/PHASE3-HANDOFF.md`；本文件保留为 Phase 2 启动基线参考。
>
> 用途：供新会话（Agent / 协作者）快速进入状态，开始 Phase 2「文档核心」开发。
> 生成依据：当前仓库 `phase-1` 分支（提交 `fd2ff79`，Phase 1 完成 + common 拆分已推送 gitee / github）；
> 规划来源：`docs/development-plan.md`（Phase 2）与《Agent-Doc-Workbench 项目完整开发规划文档》（1.4 架构约束 / 2.1 空间与文档功能清单，用户已更新）。

## 一、项目一句话

面向个人/小团队的 **Agent 活文档协作 Web 工作台**：文档是 Agent 任务的唯一协作载体，
Agent 禁止直接改写正式文档，所有修改生成 Diff 变更请求，经人工审批后合并，支持版本回滚、
Token 预算熔断与全链路审计。v0.1 仅实现单 Agent，多 Agent 编排后置 v0.2。

## 二、当前基线（Phase 0-1 已完成，均已推送）

- **Phase 0**：仓库骨架、Docker Compose（MySQL 5.7 / Redis 5.0.14.1 / RabbitMQ / MinIO / Nacos）、双语 README
- **Phase 1**（提交 `fd2ff79`，112 文件 +4418/-102）：
  - auth 鉴权闭环：注册/登录/刷新/登出/me + JWT(RS256) + JWKS + Refresh Token 存 Redis 可撤销
  - gateway：静态路由 + JWT 校验过滤 + CORS + 限流（全局 100/s + 登录 5/s 双桶）+ SpringDoc 聚合
  - 11 张表 Flyway `V1__init.sql`（auth 托管）：user / oauth2_client / space / member / document / document_version / change_request / agent / task / token_usage / audit_log
  - **common 拆分**：common-core + 4 个 starter（web / springdoc / mybatis-plus / redis）
  - **Redis 键前缀统一** `agent-doc-workbench:`（自定义 `ProjectRedisRateLimiter`，见第六节）
- 端到端验证通过：注册→登录→me→刷新→登出、JWKS、OpenAPI 聚合、网关 401/透传、高并发登录限流 429
- **2026-08-22 数据库演进（待提交）**：新增 `model` 表 + Token 统计三表架构（`token_usage` 重构为通用 `obj_id`、`token_usage_detail` 明细真相源、`token_daily_snapshot` 当日快照），`agent` 增 `model_id`；Flyway 新增 `V2__model_and_token_stats.sql` + `V3__token_stats_indexes.sql`，共 **14 张业务表**；设计详见 `docs/database-design.md`

## 三、当前目录结构

```
backend/
├── common/                          # Maven 聚合 POM（com.agentdoc:agent-doc-common）
│   ├── common-core/                 # 纯库：Result/ErrorCode/异常/常量/上下文/雪花ID/JwtTokenParser/
│   │                                #       权限注解/BaseEntity/BaseLogicDeleteEntity/RedisKeyConstants
│   ├── common-web-spring-boot-starter/       # 全局异常/TraceId/UserContext/权限拦截/Ping
│   ├── common-springdoc-spring-boot-starter/ # OpenAPI 模板（agent-doc.openapi.*）
│   ├── common-mybatis-plus-spring-boot-starter # 分页 + 乐观锁（默认关）
│   └── common-redis-spring-boot-starter/     # jsonRedisTemplate + RedisUtils（条件装配）
├── gateway-service/                 # WebFlux，端口 9090；仅依赖 common-core
├── auth-service/                    # 端口 8081；注册登录/JWT/JWKS/Refresh Token
├── document-service/                # 端口 8082；实体/Mapper 骨架（space/member/document/document_version）
└── task-service/                    # 端口 8083；实体/Mapper 骨架（agent/task/change_request/token_usage/audit_log）
```

## 四、common 扩展规划（用户已更新到 docs/tech/backend.md，尚未建模块）

> 规划目标态：common 聚合下共 10 个子模块；**当前仅有 5 个（core + 4 starter），其余为后续按需新建**。

| 模块 | 定位 | 状态 |
| --- | --- | --- |
| common-core | 基础 POJO、基类、工具、异常、注解；**全部服务必依赖** | ✅ 已建 |
| common-web / springdoc / mybatis-plus / redis starter | 纯技术基础设施装配 | ✅ 已建 |
| common-agent-sdk | Agent 相关 DTO、Feign 契约接口；**仅契约无实现**，为 v0.2 Orchestrator-Worker 预留 | ⏳ 规划 |
| common-feign / mq / oss / sentinel / monitor starter | 纯技术基础设施装配 | ⏳ 规划 |

**分层红线（必须遵守）**：
1. `common` 为 `<packaging>pom</packaging>` 聚合工程，不产出可执行 jar
2. starter **禁止存放业务 Controller / Service / Entity**
3. `common-agent-sdk` 仅放 DTO / Feign 契约，无业务实现
4. **gateway 仅依赖 common-core，禁止引入任何 xxx-spring-boot-starter**（规避 Servlet/WebFlux 冲突）
5. v0.1 的 agent、audit 业务模块内置在 task-service，接口稳定后再拆独立微服务

## 五、环境与运行

- JDK 21（`C:\Program Files\Java\jdk-21`）；默认 JAVA_HOME 指向 JDK8，命令前需切换
- 中间件：本机 MySQL 5.7（服务 MySQL）、Redis 5.0.14.1（服务 Redis，`D:\redis\Redis-x64-5.0.14.1\redis-cli.exe`）；Docker 当前不可用
- 常用命令（backend 目录）：
  ```bash
  $env:JAVA_HOME='C:\Program Files\Java\jdk-21'
  ./mvnw test                                        # 全模块测试
  ./mvnw install '-Dmaven.test.skip=true'            # 装到本地仓库（改 common 后必做）
  ./mvnw spring-boot:run '-pl' auth-service          # 单服务启动（依赖从仓库解析）
  ```
- 服务端口：Gateway 9090 / Auth 8081 / Document 8082 / Task 8083

## 六、关键架构约束（必须遵守）

1. **线程模型**：Gateway 用 WebFlux；Auth/Document/Task 统一 Spring MVC + MyBatis-Plus，严禁混用
2. **模块职责**：document = 空间/成员/文档/版本/Diff（含权限校验 Feign 接口，Phase 3 供 task 调用）；task = Agent 实例/MCP 客户端/异步任务/变更审批/Token 用量/审计日志
3. **Redis 键前缀**：所有键以 `agent-doc-workbench:` 开头（`RedisKeyConstants` 组合，禁止裸键）；限流键走自定义 `ProjectRedisRateLimiter`（SCG 4.3.0 已移除 key-prefix 配置，勿再尝试 `redis-rate-limiter.key-prefix`）
4. **BaseEntity 两层**：常规表继承 `BaseLogicDeleteEntity`（id/createdAt/updatedAt/deleted），流水表（token_usage/audit_log）继承 `BaseEntity`；逻辑删除/雪花 ID 由字段注解承担
5. **审计日志只允许 Insert**，业务层禁止 Update / 物理 Delete
6. **MQ 异步化**：MCP 调用全部异步，禁止同步阻塞 HTTP 调外部 Agent（Phase 3 生效）
7. **v0.1 禁止**：A2A、流水线、编排引擎（数据库可预留字段，不写业务代码）
8. **文档安全**：正式文档禁止 Agent 直改；改动统一 ChangeRequest 审批（Phase 2 建立模型与队列接口）

## 七、Phase 2 任务清单（原样摘自 docs/development-plan.md）

**目标**：空间 / 文档 / 版本 / Diff 数据闭环（预计 4-6 天）

- 空间、成员角色（所有者 / 编辑者 / 观察者）、树形目录、文档 CRUD
- 草稿 / 正式双文档模式：正式文档禁止 Agent 直改
- Markdown 存储 + 版本快照（合并变更自动生成版本）+ 一键回滚
- ChangeRequest 模型与审批队列接口（结构化 changes[]，防并发覆盖）
- 文档片段读取接口（按需加载、控 Token）

**验收**：REST API 全量文档管理 + 版本回滚演示可用。

## 八、Phase 2 细化要点（摘自《项目完整开发规划文档》2.1）

- **角色体系**：角色绑定到**具体空间**（非用户全局属性）：`OWNER` 全权 / `EDITOR` 编辑+任务+审批 / `VIEWER` 只读
- **双文档模式**：草稿 = Agent 可直改免审批；正式 = 所有 Agent 修改必须 Diff 审批合并，Agent 无直接写入权限
- **文档操作全集**：创建、删除、重命名、移动、归档、回收站恢复
- **版本快照**：每次合并变更自动生成版本记录；一键回滚任意历史版本，**回滚生成新版本，不删除历史快照**
- 空间全局配置：Token 总预算、Agent 最大并发（v0.1 仅入库预留）、MCP 基础配置、工具白名单

## 九、建议开发顺序

1. **common-security-spring-boot-starter（预留槽位，先补）**：document/task 写受保护接口前落地（Resource Server 自动装配：JWT 解码 + 401 JSON + 无状态链），避免复制 SecurityConfig
2. document-service：空间 / 成员 / 树形目录 / 文档 CRUD（实体已就绪，需补 Service/Controller + 分页）
3. 双文档模式与版本快照：合并变更自动生成 document_version + 回滚接口（回滚=新版本）
4. ChangeRequest 模型与审批队列接口（task-service 内，Phase 3 消费）
5. 文档片段读取接口（按需加载，控 Token，供 Phase 3 Agent 读取）
6. 全链路验证：建空间→建文档→版本快照→回滚 演示可用

## 十、提交规范（沿用 CLAUDE.md）

- 未经用户明确要求，不执行 git commit / push
- 提交格式：`类型(范围): 简述`（feat / fix / docs / refactor / test / chore），中文优先
- 前端提交触发 husky → lint-staged；后端提交建议 `--no-verify`（避免 prettier 误格式化后端 yml/md）
- **注意**：本机 git 在受限沙箱下 commit/push 需升级权限执行（Git Bash signal pipe 限制）

## 十一、注意事项 / 已知坑

- **Maven 仓库不一致**：IDEA 用系统 Maven 3.8.4（settings.xml `localRepository=D:\maven\...\repository`），命令行 mvnw 用 `~/.m2`；IDE 解析不到新 artifact 先查仓库；统一方式：IDEA User settings file 留空 或 `backend/.mvn/maven.config` 指定 repo
- **IntelliJ 子模块不识别**：新建模块可能进 `.idea/misc.xml` ignoredFiles（删除线/无图标），Maven 面板 Unignore 或手动删记录
- **Windows curl 中文**：按 GBK 发送，需 `--data-binary @file` + `Content-Type: application/json; charset=UTF-8`（纯 ASCII 可规避）
- **SCG 废弃告警（已处理 2026-08-22）**：`spring-cloud-starter-gateway` 已切换为 `spring-cloud-starter-gateway-server-webflux`，告警消除
- **限流验证需并发**：登录接口单次 >200ms，串行打不满桶；需 30 并发才触发 429
- 本机中间件版本：MySQL 5.7 / Redis 5.0.14.1（规划文档已同步，勿按 7.x 假设）

## 十二、Phase 3 前瞻（Phase 2 需为其预留）

- task-service 消费审批队列、RabbitMQ 异步任务、`AgentRuntime` + `McpAgentRuntime`（Spring AI MCP Client）
- document 提供**权限校验 Feign 接口**（配合 common-feign starter / common-agent-sdk）
- Token 统计三表架构已建（`token_usage` 聚合 / `token_usage_detail` 真相源 / `token_daily_snapshot` 快照）与模型管理（`model` 表），详见 `docs/database-design.md`；审计日志（audit_log 表已建，Insert-only）
