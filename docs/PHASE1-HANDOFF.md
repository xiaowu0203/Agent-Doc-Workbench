# Phase 1 交接文档（2026-08-20）

> 用途：供新会话（Agent / 协作者）快速进入状态，开始 Phase 1 后端地基开发。
> 生成依据：当前仓库 `main` 分支（合并提交 `37d53d6`，Phase 0 已完成）。

## 一、项目一句话

面向个人/小团队的 **Agent 活文档协作 Web 工作台**：文档是 Agent 任务的唯一协作载体，
Agent 禁止直接改写正式文档，所有修改生成 Diff 变更请求，经人工审批后合并，并支持版本回滚、
Token 预算熔断与全链路审计。

## 二、Phase 0 已完成内容（现状基线）

- 基础设施编排：`docker-compose.yml`（MySQL 5.7 / Redis 7 / RabbitMQ 3-management / MinIO / Nacos 3.2.2，含持久化卷与健康检查）
- 环境变量模板：根 `.env.example`、`frontend/.env.example`
- 后端 Maven 多模块骨架：`common` / `gateway-service` / `auth-service` / `document-service` / `task-service`（JDK 21 / Spring Boot 3.5.0 / Spring Cloud 2025.0.0）
- 前端脚手架：Vite 6 + Vue 3.5 + TypeScript(strict) + Pinia + Vue Router + Element Plus + ESLint + Prettier + Husky + lint-staged + Vitest
- 文档：双语 README、`docs/development-plan.md`（Phase 0 已勾选）、CLAUDE.md 项目记忆

## 三、当前目录结构

```
Agent-Doc-Workbench/
├── backend/                          # Maven 多模块（父 POM：com.agentdoc:agent-doc-workbench）
│   ├── common/                       # 公共库：Result<T>、BusinessException（无业务逻辑）
│   ├── gateway-service/              # Spring Cloud Gateway（WebFlux），端口 9090
│   ├── auth-service/                 # 认证服务（Spring MVC），端口 8081
│   ├── document-service/             # 文档服务（Spring MVC），端口 8082
│   ├── task-service/                 # 任务服务（Spring MVC），端口 8083
│   ├── mvnw / mvnw.cmd / .mvn/       # Maven Wrapper（3.9.16）
│   └── pom.xml                       # 父 POM：模块聚合 + Spring Cloud BOM 管理
├── frontend/                         # Vite + Vue3 前端（pnpm 管理）
│   ├── src/
│   │   ├── views/HomeView.vue        # 默认首页（Phase 0 就绪页）
│   │   ├── router/index.ts           # 路由（当前仅 /）
│   │   ├── stores/app.ts             # Pinia 示例 store
│   │   └── styles/main.css
│   ├── .husky/pre-commit             # 提交钩子：cd frontend && pnpm lint-staged
│   ├── package.json / pnpm-lock.yaml
│   └── vite.config.ts                # /api 代理 → http://localhost:9090（网关）
├── docs/                             # 规划、技术栈、UI 效果图、路线图
├── .env.example                      # 基础设施与敏感配置模板
├── docker-compose.yml
└── CLAUDE.md                         # 项目记忆与协作规范（含 ADR-001~005）
```

## 四、环境与运行

### 本地环境（已确认）
- JDK 21：`C:\Program Files\Java\jdk-21`（默认 JAVA_HOME 指向 JDK8，需手动切换）
- Maven：系统 3.8.4，或使用 `backend/mvnw`（3.9.16）
- Node 18.20.6 + pnpm 10.24.0
- 中间件：本机 MySQL 5.7、Redis 5.0.14.1；Docker 容器运行 RabbitMQ / MinIO / Nacos（本地 Docker Hub 不可达，拉新镜像需配置加速器）

### 常用命令
```bash
# 后端构建与测试（需 JAVA_HOME 指向 JDK 21）
cd backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'   # PowerShell 示例
./mvnw test                                       # 全模块测试
./mvnw spring-boot:run -pl auth-service -am       # 单服务启动

# 前端
cd frontend
pnpm install
pnpm dev                                          # http://localhost:5173
pnpm lint / pnpm build / pnpm test
```

### 服务端口
| 服务 | 端口 |
| --- | --- |
| Gateway | 9090 |
| Auth | 8081 |
| Document | 8082 |
| Task | 8083 |
| 前端 dev | 5173 |

## 五、技术栈定稿（详见 docs/tech/）

- **后端**：JDK 21 / Spring Boot 3.5.0 / Spring Cloud 2025.0.0 / MyBatis-Plus 3.5.10 / MySQL 5.7（UTF8MB4、DATETIME、逻辑删除、雪花 ID）/ Flyway（可选）
- **安全**：Spring Security 6 / Spring Authorization Server / OAuth2 Resource Server / JWT（RSA RS256）
- **基础组件**：Redis 7.x + Redisson / RabbitMQ 3-management（Spring AMQP）/ MinIO / Nacos / XXL-Job（可选）
- **Agent 接入**：Spring AI（MCP Client, ChatClient/Tool Calling 可选）+ 官方 MCP Java SDK
- **其他**：SpringDoc OpenAPI / Lombok / MapStruct / JUnit5 + Mockito
- **前端**：Vue 3 + TS strict / Vite / Pinia / Vue Router / Element Plus / Axios / ProseMirror（Phase 4 接入）/ Vitest + Playwright

## 六、关键架构约束（必须遵守）

