# CLAUDE.md

> Agent-Doc-Workbench 项目记忆与协作规范（本文件随项目演进持续维护）

## 仓库地址

- **Gitee**：https://gitee.com/wu_hai123/agent-doc-workbench
- **GitHub**：https://github.com/xiaowu0203/Agent-Doc-Workbench
- 分支约定：main 为稳定分支；phase-0 / phase-1 为开发分支（Phase 0 工程基建 / Phase 1 后端地基）

## 一、项目概述

- **项目名称**：Agent-Doc-Workbench
- **定位**：面向个人/小团队的 Agent 活文档协作开源 Web 工作台
- **核心理念**：文档作为 AI Agent 任务的唯一协作载体
- **核心范式**：Agent 禁止直接改写正式文档，所有修改统一生成 Diff 变更请求，经人工审核、批注、确认后才可合并生效
- **核心差异化**：
  - 对比 Notion AI / WPS AI：Agent 改动可追溯、可审核、可回滚，杜绝 AI 无脑改稿
  - 对比 LangGraph / CrewAI：不是纯内存任务输出，提供可视化、可留存、可迭代的文档协作载体
  - 独有「草稿/正式双文档模式」+「Token 预算熔断」+「MCP 通用适配」
- **目标用户**：独立开发者、小产品团队、内容创作团队、小型研发团队（不做重型企业多租户）

## 二、版本规划（里程碑）

- **v0.1 MVP（可开源发布）**：空间文档管理、双文档模式、ProseMirror 编辑器、单 Agent MCP 接入、Diff 审批流程、Token 预算管控、审计日志、基础导入导出、REST API
- **v0.2 功能增强**：按需开启多 Agent 分工协作、docx 导入导出、提示词模板库、审计日志导出、审批批量操作优化
- **v0.3 生态扩展**：对外暴露 MCP Server、Excalidraw 绘图嵌入、超大文档渲染优化、第三方生态适配

## 三、核心业务闭环（v0.1 演示流程）

1. 创建空间 → 新建正式文档 → 录入基础内容
2. 配置外部 MCP Agent 连接，设置单任务 Token 预算阈值，下发任务
3. Agent 通过 MCP 按需读取文档片段（不加载全文，控 Token），生成修改
4. Agent 提交 Diff 变更请求 → 进入审批队列 → 页面可视化展示增删改差异
5. 人工精细化审批：部分接受 / 修改后确认 / 拒绝 / 批注退回重改
6. 审批通过 → 合并至正式文档 → 自动生成版本快照 → 记录 Token 消耗与审计日志
7. 试错场景：复制文档至草稿区，Agent 可自由编辑，无需审批
8. 任务完成 → 导出 Markdown 成果、查看用量统计、随时终止闲置任务

## 四、技术栈（已定稿，详见 `docs/tech/README.md`）

### 当前状态（2026-08-20）
- Phase 0 工程基建**已完成**：Git 仓库（main / phase-0 分支）、Docker Compose、`.gitignore`、Apache-2.0 LICENSE、双语 README 齐备
- `backend/`：Maven 多模块骨架 `common` / `gateway-service` / `auth-service` / `document-service` / `task-service`（JDK 21 / Spring Boot 3.5.0 / Spring Cloud 2025.0.0），各服务可启动并提供 ping 接口
- `frontend/`：Vite + Vue 3 + TS(strict) + Pinia + Vue Router + Element Plus + ESLint + Prettier + Husky + Vitest 脚手架完成，默认首页可访问
- `docker compose` 一键编排 MySQL 5.7 / Redis 7 / RabbitMQ 3-management / MinIO / Nacos 3.2.2（本地开发中间件）
- 待办（下一步）：启动 Phase 1 后端地基（common 公共能力、auth 鉴权闭环、gateway 路由）

