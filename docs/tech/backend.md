# 后端技术栈

## 基础版本

|     项目     |    版本    |
| :----------: | :--------: |
|     JDK      |     21     |
| Spring Boot  |   3.5.0    |
| Spring Cloud |  2025.0.0  |
|   构建工具   |   Maven    |
|    许可证    | Apache-2.0 |
|    数据库    | MySQL 5.7  |
|    字符集    |  UTF8MB4   |
|   时间类型   |  DATETIME  |
|   删除策略   |  逻辑删除  |
|   主键策略   |  雪花 ID   |

## 后端组件

| 分类 |                 组件                 |              用途              |
| :--: | :----------------------------------: | :----------------------------: |
| Web  |              Spring Web              |            REST API            |
| 校验 |          Spring Validation           |            参数校验            |
| 安全 |          Spring Security 6           |            安全基础            |
| 安全 |     Spring Authorization Server      |       OAuth2 授权服务器        |
| 安全 |    Spring OAuth2 Resource Server     |  Gateway 与业务服务 JWT 校验   |
| 安全 |           JWT（RSA RS256）           |         非对称签名令牌         |
| 数据 |         MyBatis‑Plus 3.5.10          |           数据访问层           |
| 数据 |          MySQL Connector/J           |           数据库驱动           |
| 缓存 |            Redis 5.0.14.1            |   缓存、临时上下文、限流辅助   |
| 缓存 |           Redisson 3.23.5            | 分布式锁、分布式对象、任务互斥 |
| 消息 | Spring AMQP（RabbitMQ 3‑management） |    Agent 任务分发、异步事件    |
| 存储 |            MinIO Java SDK            |   文档、附件、Agent 生成文件   |
| 文档 |          SpringDoc OpenAPI           |            接口文档            |
| 工具 |                Lombok                |          减少样板代码          |
| 工具 |              MapStruct               |     DTO / VO / Entity 转换     |
| 迁移 |                Flyway                |     数据库版本迁移（可选）     |
| 测试 |          JUnit 5 + Mockito           |            单元测试            |

## 基础设施

|         组件         |           版本           |                 用途                 |
| :------------------: | :----------------------: | :----------------------------------: |
|        Nacos         |          3.2.2           |       服务注册、发现、统一配置       |
| Spring Cloud Gateway |            —             | 统一入口、JWT 鉴权、路由、跨域、限流 |
|       RabbitMQ       |       3‑management       |          任务分发、异步事件          |
|        Redis         |         5.0.14.1         |            缓存、分布式锁            |
|        MinIO         |            —             |               对象存储               |
|       XXL‑Job        |            —             | 定时任务（v0.1 用 Spring `@Scheduled` + Redisson 锁，XXL‑Job 待 v0.2 集群化引入） |
|         ELK          | 可选（非 v0.1 启动依赖） |             日志收集分析             |
|    Docker Compose    |            —             |         本地一键启动基础设施         |

## 模块划分

> 说明：
>
> 1. `common` 为 Maven 聚合 Pom 工程，`<packaging>pom</packaging>`，本身不产出可执行 jar；
> 2. `common‑xxx‑spring‑boot‑starter` 均为纯技术基础设施 starter，仅装配 Bean、拦截器、配置类；**禁止存放业务 Controller / Service / Entity**；
> 3. 跨服务 DTO / Feign 契约放在 `common-core` 的 `com.agentdoc.common.feign` 包中；
> 4. gateway‑service 为 WebFlux，**仅依赖 common‑core，禁止引入任何 xxx‑spring‑boot‑starter，规避 Servlet / WebFlux 线程模型冲突**；
> 5. Agent 已拆分为独立 `agent-service`；`task-service` 保留任务编排、A2A Client、Workbench MCP Server、审批、Token 与审计。

```plaintext
backend/
├── gateway‑service       # Gateway、鉴权入口、路由、限流
├── auth‑service          # 用户、OAuth2、JWT、客户端管理、平台角色管理
├── document‑service      # 空间、成员、空间角色、目录、文档、版本、Diff
├── task‑service          # 任务编排、A2A Client、Workbench MCP Server、审批、Token、RabbitMQ消费
├── agent‑service         # Agent/Model配置、A2A Server、Spring AI Runtime、MCP Client
└── common                # Maven聚合Pom工程
    ├── common‑core                       # POJO、BaseEntity、Result、ErrorCode、异常、常量、雪花ID、Jwt工具、权限注解
    ├── common‑web‑spring‑boot‑starter
    ├── common‑springdoc‑spring‑boot‑starter
    ├── common‑mybatis‑plus‑spring‑boot‑starter
    ├── common‑redis‑spring‑boot‑starter
    ├── common‑feign‑spring‑boot‑starter
    └── common‑security‑spring‑boot‑starter
```

### 当前优先实现

1. `gateway‑service`
2. `auth‑service`
3. `document‑service`
4. `task‑service`
5. `agent‑service`

`agent‑service` 是独立的 Agent 运行中心；`audit` 仍归 task-service 负责。

### 当前权限实现（Phase 5）

- `auth-service` 维护平台角色及用户平台角色绑定，并提供 `/api/platform/roles` 的列表、详情、创建、修改和删除接口。接口统一要求 `PLATFORM_SUPER_ADMIN`；该角色由数据库初始化，受保护角色不可通过接口修改或删除。
- `document-service` 维护空间成员、空间角色和权限标识符。每个空间默认创建 `OWNER`、`EDITOR`、`VIEWER`，只有 `OWNER` 受保护；`VIEWER` 默认不能查看成员和角色。
- 业务 Controller 使用 Spring Security `@PreAuthorize` 声明接口入口权限，`SpacePermissionService` 负责当前用户的平台超级管理员特例、空间成员关系和权限标识符判定；Service 保留业务规则与事务边界。
- 平台超级管理员不是所有空间写操作的替代授权。除平台管理接口和约定的跨空间读取能力外，空间写入仍须满足对应空间权限。

### 线程模型约束

- **Gateway**：Spring WebFlux
- **Auth / Document / Task / Agent**：Spring MVC + MyBatis‑Plus
- **严禁**：在业务服务中混用 WebFlux 和 MVC，避免线程模型、事务和 MyBatis 集成复杂度。

## Agent / MCP / A2A 组合

```plaintext
Spring Boot 3.5
├── Spring AI ChatClient + Tool Calling
├── 官方 A2A Java SDK（agent-service Server / task-service Client）
├── 官方 MCP Java SDK（task-service Server / agent-service Client）
├── Spring AMQP + RabbitMQ
├── MyBatis‑Plus
├── Redis / Redisson
├── Spring Security OAuth2 Resource Server
└── Task A2A Orchestrator（task-service）
```

## 协议角色边界

- `task-service` → `agent-service`：A2A，用于任务发送、查询、取消和状态推送。
- `agent-service` → `task-service`：MCP，用于读取文档、读取任务上下文和提交变更提案。
- `agent-service` → LLM 厂商：Spring AI Provider API，用于模型推理。
- Task Capability 是 A2A 与 MCP 的任务级 Bearer 凭证；A2A Task/Push Config 状态在 agent-service 由 MySQL 持久化。

### 设计原则

> Spring AI 负责模型适配，官方 A2A/MCP SDK 负责协议适配，项目自身负责任务、审批、预算和文档变更编排。
