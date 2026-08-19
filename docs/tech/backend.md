# 后端技术栈

## 基础版本

| 项目 | 版本 |
| ---- | ---- |
| JDK | 21 |
| Spring Boot | 3.5.0 |
| Spring Cloud | 2025.0.0 |
| 构建工具 | Maven |
| 许可证 | Apache-2.0 |
| 数据库 | MySQL 5.7 |
| 字符集 | UTF8MB4 |
| 时间类型 | DATETIME |
| 删除策略 | 逻辑删除 |
| 主键策略 | 雪花 ID |

## 后端组件

| 分类 | 组件 | 用途 |
| ---- | ---- | ---- |
| Web | Spring Web | REST API |
| 校验 | Spring Validation | 参数校验 |
| 安全 | Spring Security 6 | 安全基础 |
| 安全 | Spring Authorization Server | OAuth2 授权服务器 |
| 安全 | Spring OAuth2 Resource Server | Gateway 与业务服务 JWT 校验 |
| 安全 | JWT（RSA RS256） | 非对称签名令牌 |
| 数据 | MyBatis-Plus 3.5.10 | 数据访问层 |
| 数据 | MySQL Connector/J | 数据库驱动 |
| 缓存 | Redis 7.x | 缓存、临时上下文、限流辅助 |
| 缓存 | Redisson | 分布式锁、分布式对象、任务互斥 |
| 消息 | Spring AMQP（RabbitMQ 3-management） | Agent 任务分发、异步事件 |
| 存储 | MinIO Java SDK | 文档、附件、Agent 生成文件 |
| 文档 | SpringDoc OpenAPI | 接口文档 |
| 工具 | Lombok | 减少样板代码 |
| 工具 | MapStruct | DTO / VO / Entity 转换 |
| 迁移 | Flyway | 数据库版本迁移（可选） |
| 测试 | JUnit 5 + Mockito | 单元测试 |

## 基础设施

| 组件 | 版本 | 用途 |
| ---- | ---- | ---- |
| Nacos | 3.2.2 | 服务注册、发现、统一配置 |
| Spring Cloud Gateway | — | 统一入口、JWT 鉴权、路由、跨域、限流 |
| RabbitMQ | 3-management | 任务分发、异步事件 |
| Redis | 7.x | 缓存、分布式锁 |
| MinIO | — | 对象存储 |
| XXL-Job | — | 定时任务 |
| ELK | 可选（非 v0.1 启动依赖） | 日志收集分析 |
| Docker Compose | — | 本地一键启动基础设施 |

## 模块划分

```
backend/
├── gateway-service       # Gateway、鉴权入口、路由、限流
├── auth-service          # 用户、OAuth2、JWT、客户端管理
├── document-service      # 空间、目录、文档、版本、Diff
├── agent-service         # Agent 配置、MCP 凭证、权限（v0.1 合并入主服务）
├── task-service          # Agent 任务、Token 预算、RabbitMQ 消费
├── audit-service         # 审计日志和操作记录（v0.1 合并入主服务）
└── common                # 公共 DTO、异常、响应、鉴权工具
```

### v0.1 优先实现

1. `gateway-service`
2. `auth-service`
3. `document-service`
4. `task-service`

`agent-service` 和 `audit-service` 初期作为业务模块合并在主服务中，接口稳定后再拆分。

### 线程模型约束

- **Gateway**：Spring WebFlux
- **Auth / Document / Task**：Spring MVC + MyBatis-Plus
- **严禁**：在业务服务中混用 WebFlux 和 MVC，避免线程模型、事务和 MyBatis 集成复杂度。

## v0.1 推荐组合

```
Spring Boot 3.5
├── Spring AI
│   ├── ChatClient（可选）
│   ├── Tool Calling（可选）
│   └── MCP Client
├── 官方 MCP Java SDK
├── Spring AMQP + RabbitMQ
├── MyBatis-Plus
├── Redis / Redisson
├── Spring Security OAuth2 Resource Server
└── 自定义 Agent Task Orchestrator
```

## Agent 抽象设计

### 核心接口

```java
public interface AgentRuntime {
    AgentExecutionResult execute(AgentExecutionContext context);
    void cancel(String executionId);
    AgentRuntimeStatus status(String executionId);
}
```

### 适配器体系

```
AgentRuntime
├── McpAgentRuntime          # v0.1，外部 MCP Agent
├── SpringAiAgentRuntime     # 可选，内置模型 Agent
└── AgentScopeRuntime        # v0.2，未来多 Agent
```

### McpAgentRuntime 职责

1. 校验 Agent 的空间、目录和文档权限；
2. 通过 MCP 获取允许读取的文档片段；
3. 通过 RabbitMQ 异步执行任务；
4. 监控 Token 使用量和任务预算；
5. 接收 Agent 输出；
6. 将输出转换成统一的 `ChangeRequest`；
7. 正式文档进入审批队列，草稿文档直接写入；
8. 记录任务日志、版本快照和审计记录；
9. 支持暂停、终止和超预算熔断。

### MCP 角色边界

- **两种模式**：Workbench 作为 MCP Server/API 服务（被动） vs Workbench 内部 MCP Client 主动调用外部 Agent
- **v0.1 主路径**：Workbench 内部作为 MCP Client，主动调用外部 MCP 驱动的 Agent
- v0.2：保留 AgentRuntime 抽象，评估多 Agent 编排
- v0.3：对外暴露 Workbench MCP Server，让 LangGraph、CrewAI、AgentScope 等外部框架调用本系统

### 设计原则

> Spring AI 负责适配，项目自身负责业务编排。审批、预算、文档变更等核心逻辑不放进通用 Agent 框架。