### 当前状态（2026-08-21）
- **Phase 1 后端地基完成并实测**：common 公共能力、auth 鉴权闭环（注册/登录/刷新/登出/me + JWT RS256 + JWKS）、gateway 路由/鉴权/限流/OpenAPI 聚合，全链路验证通过（含高并发限流 429）
- **common 拆分为 5 子模块**（详见 `docs/common-modules.md`）：`common-core` + `common-web-spring-boot-starter` + `common-springdoc-spring-boot-starter` + `common-mybatis-plus-spring-boot-starter` + `common-redis-spring-boot-starter`；gateway 仅依赖 core
- **Redis 键统一 `agent-doc-workbench:` 前缀**（多项目共享 Redis 隔离）；限流用自定义 `ProjectRedisRateLimiter`（SCG 4.3.0 移除 key-prefix 配置）
- 待办（下一步）：Phase 2（前端鉴权接入、文档空间 CRUD API 等）

### 后端选型（v0.1）
- **基础**：JDK 21 / Spring Boot 3.5.0 / Spring Cloud 2025.0.0 / Maven / Apache-2.0
- **数据**：MySQL 5.7（UTF8MB4、DATETIME、逻辑删除、雪花 ID）/ MyBatis-Plus 3.5.10 / Flyway（可选）/ Redis 7.x + Redisson
- **安全**：Spring Security 6 / Spring Authorization Server / OAuth2 Resource Server / JWT（RSA RS256）
- **基础设施**：Nacos 3.2.2 / Spring Cloud Gateway / RabbitMQ 3-management（Spring AMQP）/ MinIO / XXL-Job / Docker Compose 一键启动；ELK 可选，非 v0.1 启动依赖
- **Agent 接入**：Spring AI（MCP Client，ChatClient/Tool Calling 可选）+ 官方 MCP Java SDK
- **其他**：SpringDoc OpenAPI / Lombok / MapStruct / JUnit5 + Mockito
- **关键约束**：Gateway 用 WebFlux，业务服务统一 Spring MVC + MyBatis-Plus，不混用线程模型

### 模块划分（v0.1）
- 模块边界：`gateway` / `auth` / `document` / `agent` / `task` / `audit` / `common`
- v0.1 优先实现 `gateway`、`auth`、`document`、`task`；`agent` 与 `audit` 初期合并在主服务，接口稳定后再拆分

### Agent 抽象（核心设计）
```java
public interface AgentRuntime {
    AgentExecutionResult execute(AgentExecutionContext context);
    void cancel(String executionId);
    AgentRuntimeStatus status(String executionId);
}
```
- 适配器：`McpAgentRuntime`（v0.1）、`SpringAiAgentRuntime`（可选）、`AgentScopeRuntime`（v0.2）
- 原则：Spring AI 只负责模型/MCP 适配；审批、预算、文档变更等业务编排全部自研
- v0.1 主路径：Workbench 内部作为 **MCP Client**，主动调用外部 MCP 驱动的 Agent
- v0.2：保留 `AgentRuntime` 抽象，评估多 Agent 编排；v0.3：对外暴露 Workbench MCP Server

### 前端选型（v0.1）
- **框架**：Vue 3 / TypeScript（strict）/ Vite / Vue Router / Pinia / Element Plus / Axios
- **编辑器**：ProseMirror 全家桶（state/view/model/schema-basic/schema-list/tables/markdown/history/keymap），支持标题、列表、表格、代码块、图片、链接、文本样式
- **文档三格式**：Markdown 持久化 + ProseMirror JSON 编辑与结构化 Diff + HTML 按需生成展示；**不以 HTML 为主存储**
- **协同**：v0.1 用 REST + 轮询；Yjs + y-prosemirror + y-websocket 延后至 v0.2 实时协同
- **Diff 审批**：diff-match-patch（文本级）+ prosemirror-changeset（结构化）+ 自定义审批组件；变更请求保留结构化信息（requestId/documentId/taskId/agentId/baseVersion/changes[]/status）
- **渲染安全**：markdown-it + DOMPurify 防 XSS；MinIO 文件走后端临时签名 URL，前端不持有永久凭证
- **状态管理**：Pinia 按域拆分（auth/workspace/document/editor/task/approval/notification）
- **质量**：Vitest + Vue Test Utils + Playwright / ESLint + Prettier / Husky + lint-staged / pnpm
- **v0.1 页面**：登录、工作空间首页、文档树与编辑、Agent 配置、任务创建、Diff 审批、版本历史、Token 用量与审计日志