1. **线程模型**：Gateway 用 **WebFlux**，业务服务（Auth/Document/Task）统一 **Spring MVC + MyBatis-Plus**，严禁混用
2. **模块边界**：v0.1 仅拆 `gateway` / `auth` / `document` / `task`；`agent` 与 `audit` 合并进主服务，接口稳定后再拆
3. **Agent 权限**：外部 Agent 不直接拥有用户权限，必须绑定空间、文档范围与工具白名单（OAuth2 Client Credentials）
4. **鉴权方案**（ADR-004）：前端 Authorization Code + PKCE；Access Token 仅存内存（30 分钟），Refresh Token HttpOnly Cookie（7 天）；RSA RS256 + JWK Set
5. **Agent 抽象**（ADR-005）：核心接口 `AgentRuntime.execute/cancel/status`，v0.1 实现 `McpAgentRuntime`（Workbench 作为 MCP Client 主动调用外部 Agent）
6. **文档安全**：正式文档禁止 Agent 直改，草稿区可自由编辑；改动统一走 ChangeRequest 审批

## 七、Phase 1 任务清单（下一阶段，原样摘自 docs/development-plan.md）

**目标**：公共能力与鉴权闭环 —— **已于 2026-08-21 全部完成并实测通过** ✅

- [x] `common` 模块：统一响应体（Result/ErrorCode）、全局异常、雪花 ID、上下文工具（LoginUser/TraceContext）、鉴权工具（JwtTokenParser/@RequireLogin/PermissionInterceptor）
- [x] 数据库设计定稿：11 张表一次定稿（space / member / document / document_version / change_request / agent / task / token_usage / audit_log / user / oauth2_client），逻辑删除、雪花 ID、UTF8MB4；Flyway `V1__init.sql` 由 auth-service 托管
- [x] `auth-service`：注册/登录/刷新/登出/me + JWT(RS256) 签发校验 + JWKS 分发 + Refresh Token 存 Redis（可撤销）；**采用精简 JWT + Spring Security 方案（未启用 Spring Authorization Server，用户已确认）**
- [x] `gateway-service`：静态路由 + JWT 校验过滤器 + CORS + 限流（RedisRateLimiter 全局 100/s + 登录单独 5/s，经网关实测 429）
- [x] SpringDoc OpenAPI 接入：网关聚合三服务 Swagger UI

**验收（已实测通过）**：注册→登录→带 Token 调业务接口全链路可用；OpenAPI 文档可访问；登录并发限流 429 生效。

## 八、Phase 1 建议开发顺序

1. `common` 补齐公共能力（响应体/异常/雪花 ID/上下文/鉴权工具）+ 单元测试
2. 数据库模型与建表 SQL（Flyway 或初始化脚本）+ 实体/Mapper 骨架
3. `auth-service`：注册/登录/JWT 签发校验（先 JWT + Security，再接 Spring Authorization Server 与 OAuth2）
4. `gateway-service`：路由 + JWT 校验过滤器 + CORS + 限流（Spring Cloud Gateway 过滤器链）
5. SpringDoc OpenAPI 聚合 + 全链路手工验证

## 九、提交规范（沿用 CLAUDE.md）

- 未经用户明确要求，不执行 git commit / push / 分支创建切换
- 提交格式：`类型(范围): 简述`（feat / fix / docs / refactor / chore / test），中文优先
- 前端提交自动触发 husky → lint-staged → eslint/prettier；后端提交无钩子（可后续补充）
- 变更小而聚焦，遵守单一职责；重要决策记录到 CLAUDE.md「决策记录」章节

## 十、注意事项 / 已知环境限制

- 默认 `JAVA_HOME` 指向 JDK8，所有后端命令需先切换为 JDK 21
- Docker Hub 不可达：新建容器镜像需配置国内加速器；MySQL/Redis 建议继续使用本机服务
- 本机 Redis 为 5.0.14.1（技术栈文档写 Redis 7.x）：基础缓存/锁无影响，Phase 1 引入 Redisson 时留意兼容性
- `frontend/.env.example` 为 UTF-8 无 BOM，若用旧版 Windows 记事本编辑注意保持编码
- **Windows curl 发中文请求体会按 GBK 编码**，服务端 JSON 解析报 `Invalid UTF-8 middle byte`。需将 body 写入 UTF-8 文件后 `curl --data-binary @file`，并带 `Content-Type: application/json; charset=UTF-8`
- **限流验证需并发**：登录接口每次含 BCrypt+DB+Redis+RSA 签名，串行 curl 单次 >200ms、实际速率 <5/s，永远打不满 10 桶拿不到 429。需 `seq 1 30 | xargs -P 30 -I{} curl ...` 并发打才触发（实测 30 并发 → 23×429）。若只是抽查 Redis 限流 key，注意正确配置 5/10 时 key TTL=4s，几秒后查询自然消失属正常，不能据此判定配置未生效
- **Spring Cloud Gateway 2025.0.0 配置前缀废弃**：`spring.cloud.gateway.*` 已迁移为 `spring.cloud.gateway.server.webflux.*`（routes/default-filters/globalcors 全部在其下，`corsConfigurations` 改为 `cors-configurations`）。当前 gateway `application.yml` 已用新前缀，配置键告警已消除
- **遗留告警（已处理 2026-08-22）**：`spring-cloud-starter-gateway` 在 Spring Cloud 2025.0.0 被废弃，已切换为 `spring-cloud-starter-gateway-server-webflux`（同 BOM 管理，WebFlux 服务端直接替代），弃用警告消除，网关模块编译 + 6 项测试通过
