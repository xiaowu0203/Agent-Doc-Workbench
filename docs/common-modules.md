# common 模块说明（2026-08-21，包结构 2026-08-22 按代码规范更新）

> Agent-Doc-Workbench 公共能力模块：纯库 + 多个 Spring Boot Starter 的拆分说明。
> 相关决策见 CLAUDE.md ADR-006 / ADR-007；包结构遵循 CLAUDE.md「后端代码规范」1-13 条。

## 一、模块结构

```
backend/common/                        # 聚合 POM（com.agentdoc:agent-doc-common）
├── common-core/                       # 纯库（无自动装配，不依赖 springdoc，字段注释用 javadoc）
│   ├── api/           Result<T>
│   ├── exception/     BusinessException
│   ├── constant/      HeaderConstants / RedisKeyConstants / JwtConstant
│   ├── context/       LoginUser / TraceContext / UserContext
│   ├── id/            SnowflakeIdGenerator
│   ├── enums/         ErrorCode
│   ├── annotation/    @RequireLogin / @RequirePermission
│   ├── utils/         JwtTokenParser
│   ├── pojo/entity/   BaseEntity / BaseLogicDeleteEntity
│   └── pojo/dto/      PageParam（分页参数：pageNum/pageSize 默认 1/10 + validate()）
├── common-web-spring-boot-starter/    # Servlet Web 自动装配
│   ├── config/        CommonWebAutoConfiguration
│   ├── controller/    PingController（/api/{service}/ping）
│   ├── handler/       GlobalExceptionHandler
│   ├── web/           TraceIdFilter / UserContextFilter
│   └── security/      PermissionInterceptor
├── common-springdoc-spring-boot-starter/   # OpenAPI 模板（agent-doc.openapi.*）
├── common-mybatis-plus-spring-boot-starter # 分页插件 + 乐观锁（默认关）；CommonMetaObjectHandler 在 handler/
└── common-redis-spring-boot-starter/       # jsonRedisTemplate + RedisUtils（utils/，条件装配）
```

## 二、依赖矩阵

| 模块 | 依赖 |
| --- | --- |
| gateway-service | `common-core` only（WebFlux，其余 starter 全不引） |
| auth-service | core + web + springdoc + mybatis-plus + redis |
| document-service | core + web + springdoc + mybatis-plus（redis 按需） |
| task-service | core + web + springdoc + mybatis-plus + redis |

约束：
- **线程模型**：web starter 仅面向 Servlet MVC；gateway（WebFlux）不依赖任何 MVC starter
- **optional 纪律**：core 中 webmvc / servlet / mybatis-plus-annotation 均为 optional，避免污染 WebFlux 依赖树
- **starter 自包含**：每个 starter 必须显式声明自身用到的依赖（勿依赖传递）

## 三、BaseEntity 两层设计

| 基类 | 字段 | 适用 |
| --- | --- | --- |
| `BaseEntity` | id（@TableId ASSIGN_ID）+ createdAt | 全部 14 张表 |
| `BaseLogicDeleteEntity extends BaseEntity` | + updatedAt + @TableLogic deleted | 9 张常规业务表 |

- `TokenUsageEntity` / `AuditLogEntity`（流水/日志表，无 deleted/updated_at）→ 继承 `BaseEntity`
- `token_usage_detail` / `token_daily_snapshot`（V2 新增流水表，无 deleted/updated_at）→ 继承 `BaseEntity`（Phase 2 建实体）
- `model`（V2 新增，含 updated_at/deleted）→ 继承 `BaseLogicDeleteEntity`（Phase 2 建实体）
- `DocumentVersionEntity`（无 updated_at）→ 继承 `BaseEntity` + 自持 `@TableLogic deleted`
- 逻辑删除/雪花 ID 由字段注解承担，无需 yml 全局配置

## 四、Redis 键约定

**所有 Redis 键必须以 `agent-doc-workbench:` 开头**（多项目共享 Redis 实例隔离），通过 `RedisKeyConstants` 常量组合生成，禁止硬编码裸键：

| 用途 | 键 |
| --- | --- |
| Refresh Token | `agent-doc-workbench:auth:refresh:{token}` |
| 全局限流（100/s 桶 200） | `agent-doc-workbench:rate.{routeId}.{ip}.{tokens,timestamp}` |
| 登录限流（5/s 桶 10） | `agent-doc-workbench:rate.{routeId}.{login:ip}.{tokens,timestamp}` |

### 限流实现说明（重要）

Spring Cloud Gateway **4.3.0（2025.0.0）已移除 `RedisRateLimiter` 的 key-prefix 配置**：
- 前缀硬编码 `request_rate_limiter`（字节码级，`Config` 类无 keyPrefix 字段）
- `spring.cloud.gateway.redis-rate-limiter.prefix` 与 filter args `redis-rate-limiter.key-prefix` 均**实测无效**（静默忽略）

因此网关使用自定义 **`ProjectRedisRateLimiter`**（`gateway-service/.../config/`）：
- 复制框架令牌桶 Lua（`resources/scripts/request_rate_limiter.lua`，自包含）
- 键前缀 `agent-doc-workbench:rate`，`@Primary` 替换框架默认实现
- 双桶隔离：全局限流用 `clientIpKeyResolver`（纯 IP），登录限流用 `loginKeyResolver`（`login:` + IP）
- 升级 SCG 时需回归验证限流行为

## 五、新增 Starter 指南

1. 在 `backend/common/` 下新建 `common-xxx-spring-boot-starter/`，parent 为 `agent-doc-common`
2. 装配类置于 `com.agentdoc.common.config`，注册到 `META-INF/spring/...AutoConfiguration.imports`
3. 显式声明依赖；MVC 装配加 `@ConditionalOnWebApplication(type = SERVLET)`；提供 `@ConditionalOnMissingBean` 覆盖点
4. 更新 `common/pom.xml`（modules）、`backend/pom.xml`（dependencyManagement）、目标服务 pom（按依赖矩阵）
5. 父 POM `dependencyManagement` 统一版本；重新 `mvnw install` 后 IDE 才解析到新 artifact

## 六、IDE 注意事项（踩坑记录）

- **Maven 仓库不一致**：IDEA 用系统 Maven 3.8.4（settings.xml `localRepository=D:\maven\...\repository`），命令行 `mvnw` 用默认 `~/.m2`——IDE 解析不到新 artifact 时先检查仓库；统一方式：IDEA 设置 User settings file 留空，或 `backend/.mvn/maven.config` 指定 `-Dmaven.repo.local`
- **子模块不识别**：IntelliJ 可能把新建子模块加入 `.idea/misc.xml` 的 ignoredFiles（删除线/无 Maven 图标）——Maven 面板右键 Unignore，或直接编辑 misc.xml 删除记录
- 开发期修改 common 代码：IDE 识别 reactor 后自动联动；未识别时需重新 `mvnw install`