### 鉴权与 Token（OAuth2/JWT 方案）
- 前端：Authorization Code + PKCE；Access Token 仅存内存，Refresh Token 走 HttpOnly + Secure + SameSite Cookie
- Access Token 30 分钟 / Refresh Token 7 天 / RSA 签名 / JWK Set 分发公钥
- 前端 401 自动刷新，失败跳登录；前端权限仅控制界面，最终权限由后端校验
- 外部 Agent/MCP Client：OAuth2 Client Credentials 获取 Agent 专属 Token；Agent 不直接拥有用户权限，必须绑定空间、文档范围和工具白名单

## 五、v0.1 明确不做（严控复杂度）

- 多 Agent 并行协同（延后 v0.2）
- 不自托管大模型推理，全程依赖外部 MCP/第三方模型
- 大型企业多租户、组织架构、部门层级
- Excel / PPT 解析渲染
- 邮件、第三方 IM 深度集成
- 复杂合规导出、企业级权限风控

## 六、开发规范

### 提交与推送（强制约束）
- **未经用户明确要求，禁止执行任何 git commit / git push / 分支创建与切换**
- 用户明确说「提交」「推送」等指令时才可操作，且操作前说明将要执行的 git 命令
- 工作区保持整洁：完成任务后不自动提交，等待用户指令

### 代码与协作
- 中文优先：注释、文档、提交信息使用中文（面向中文社区开源）
- 提交信息格式：`类型(范围): 简述`，如 `feat(backend): 新增空间实体`；类型参考 feat / fix / docs / refactor / test / chore
- 变更保持小而聚焦，遵守「单一职责」；大改动先拆解再提交
- 后端代码遵循 Spring 官方风格；前端遵循既定工程规范（ESLint/Prettier/Husky）
- 重要技术决策记录到「决策记录」章节，形成项目记忆；技术选型细节以 `docs/` 文档为准

### 后端代码规范（全后端强制，含后续新代码）
1. **DTO/VO 命名与分层**：DTO/VO 后缀必须全大写（`UserVO`，禁止 `UserDto`）；`entity` / `dto` / `vo` 三个包的父级包必须是 `pojo`（即 `pojo.entity` / `pojo.dto` / `pojo.vo`）；请求入参归 DTO、出参/展示对象归 VO
2. **禁止魔法值**：代码中不得出现硬编码数值/字符串（如 `user.setStatus(1)`），一律抽取为常量（`constant` 包，`XXXConstant` 命名）或枚举（`enums` 包）
3. **枚举放 `enums` 包**
4. **配置类放 `config` 包**，命名 `XXXConfig` / `XXXProperties`
5. **常量类放 `constant` 包**，命名 `XXXConstant`
6. **自定义注解放 `annotation` 包**
7. **AOP 相关放 `aop` 包**（无 AOP 代码则不建）
8. 分层示例：`controller` / `service` / `mapper` / `pojo{entity,dto,vo}` / `enums` / `aop` / `config` / `annotation` / `constant` / `utils` / `handler` / `executor`，按实际情况创建
9. **Entity/VO/DTO/Enum 字段必须带注释或 `@Schema`**：业务服务（依赖 springdoc）字段统一加 `@Schema(description = "...")`；common-core 纯库不加 `@Schema`（避免反向依赖 springdoc），用 javadoc
10. **分页参数统一用 `PageParam`**（`common-core` 的 `pojo.dto`）：`pageNum`（默认 1）/ `pageSize`（默认 10）+ `validate()` 校验（pageSize 上限 100），分页接口直接接收并先校验
11. **Feign 远程调用规范**：新模块命名 `common-feign-spring-boot-starter`；接口落位 `common-core` 的 `com.agentdoc.common.feign` 包，一个业务一个 `XXXXFeign` 接口；待出现第一个跨服务调用（Phase 2）时再建
12. **类转换全部封装进实体类**：Entity 自带 `toVO()`/`toDTO()`，DTO/VO 自带 `toEntity()` 等转换方法，Controller/Service 中禁止出现对象字段搬运
13. **实体类禁止手写 getter/setter**：VO/DTO/Entity 统一用 Lombok `@Data`/`@Getter`/`@Setter`（record 自动生成访问器），除非不得已（如实现框架接口的 `getOrder()`/`setApplicationContext()`）

### 开源规范
- 目标开源，License 倾向 Apache-2.0；发布前补齐 README、CONTRIBUTING、安全说明
- 敏感配置（MCP 凭证、Token 密钥）严禁提交，使用环境变量/配置文件忽略机制
- 开源前检查：无内部敏感信息、无硬编码密钥、依赖许可证合规

## 七、项目记忆（随项目演进追加）

### 2026-08-19（初始）
- 项目起步：仅规划文档（`docs/`）+ Spring Boot 空壳 + 空 `frontend` 目录
- 技术栈定稿：已汇总于第四节，并记录为 ADR-001 ~ ADR-005
- 已生成 v0.1 全 8 页 UI 效果图（docs/ui-mockups/）
- 开发路线图定稿（docs/development-plan.md，Phase 0-6，约 1 个月）
- 已生成双语 README（README.md / README.en.md）

### 2026-08-20（Phase 0 完成）
- `docker-compose.yml`：MySQL 5.7 / Redis 7 / RabbitMQ 3-management / MinIO / Nacos 3.2.2，含持久化卷与健康检查
- 后端重构为 Maven 多模块：`common`（Result/BusinessException）、四个 Boot 服务，端口 Gateway 9090 / Auth 8081 / Document 8082 / Task 8083
- 前端脚手架完成并验证：lint / build（vue-tsc）/ vitest 全绿，Husky pre-commit → lint-staged → eslint/prettier 链路实测可用
- 清理 backend 冗余文件：HELP.md、backend.iml、.idea/、旧单模块 src 与 target、重复的 backend/.gitignore

### 2026-08-21（Phase 1 完成 + common 拆分）
- Phase 1 后端地基完成并实测：common 公共能力、11 张表 Flyway（V1__init.sql 由 auth-service 托管）、auth 鉴权闭环、gateway 路由/鉴权/限流/OpenAPI 聚合
- **common 拆分为 common-core + 4 个 starter**（web / springdoc / mybatis-plus / redis），11 模块构建全绿（37 测试），端到端验证通过（注册→登录→me→刷新→登出、JWKS、OpenAPI 聚合、网关 401/透传、高并发登录限流 429）
- **BaseEntity 两层设计**：`BaseEntity`（id/createdAt）+ `BaseLogicDeleteEntity`（+updatedAt/deleted），依据 11 表字段实情（11 个 created_at / 8 个 updated_at / 9 个 deleted）；TokenUsage/AuditLog（流水表）继承 Base，DocumentVersion（无 updated_at）继承 Base + 自持 @TableLogic
- **Redis 键前缀统一 `agent-doc-workbench:`**：refresh token 键、限流键（自定义 `ProjectRedisRateLimiter`，SCG 4.3.0 移除 key-prefix 后自研）；双桶（全局 100/s + 登录 5/s）经独立 KeyResolver 隔离
- 环境事实：IDEA 用系统 Maven 3.8.4（settings.xml localRepository=`D:\maven\...\repository`），命令行 mvnw 用默认 `~/.m2`（**两仓库不一致**，IDE 解析失败先查这个）；IntelliJ 新建子模块可能被加入 `.idea/misc.xml` 的 ignoredFiles（表现为删除线/无 Maven 图标，Maven 面板右键 Unignore 解决）

### 2026-08-22（代码审查修正，未提交）
- 代码审查结论：网关端口统一 9090（此前配置 8084 与文档不一致已修正）、common 基类 fill 注解、`RefreshTokenService` 单设备会话模式；后端 11 模块编译 + 测试全绿（JDK 21 mvnw），前端 vue-tsc + vite build 通过
- 修正 auth-service / task-service 的 application.yml 头部注释复制粘贴错误（误写为「文档服务 document‑service」）
- 新增 **ADR-008**：Refresh Token 单设备会话策略（详见决策记录）
- **Refresh Token 用户索引键独立前缀** `agent-doc-workbench:auth:refresh:user:`（`REFRESH_TOKEN_USER_INDEX_PREFIX`）：索引键与 token 键空间隔离，消除数字 userId 与随机 token 撞键隐患
- **common-mybatis-plus starter 新增 `CommonMetaObjectHandler`**：createdAt（INSERT）/ updatedAt（INSERT_UPDATE）由应用层自动填充，`@TableField(fill=...)` 注解真正生效（原依赖 DDL 默认值兜底）
- 遗留提示：`validateAndGetRefreshToken(Long)` 暂未使用（预留）；common-core 引入 micrometer `StringUtils` 未显式声明依赖（当前靠传递依赖编译通过）

### 2026-08-22（代码规范对齐重构，未提交）
- 按代码规范整改 8 个模块的包结构与魔法值，**编译 + 全部测试通过（BUILD SUCCESS）**：
  - **pojo 父包**：entity/dto/vo 统一收归 `pojo` 下——auth/document/task 实体 → `pojo.entity`；auth 请求对象 → `pojo.dto`（改名 `RegisterRequestDTO` / `LoginRequestDTO` / `RefreshRequestDTO`），响应对象 → `pojo.vo`（`AuthResponseVO` / `UserVO`，原 `UserDto` 小写违规）；common 基类 → `common.pojo.entity`。record 字段名未改，**API JSON 契约不变**
  - **enums 包**：`ErrorCode` → `common.enums`；新增 `auth.enums.UserStatus`（ENABLED/DISABLED + `isEnabled()`）消除 `user.setStatus(1)` 魔法值
  - **config 包**：`JwtProperties` / `SecurityConfig` → `auth.config`（gateway 与 common starter 原本已合规）
  - **annotation 包**：`RequireLogin` / `RequirePermission` → `common.annotation`
  - **constant 包**：common 新增 `JwtConstant`（JWT claim 键名/scope/Bearer 签发解析共用，防拼写漂移）；auth 新增 `AuthConstant`（RSA 位数 2048 / refresh token 字节数 48 / PEM 标记）
  - **其余归位**：`JwtService`→service、`PingController`→controller、`GlobalExceptionHandler`→handler、`RedisUtils`/`JwtTokenParser`→utils、`CommonMetaObjectHandler`→handler；`web` 下两个 Filter 与 `security` 下 `PermissionInterceptor` 保留原位
  - **魔法值清理**：`response.setStatus(401)`→`HttpServletResponse.SC_UNAUTHORIZED`、JWT claim 键名/scope/`"Bearer"`/RSA 位数/`byte[48]`/PEM 标记全部常量化；代码库无 AOP 代码，不建 aop 包
- 追加代码规范 9-13 条（详见「开发规范 → 后端代码规范」清单，全后端强制）：
  - **规则 9（字段注释/@Schema）**：auth 的 7 个 pojo + UserStatus 枚举、document/task 共 9 个实体所有字段统一加 `@Schema(description=...)`；common-core 纯库（BaseEntity 系、PageParam）用 javadoc，不引 springdoc
  - **规则 10（PageParam）**：新增 `common-core/pojo/dto/PageParam`（pageNum 默认 1 / pageSize 默认 10 + `validate()` 校验，pageSize 上限 100）；MyBatis-Plus `Page` 转换 `toPage()` 待 Phase 2 首个分页接口出现时再定落位
  - **规则 11（Feign）**：暂不建模块，规范已固化（starter 命名 `common-feign-spring-boot-starter` + common-core `feign` 包 + `XXXXFeign` 接口），Phase 2 首个跨服务调用时再建
  - **规则 12（类转换入实体）**：`UserEntity.toVO()` 与 `RegisterRequestDTO.toEntity(String passwordHash)`（密码编码仍由 Service 准备），`AuthService` 的私有 `toDto` 已删除、注册字段搬运收敛到实体类
  - **规则 13（禁止手写 getter/setter）**：VO/DTO/Entity 全部使用 Lombok（实体 `@Data`、DTO/VO 用 record），仅 `PageParam` 曾手写 4 个访问器已改为 `@Data`；保留手写仅限框架接口实现（如 `getOrder()`/`setApplicationContext()`）与枚举取值
- 环境注意：命令行 `JAVA_HOME=D:\jdk8`（JDK 8），编译需显式 `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'`；Maven 写 `target\` 状态文件与文件删除受 DSH 沙箱限制，构建/清理需以全权限运行

### 2026-08-22（网关 starter 依赖更新，未提交）
- 网关依赖替换：`spring-cloud-starter-gateway` → `spring-cloud-starter-gateway-server-webflux`（Spring Cloud 2025.0.0 弃用旧坐标，消除启动时 `spring-cloud-starter-gateway is deprecated` 警告；本项目网关为 WebFlux 服务端，新坐标为直接替代，版本仍由 `spring-cloud-dependencies` BOM 管理），网关模块编译 + 6 测试通过

### 2026-08-22（模型管理 + Token 统计设计定稿，未提交）
- **model 表引入**（Flyway `V2__model_and_token_stats.sql` 已执行，14 张表）：`model` 模型元数据表（厂商 / model_key / 预估价格，不存密钥）+ `agent.model_id` 逻辑外键；业务渲染变化：Agent 绑定模型、任务执行透传 `model_key` 给外部 MCP‑Server、Token 明细 JOIN 模型名渲染、新增模型管理页；聚合维度无模型维度（v0.2 再评估）
- **Token 统计三表架构**：`token_usage_detail`（真相源，无条件落库）/ `token_usage`（历史日聚合，折线图截止昨日）/ `token_daily_snapshot`（当日快照，今日卡片仅展示）；`token_usage` 重构为通用 `obj_id` + 唯一键 `uk_dim_obj_date`
- **设计决策已定稿**（详见 `docs/database-design.md`）：任务级熔断本地累计 `task.tokens_used` + 结束对账补偿，空间 / Agent 级 v0.1 实时 SUM 明细（v0.2 Redis 计数器）；凌晨聚合 Spring `@Scheduled` + Redisson 锁（v0.2 迁 XXL‑Job），聚合幂等；今日快照懒加载异步触发 + 3min 节流 + 手动刷新 + 跨零点收尾快照
- **`V3__token_stats_indexes.sql`**（未执行）：`token_usage(space_id, usage_date)`、`token_daily_snapshot(space_id, snapshot_date, created_at)`（替换旧索引）
- 实体同步：`AgentEntity` + `modelId`；`TokenUsageEntity` 三列收敛为 `objId`；3 张新表实体待 Phase 2

### 2026-08-22（Phase 1 合并入 main + README 进度同步）
- Phase 1 后端地基经 Gitee PR !2 合并入 main；README（中英）同步进度：状态徽章 Phase 1 ready、分支说明、快速开始提示、路线图 Phase 1 标记已完成、新增「当前进度」小节、文档导航补充 PHASE1/PHASE2 交接文档
- `docs/development-plan.md` 更新头部状态（Phase 0/1 已完成）并为 Phase 1 补充实际交付说明（common 5 子模块 / auth 闭环 / gateway 限流与 OpenAPI / 14 张表 / 规范整改）
- 下一步：Phase 2（前端鉴权接入、文档空间 CRUD API 等）

## 八、决策记录（ADR，倒序追加）

### ADR-008：Refresh Token 单设备会话策略（2026-08-22）
- 结论：Refresh Token 采用**单设备模式**；Redis 主映射 `agent-doc-workbench:auth:refresh:{token} -> userId`，反向索引独立键空间 `agent-doc-workbench:auth:refresh:user:{userId} -> token`（`RedisKeyConstants.REFRESH_TOKEN_USER_INDEX_PREFIX`，与 token 键隔离防撞键）；`store()` 时先读取并删除旧会话（旧 token + 索引）再写入新令牌对；`revoke(String)` 双向删除；新增 `revoke(Long)` 按 userId 撤销全部会话（登出/改密场景）
- 理由：小团队场景单账号多端并发登录易造成会话混乱与安全风险；单设备实现简单，登出/改密可一键全端下线，配合 7 天 TTL 足够 v0.1 使用
- 后果：同一账号新登录会立即使旧 Refresh Token 失效（旧 Access Token 至多 30 分钟自然过期）；用户索引键空间与 token 键空间已隔离（随机 token 为纯数字亦不会撞上 userId 索引）；若后续需多设备并存，改回独立 token 映射并去掉旧会话删除即可

### ADR-007：Redis 键前缀统一 + 自定义 RateLimiter（2026-08-21）
- 结论：所有 Redis 键以 `agent-doc-workbench:` 开头（`RedisKeyConstants` 常量统一）；限流键经自定义 `ProjectRedisRateLimiter`（前缀 `agent-doc-workbench:rate`，键格式 `agent-doc-workbench:rate.{routeId.id}.{tokens,timestamp}`）实现
- 理由：本机共享 Redis 实例与多项目共存，裸键会冲突；Spring Cloud Gateway 4.3.0 已移除 RedisRateLimiter 的 key-prefix 配置（字节码硬编码 `request_rate_limiter`），`spring.cloud.gateway.redis-rate-limiter.prefix` 与 filter args 均实测无效，KeyResolver 注入仅能让键名中间含工程名、无法实现真前缀
- 后果：网关维护一份复制自框架的令牌桶 Lua + 限流器实现（约百行），升级 SCG 时需回归验证；全局限流（100/s 桶 200）与登录限流（5/s 桶 10）通过独立 KeyResolver（`login:` 前缀 id）实现双桶隔离

### ADR-006：common 模块拆分为 core + 多 Starter（2026-08-21）
- 结论：`common` 拆为 `common-core`（纯库：Result/异常/常量/上下文/工具/权限注解/BaseEntity）+ 4 个职责单一 starter（web / springdoc / mybatis-plus / redis），服务按需依赖（gateway 仅依赖 core）
- 理由：纯库与自动装配分离，符合 Spring Boot starter 惯例；消除三服务重复（Ping/OpenAPI/MyBatis-Plus 配置/Redis 模板）；BaseEntity 依 11 表实情设计两层
- 后果：新公共能力按「core 纯库 / starter 装配」定位落位；MVC 与 WebFlux 装配严格隔离（optional 依赖纪律）；starter 必须显式声明自身依赖（Step 5 曾因传递依赖断裂致编译失败）

### ADR-005：Agent 接入抽象层（2026-08-19）
- 结论：定义 `AgentRuntime` 接口，v0.1 实现 `McpAgentRuntime`（Workbench 作为 MCP Client 主动驱动外部 Agent）
- 理由：屏蔽不同 Agent 实现差异，为 v0.2 多 Agent、v0.3 对外 MCP Server 预留扩展点
- 后果：需要自己维护任务状态机、预算监控与编码实现

### ADR-004：OAuth2/JWT 鉴权方案（2026-08-19）
- 结论：前端 Authorization Code + PKCE，外部 Agent 用 Client Credentials；JWT RSA RS256，Access 30 分钟 / Refresh 7 天
- 安全要点：Access Token 仅存内存、Refresh Token HttpOnly Cookie、JWK Set 公钥校验、权限 = scope + 角色 + 空间成员关系 + Agent 范围白名单

### ADR-003：模块划分与线程模型（2026-08-19）
- 结论：模块边界预设 `gateway/auth/document/agent/task/audit/common`，v0.1 仅拆 `gateway`、`auth`、`document`、`task`，`agent` 与 `audit` 先合并
- 线程模型：Gateway 用 WebFlux，业务服务统一 Spring MVC + MyBatis-Plus，不混用

### ADR-002：前端技术栈（2026-08-19）
- 结论：Vue 3 + TypeScript + Vite + Pinia + Element Plus + ProseMirror；文档以 Markdown 为主存储，ProseMirror JSON 辅助编辑与结构化 Diff
- 理由：轻量、社区成熟；Yjs 留到 v0.2 再启用，避免 v0.1 被实时协同基础设施拖慢
- 后果：需自研 markdown ↔ ProseMirror 转换与 Diff 审批组件

### ADR-001：后端技术栈（2026-08-19）
- 结论：Spring Boot 3.5 + Java 21 + Spring Cloud Gateway + MyBatis-Plus + MySQL + RabbitMQ + Redis/Redisson + MinIO；Agent 接入用 Spring AI MCP + 官方 MCP Java SDK
- 理由：贴合现有 Spring 技术栈与开源定位；MCP 适配交给 Spring AI，业务编排（审批、预算、变更）自研
- 后果：v0.1 依赖基础设施较多（Nacos/MySQL/RabbitMQ/Redis/MinIO），需 Docker Compose 本地一键启动
