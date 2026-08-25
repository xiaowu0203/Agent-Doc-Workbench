# Spring AI Alibaba Agent Runtime 双实现迁移实施文档

> 文档用途：交给后续实现模型或开发者直接执行。
>
> 基线日期：2026-08-25。
>
> 目标：保留当前 A2A、MCP、任务编排、权限、审批、审计和持久化链路，仅为 `agent-service` 增加 Spring AI Alibaba Agent Framework Runtime；原自研 Runtime 完整保留并可配置回退。

## 1. 最终决策

采用“双 Runtime、单 SPI、配置切换”的渐进迁移方案：

```text
AgentExecutionApplicationService
              |
              v
     AgentExecutionRuntime
       |                  |
       |                  +-- SpringAiAlibabaAgentExecutionRuntime（新增）
       |
       +-- SpringAiAgentExecutionRuntime（现有自研实现，保留）
```

第一阶段使用应用级配置选择 Runtime，不修改数据库，不增加 Agent 级 `runtime_type` 字段。

```yaml
agent-doc:
  agent:
    runtime:
      type: ${AGENT_RUNTIME_TYPE:custom}
```

合法值：

- `custom`：现有 `SpringAiAgentExecutionRuntime`。
- `spring-ai-alibaba`：新增 `SpringAiAlibabaAgentExecutionRuntime`。

实现和验证期间默认值必须为 `custom`。全部验收通过后，才将默认值改为 `spring-ai-alibaba`。旧实现不删除。

## 2. 成功标准

完成迁移必须同时满足以下条件：

1. `agent-service` 可以通过配置在两个 Runtime 之间启动级切换。
2. `AgentExecutionApplicationService`、A2A Executor、A2A Controller、A2A Store、task-service 无需感知具体 Runtime。
3. 现有 `AgentExecutionRuntime` 接口和 `AgentRuntimeResult` 对上层保持兼容。
4. 新 Runtime 支持 OpenAI Chat、OpenAI-compatible、Anthropic Messages、Google Gemini。
5. 每个执行任务创建独立 MCP Client，并使用当前 Task Capability 设置标准 `Authorization: Bearer ...`。
6. MCP Client 在成功、失败、取消和超时路径都能关闭。
7. 非流式执行返回最终摘要；流式执行只向 A2A 转发文本增量，不重复发送最终文本，不向用户暴露 tool-call JSON 分片。
8. 最大 Agent 迭代次数、Token 预算、取消信号、模型输出上限均有明确实现或明确的权威补偿机制。
9. 多轮模型调用的 Token 用量累计后返回，不能只返回最后一轮用量。
10. 模型错误仍转换成项目统一异常语义，不能把 API Key、Capability 或完整请求体写入日志。
11. 原 Runtime 的现有测试继续通过；新 Runtime 增加对应契约测试。
12. 完整后端构建通过，依赖树中不存在意外的旧版 A2A 实现或同包重复类。

## 3. 严格范围

### 3.1 本次允许修改

- `backend/pom.xml` 中 Spring Boot、Spring AI、Spring AI Alibaba 的版本管理。
- `backend/agent-service/pom.xml` 中 Agent Framework 依赖。
- `backend/agent-service/src/main/java/com/agentdoc/agent/execution/**`。
- `backend/agent-service/src/main/resources/application.yml` 中 Runtime 选择配置。
- `backend/agent-service/src/test/java/com/agentdoc/agent/execution/**`。
- 与本次 Runtime 迁移直接相关的技术文档。

### 3.2 本次禁止修改

- task-service 自研任务编排和任务状态机。
- RabbitMQ 分发链路。
- A2A Client、Server、TaskStore、PushNotificationConfigStore、回调和定时对账逻辑。
- 当前官方 A2A Java SDK DTO 和协议边界。
- Workbench MCP Server 的业务工具实现。
- Task Capability JWT 的 claims、验签和 action 授权规则。
- AgentExecution、Task、TokenUsage、ChangeRequest、Audit 的业务状态与数据库语义。
- 文档审批和变更提交流程。

不得引入 Spring AI Alibaba A2A Starter、Nacos A2A、Graph 编排或 Admin 平台。本次只使用 Agent Framework 的单 Agent Runtime 能力。

## 4. 当前必须保留的外部契约

### 4.1 Runtime SPI

保留当前接口签名：

```java
public interface AgentExecutionRuntime {

    AgentRuntimeResult execute(
            AgentEntity agent,
            ModelEntity model,
            String instruction,
            AgentTaskInputDTO input,
            BooleanSupplier cancelRequested);

    default AgentRuntimeResult execute(
            AgentEntity agent,
            ModelEntity model,
            String instruction,
            AgentTaskInputDTO input,
            BooleanSupplier cancelRequested,
            Consumer<String> onTextDelta) {
        return execute(agent, model, instruction, input, cancelRequested);
    }
}
```

本次不要把 Spring AI Alibaba 的 `ReactAgent`、Graph State 或流式事件类型暴露到该接口。

### 4.2 Runtime 结果

保留：

```java
public record AgentRuntimeResult(String summary, TokenUsage tokenUsage) {
}
```

其中 `TokenUsage` 的 input、cached input、output 语义必须保持不变。框架无法提供 cached input 时应返回 `unavailable`，不得伪造为 0。

### 4.3 应用服务边界

`AgentExecutionApplicationService` 继续负责：

- 从 A2A Message 提取 Workbench DataPart。
- 按 Workbench Task ID 幂等。
- 加载并冻结 Agent、Model、Prompt。
- 写入 `agent_execution`。
- 映射 WORKING、COMPLETED、FAILED、CANCELED。
- 通过 A2A `AgentEmitter` 输出消息、Artifact 和 Token 元数据。

不要把以上职责迁入新 Runtime。

## 5. 依赖版本策略

### 5.1 推荐对齐版本

当前项目为：

```text
Spring Boot 3.5.0
Spring AI 1.1.8
A2A Java SDK 1.2.0.Final
```

Spring AI Alibaba `1.1.2.2` 的官方构建基线为：

```text
Spring Boot 3.5.8
Spring AI 1.1.2
Spring AI Alibaba 1.1.2.2
```

由于项目尚未上线，可以接受版本对齐。推荐方案：

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.8</version>
</parent>

<properties>
    <spring-ai.version>1.1.2</spring-ai.version>
    <spring-ai-alibaba.version>1.1.2.2</spring-ai-alibaba.version>
    <a2a-java-sdk.version>1.2.0.Final</a2a-java-sdk.version>
</properties>
```

优先使用 Spring AI Alibaba BOM 管理 Agent Framework 及其匹配的 Spring AI 版本。不要同时让两个 BOM 对同一批 Spring AI artifact 产生不可预测的覆盖关系。实现者必须通过 `dependency:tree` 确认最终版本；若选择保留显式 Spring AI BOM，则必须证明所有 Spring AI artifact 最终统一为 `1.1.2`。

推荐在根 `dependencyManagement` 中使用：

```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-bom</artifactId>
    <version>${spring-ai-alibaba.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

若该 BOM 已完整管理项目所需的 Spring AI artifact，则移除原先单独导入的 `spring-ai-bom`；不要同时依赖 Maven 导入顺序碰运气。调整后以 `dependency:tree` 和实际编译结果确认。

在 `agent-service` 增加：

```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-agent-framework</artifactId>
</dependency>
```

不要增加：

```text
spring-ai-alibaba-starter-a2a-nacos
spring-ai-alibaba-graph-core（除非 Agent Framework 必需的传递依赖）
spring-ai-alibaba-admin
spring-ai-alibaba-studio
spring-ai-alibaba-sandbox
```

### 5.2 A2A 依赖隔离

当前项目显式使用：

```text
org.a2aproject.sdk:a2a-java-sdk-*:1.2.0.Final
```

必须继续保留。Spring AI Alibaba 自己的版本管理中包含较旧的 A2A 坐标，不能让它替换当前 A2A 实现。

依赖调整后执行：

```powershell
./mvnw.cmd -f backend/pom.xml -pl agent-service -am dependency:tree
```

检查：

- `org.springframework.ai:*` 是否全部为同一版本。
- `com.alibaba.cloud.ai:spring-ai-alibaba-agent-framework` 是否为目标版本。
- 当前 `org.a2aproject.sdk:*:1.2.0.Final` 是否保留。
- 是否额外引入 `io.github.a2asdk` 的实现 jar。
- 是否存在相同 `org.a2aproject.*` 类的重复来源。
- MCP SDK 是否发生意外降级。

如发现 Agent Framework 传递引入旧 A2A 实现，应在 `agent-service/pom.xml` 对该传递依赖做精确 exclusion；不要删除当前 A2A SDK。

### 5.3 依赖阶段验收

在编写新 Runtime 前必须先完成：

```powershell
./mvnw.cmd -f backend/pom.xml -pl agent-service -am clean test
```

若对齐版本导致旧 Runtime 无法编译，应先做最小 API 兼容修改并保证旧测试恢复，再开始新 Runtime。禁止同时处理版本迁移和 Runtime 行为迁移，否则问题无法归因。

## 6. 配置与 Bean 选择

### 6.1 新增配置类型

建议新增：

```text
execution/runtime/AgentRuntimeType.java
execution/runtime/AgentRuntimeProperties.java
```

示意：

```java
public enum AgentRuntimeType {
    CUSTOM,
    SPRING_AI_ALIBABA
}
```

```java
@ConfigurationProperties(prefix = "agent-doc.agent.runtime")
public record AgentRuntimeProperties(AgentRuntimeType type) {

    public AgentRuntimeProperties {
        type = type == null ? AgentRuntimeType.CUSTOM : type;
    }
}
```

项目已有 `@ConfigurationPropertiesScan`，遵循现有配置扫描方式即可。

### 6.2 条件装配

现有 `SpringAiAgentExecutionRuntime` 增加条件：

```java
@Component
@ConditionalOnProperty(
        prefix = "agent-doc.agent.runtime",
        name = "type",
        havingValue = "custom",
        matchIfMissing = true)
public class SpringAiAgentExecutionRuntime implements AgentExecutionRuntime {
}
```

新增实现：

```java
@Component
@ConditionalOnProperty(
        prefix = "agent-doc.agent.runtime",
        name = "type",
        havingValue = "spring-ai-alibaba")
public class SpringAiAlibabaAgentExecutionRuntime implements AgentExecutionRuntime {
}
```

两个 Bean 在同一次启动中只能有一个生效。不要使用 `@Primary` 掩盖双 Bean 注入问题。

增加 ApplicationContext 测试：

- 无配置时只存在现有 Runtime。
- `custom` 时只存在现有 Runtime。
- `spring-ai-alibaba` 时只存在新 Runtime。
- 非法枚举值启动失败并提示配置错误。

## 7. 新 Runtime 的内部结构

保持实现简单，推荐拆为四个职责明确的组件：

```text
execution/runtime/alibaba/
  SpringAiAlibabaAgentExecutionRuntime.java
  TaskScopedMcpTools.java
  AlibabaRuntimeControl.java
  AlibabaRuntimeUsageCollector.java
```

当前代码将这些类放在 `execution/runtime` 包中；`TaskScopedMcpTools` 是两个 Runtime 共享的任务级 MCP 资源封装，不限定于 Alibaba Runtime。只有确实需要时才新增组件；不得把 Spring AI Alibaba Graph、工作流或多 Agent 抽象提前引入。

### 7.1 SpringAiAlibabaAgentExecutionRuntime

职责仅包括：

1. 执行前检查取消。
2. 校验模型工具调用能力。
3. 解密 API Key。
4. 获取或创建匹配 ModelEntity 的 Spring AI `ChatModel`。
5. 创建本次任务专属 MCP Client 和 ToolCallbacks。
6. 创建执行级控制器和用量收集器。
7. 构建 Spring AI Alibaba `ReactAgent`。
8. 执行非流式或流式调用。
9. 将最终输出和累计 Token 转换为 `AgentRuntimeResult`。
10. 关闭本次任务 MCP Client。

Runtime 不访问数据库。取消仍通过上层传入的 `BooleanSupplier` 获取。

### 7.2 TaskScopedMcpTools

把现有 Runtime 中以下逻辑提取为可复用、可关闭的任务级对象：

- 校验 `mcpServerUrl` 是绝对地址。
- 拆分 base URL 和 endpoint path。
- 创建 `WebClient`。
- 设置 `Authorization: Bearer <taskCapability>`。
- 创建 Streamable HTTP Transport。
- 创建并初始化 `McpSyncClient`。
- 使用 `SyncMcpToolCallbackProvider` 获取工具。
- 为每个 ToolCallback 保留取消包装。
- `close()` 关闭 MCP Client。

约束：

- 不允许使用应用启动时的全局 MCP Client，因为 Capability 是任务级短期凭证。
- 不允许缓存携带某个任务 Capability 的 MCP Client 或 ToolCallback。
- `toString()`、异常消息和日志不得输出 Capability。
- MCP 初始化失败也必须释放已创建资源。

旧 Runtime 已复用 `TaskScopedMcpTools`，因此两个 Runtime 共享 MCP URL 解析、Capability 鉴权、初始化取消/超时和资源释放语义；各自的模型调用循环仍保持独立。不要为消除其它少量重复而重构整个旧实现。

### 7.3 ChatModel 创建与复用

新 Runtime 需要从动态数据库配置构建 ChatModel：

- provider / adapterType。
- modelKey。
- baseUrl。
- 解密后的 API Key。
- configVersion。
- 单轮 max output tokens。

禁止使用配置文件里的全局 API Key 替代数据库中的加密配置。

推荐复用现有 `ModelChatModelCache` 的 `(modelId, configVersion)` 缓存语义。不要在新 Runtime 中复制一套永不淘汰的模型缓存。

允许采用以下两种方式，优先选择改动更小且测试更清楚的一种：

1. 提取一个公共 `SpringAiChatModelFactory`，三个现有 ModelAdapter 和新 Runtime 都通过它创建 ChatModel。
2. 在现有 Adapter 边界增加只读的 ChatModel 获取能力，让新 Runtime 复用当前 provider 创建和缓存逻辑。

无论采用哪一种，都必须满足：

- 旧 Runtime 的调用行为不变。
- OpenAI-compatible 仍支持自定义 baseUrl。
- Gemini Client 在缓存淘汰时仍关闭。
- API Key 不进入 `equals/hashCode/toString` 或缓存 key。
- configVersion 变化时不能复用旧客户端。

不要简单复制三套 provider builder 到新 Runtime；这会重新制造双份模型适配维护成本。

### 7.4 ReactAgent 构建原则

每次 AgentExecution 构建一个执行级 `ReactAgent`，至少传入：

- 稳定且不含密钥的 name，例如 `agent-<agentId>-execution-<workbenchTaskId>`。
- 当前 ChatModel。
- `promptService.systemPrompt(...)` 得到的系统提示词。
- 当前任务 MCP ToolCallbacks。
- 与 `agent.maxIterations` 对齐的模型/工具调用限制 Hook。
- Runtime 控制 Hook/Interceptor。

第一阶段不要启用：

- MemorySaver 或持久化 Saver。
- Graph workflow。
- 多 Agent 编排。
- Skills。
- Planning。
- 自动工具重试。
- Human-in-the-loop。
- 自动上下文压缩。

原因：这些能力会改变当前单次 A2A Task 的行为或与已有任务/审批状态产生第二套状态来源。先实现行为等价迁移，再单独评估增强能力。

具体 builder、Hook 和流式 API 必须以项目最终锁定的 `1.1.2.2` 编译结果为准，不要凭其他版本示例猜测方法名。若官方示例与依赖中的 API 不一致，以依赖 jar 的源码/Javadoc 和编译器为准。

## 8. 必须保留的运行语义

### 8.1 最大迭代次数

当前语义是：模型可以产生多轮工具调用，超过 `agent.maxIterations` 后在执行下一次工具前失败。

新 Runtime 使用 Spring AI Alibaba 的 `ModelCallLimitHook` 或等价机制实现。需要通过测试明确它限制的是“模型调用次数”还是“工具循环次数”。如果框架语义与当前不同，应在 `AlibabaRuntimeControl` 中补齐，而不是静默改变业务含义。

默认值继续使用当前 `AgentConstant.DEFAULT_MAX_ITERATIONS`。

### 8.2 取消

当前取消信号来自数据库轮询回调：

```java
BooleanSupplier cancelRequested
```

新 Runtime 至少在以下检查点调用：

- MCP 初始化前后。
- 每次模型调用前。
- 模型响应后。
- 每次工具调用前后。
- 流式文本处理期间。

检测到取消时继续抛出项目现有 `AgentExecutionCanceledException`，由 `AgentExecutionApplicationService` 映射为 CANCELED。

如果 Spring AI Alibaba 支持中断执行，应把外部取消转换为框架中断；但不能仅依赖内存中断，因为当前取消事实保存在数据库中。

### 8.3 超时

继续使用 `agent.executionTimeoutSeconds`。

区分：

- MCP 请求超时。
- 单次模型 HTTP 请求超时。
- 整个 Agent 执行总超时。

当前实现至少把该值用于 MCP request timeout。新 Runtime 不得比当前更弱。若框架调用本身没有总超时，使用受控的 Future/Reactor timeout 包裹执行，并在超时后触发取消、关闭 MCP Client、返回统一失败。

不要创建无法关闭的公共线程池；优先使用项目或框架已有调度器。

### 8.4 Token 预算

预算来源保持：

```java
Long tokenBudget = input.tokenBudget() == null
        ? agent.getTokenBudget()
        : input.tokenBudget();
```

新 Runtime 必须累计每次模型调用的：

- input tokens。
- cached input tokens（provider 提供时）。
- output tokens。

必须说明并实现两个层次：

1. Runtime 快速熔断：每轮模型调用前后检查累计用量，防止继续进入下一轮。
2. task-service 权威核算：任务完成/失败后仍使用当前 Token 明细和 SUM 对账机制。

若 Spring AI Alibaba 无法在每轮调用前动态设置“剩余 completion token”，允许第一阶段采用：

- 模型自身 max output tokens 作为单轮硬上限。
- 每轮后累计并检查任务预算。
- task-service 保持最终权威核算。

但不得只读取最终 AssistantMessage 的 usage，因为它可能只代表最后一轮。应通过 Hook、Advisor、Observation 或模型调用拦截点累计每轮 `ChatResponse` usage。

Provider 不返回 Token 时，可以继续使用现有估算器；估算值必须保持 `estimated=true`。无法获取也无法估算时应失败，不得把未知用量当作 0。

### 8.5 模型输出上限

继续使用 `model.maxOutputTokens`。如果 Spring AI Alibaba ReactAgent 接收执行级 ChatOptions，应在每次模型调用设置；否则通过 ChatModel 默认 options 或模型调用 Interceptor 设置。

不能把任务级 ToolCallbacks 放入被跨任务缓存的 ChatModel 默认 options，否则会泄漏 Capability 或错误复用工具集合。

### 8.6 错误翻译

保留现有 `ModelProviderException` 分类：

- authentication。
- rate limit。
- context length。
- timeout/network。
- transient/non-transient。

新 Runtime 捕获 Spring AI Alibaba 包装异常后，应向下查找 root cause，再复用当前异常分类逻辑。不要只返回 `RuntimeException.getMessage()`。

## 9. 流式输出设计

### 9.1 对外语义

流式 Runtime 只调用：

```java
onTextDelta.accept(textDelta);
```

必须过滤：

- tool-call 参数分片。
- ToolResult。
- Graph/Node 调试事件。
- Hook 内部消息。
- 最终完整文本的重复回放。

最终 `AgentRuntimeResult.summary` 必须是完整最终回答。

### 9.2 测试场景

- 单轮纯文本：增量拼接等于 summary。
- 先文本后工具调用再文本：只输出面向用户的文本。
- tool-call JSON 被多段返回：不泄漏到 `onTextDelta`。
- 模型仅在最后返回完整文本：不得同时发送增量和完整文本造成重复。
- 流式期间取消：停止后不再产生新 delta。

## 10. 实施步骤

### 阶段 0：保护工作区和建立基线

当前仓库可能存在未提交的 Runtime/A2A 修改。实现者必须：

1. 执行 `git status --short`。
2. 不得 reset、checkout、覆盖或删除现有未提交修改。
3. 只修改本文件第 3.1 节允许的范围。
4. 记录迁移前测试结果。

验证：

```powershell
./mvnw.cmd -f backend/pom.xml -pl agent-service -am test
```

### 阶段 1：完成版本对齐

1. Spring Boot 对齐到 3.5.8。
2. Spring AI 对齐到 1.1.2。
3. 加入 Spring AI Alibaba 1.1.2.2 BOM/依赖。
4. 保持 A2A Java SDK 1.2.0.Final。
5. 检查 dependency tree。
6. 修复旧 Runtime 因版本变化产生的最小编译问题。
7. 运行原有测试。

验收：旧 Runtime 在新依赖组合下编译和测试通过。

### 阶段 2：增加 Runtime 配置选择

1. 新增 Runtime type/properties。
2. 给旧 Runtime 增加条件装配，默认 `custom`。
3. 增加新 Runtime 空壳 Bean，先抛出明确的未实现异常。
4. 增加 ApplicationContext 选择测试。

验收：两种配置下都能正确选择唯一 Bean；默认仍运行旧实现。

注意：阶段完成提交前，不允许让 `spring-ai-alibaba` 配置在实际任务中静默返回假结果。

### 阶段 3：实现模型与 MCP 适配

1. 建立可复用 ChatModel 创建边界。
2. 复用当前模型缓存和 configVersion 失效语义。
3. 实现 TaskScopedMcpTools。
4. 验证 Capability Header。
5. 验证 MCP Client 全路径关闭。

验收：不调用 ReactAgent 的情况下，组件测试可以获得正确 ChatModel 和 MCP ToolCallbacks。

### 阶段 4：实现非流式 ReactAgent

1. 组合系统提示词和 instruction。
2. 注入 MCP tools。
3. 配置最大调用/迭代次数。
4. 实现取消检查。
5. 实现多轮 Token 累计。
6. 转换最终 summary 和 TokenUsage。
7. 复用统一异常翻译。

验收：纯文本、一次工具、多次工具、超限、取消、模型异常场景通过。

### 阶段 5：实现流式 ReactAgent

1. 使用框架流式 API。
2. 只转发文本增量。
3. 聚合最终 summary。
4. 累计所有模型轮次 usage。
5. 在流式事件边界检查取消。

验收：第 9.2 节全部通过。

### 阶段 6：契约与集成验证

使用相同场景分别运行 `custom` 和 `spring-ai-alibaba`：

| 场景 | 必须验证 |
|---|---|
| 纯文本回答 | summary 非空，状态 COMPLETED |
| 读取文档工具 | MCP Bearer 正确，工具返回进入下一轮模型 |
| 提交文档变更 | 仍创建 ChangeRequest，不直接修改正式版本 |
| 多轮工具 | 不超过 maxIterations，Token 累计 |
| 重复 A2A Send | 不重复执行，回放已有状态 |
| 执行前取消 | 不调用模型和工具，状态 CANCELED |
| 工具前取消 | 工具不执行，状态 CANCELED |
| 流式中取消 | 停止 delta，状态 CANCELED |
| API Key 错误 | FAILED，错误分类正确，不泄密 |
| Rate limit | retryable 分类保持 |
| MCP 401/403 | FAILED，不被误判为模型错误 |
| 超预算 | Runtime 停止后续轮次，task-service 最终核算 |
| MCP/模型超时 | 资源关闭，状态 FAILED |

### 阶段 7：切换默认值

只有全部验收通过后：

```yaml
type: ${AGENT_RUNTIME_TYPE:spring-ai-alibaba}
```

同时保留：

```text
AGENT_RUNTIME_TYPE=custom
```

作为启动级回退开关。

本阶段仍不删除旧 Runtime、ModelAdapter 或旧测试。

## 11. 测试清单

### 11.1 单元测试

新增或保留：

- `AgentRuntimeSelectionTest`
- `SpringAiAlibabaAgentExecutionRuntimeTest`
- `AlibabaRuntimeControlTest`
- `AlibabaRuntimeUsageCollectorTest`
- `TaskScopedMcpToolsTest`
- 原 `ProviderNeutralToolLoopTest`
- 原模型 adapter、stream accumulator、cache、exception translation 测试

至少覆盖：

- 预算为空、刚好用完、超过预算。
- provider usage 完整、部分缺失、完全缺失。
- cached token 可用和不可用。
- maxIterations 为 null、0、1、正常值。
- cancel 在各检查点返回 true。
- MCP URL 缺少 scheme/authority。
- Capability 不出现在异常和 `toString()`。
- 流式 delta 顺序和去重。

### 11.2 Spring Context 测试

分别以两种配置加载最小上下文，断言：

```text
custom              -> SpringAiAgentExecutionRuntime 1 个
spring-ai-alibaba   -> SpringAiAlibabaAgentExecutionRuntime 1 个
```

并断言 `AgentExecutionApplicationService` 只注入一个 `AgentExecutionRuntime`。

### 11.3 构建验证

按顺序执行：

```powershell
./mvnw.cmd -f backend/pom.xml -pl agent-service -am test
./mvnw.cmd -f backend/pom.xml test
./mvnw.cmd -f backend/pom.xml -pl agent-service -am dependency:tree
```

如果完整测试依赖 MySQL、Redis、RabbitMQ 等外部基础设施，应明确区分：

- 单元测试是否通过。
- Spring Context 测试是否通过。
- 需要基础设施的集成测试是否跳过以及原因。
- 尚未执行的真实 LLM/A2A/MCP E2E。

不得用“编译通过”代替完整验收。

## 12. 最小真实 E2E

项目尚未真实跑通过，因此本次迁移完成定义必须包含一次最小闭环：

```text
创建 Task
  -> RabbitMQ 分发
  -> task-service A2A SendMessage
  -> agent-service 新 Runtime
  -> 模型调用 Workbench MCP 读取工具
  -> 模型调用 propose_document_changes
  -> 创建 ChangeRequest
  -> A2A Push COMPLETED
  -> task-service 更新 Task 和 Token 明细
```

至少分别执行：

1. `AGENT_RUNTIME_TYPE=custom` 一次。
2. `AGENT_RUNTIME_TYPE=spring-ai-alibaba` 一次。

对比：

- Task 最终状态。
- A2A Task ID 和 context ID。
- AgentExecution 状态。
- Artifact summary。
- ChangeRequest。
- TokenUsageDetail。
- 审计记录。
- MCP 鉴权结果。
- 是否有重复回调或重复变更。

真实 E2E 未完成前，文档和交付说明必须写“Runtime 代码完成、真实闭环待验证”，不能宣称迁移完成。

## 13. 回退方案

代码回退不依赖数据库迁移，只需：

```text
AGENT_RUNTIME_TYPE=custom
```

然后重启 `agent-service`。

由于本次第一阶段不新增数据库字段，两套 Runtime 共用同一个 AgentExecution/A2A/Task 数据模型。

注意：运行中的任务不做 Runtime 热切换。配置变化只影响服务重启后新接收的任务。服务关闭前应按现有机制完成或中断正在执行的任务。

## 14. 明确不做的事情

- 不删除现有自研 Runtime。
- 不把自研任务编排改成 Spring AI Alibaba Graph。
- 不让 ReactAgent 直接更新 Task、ChangeRequest 或 TokenUsageDetail。
- 不改造 A2A 协议 DTO。
- 不切换到 Spring AI Alibaba A2A Starter。
- 不新增 Nacos。
- 不做 Agent 级 Runtime 数据库配置。
- 不做双 Runtime shadow execution；这会重复调用模型和写工具，既昂贵又可能产生两份 ChangeRequest。
- 不同时加入 AgentScope Java。
- 不顺手重构无关 Controller、Mapper、Entity 或公共 Starter。

## 15. 实现者交付格式

完成后必须提交一份简短实施报告，包含：

1. 实际采用的最终依赖版本。
2. 修改文件列表及每个文件的目的。
3. 两套 Runtime 的选择方式。
4. Token、取消、超时、流式输出分别如何实现。
5. 与本文件存在的偏差及理由。
6. 执行过的测试命令和结果。
7. 未执行的真实测试及所需环境。
8. 当前默认 Runtime。
9. 一条可直接使用的回退命令或环境变量配置。

## 16. 给实现模型的执行指令

可将下面内容连同本文件路径直接交给实现模型：

```text
请严格按照 docs/spring-ai-alibaba-runtime-migration-plan.md 实现。

开始前先读取仓库 AGENTS.md、该迁移文档、当前 git status，以及文档中列出的 Runtime、模型适配、A2A/MCP 设计文件。仓库可能存在用户未提交的修改，禁止 reset、checkout 或覆盖这些修改。

按阶段执行，每个阶段先验证再继续。只修改文档允许范围内的文件。保留现有自研 Runtime，新增 Spring AI Alibaba Runtime，通过 agent-doc.agent.runtime.type 选择唯一实现。不要修改 task-service 编排、A2A 业务链路、Task Capability、安全、审批和数据库状态语义；不要引入 Spring AI Alibaba A2A Starter、Graph 编排、Nacos、AgentScope 或额外功能。

遇到 Spring AI Alibaba 文档 API 与实际 1.1.2.2 依赖不一致时，以锁定依赖的源码/Javadoc和编译结果为准，不要猜 API。完成后运行迁移文档中的测试命令，并按“实现者交付格式”报告结果。真实 E2E 未执行时必须明确说明，不得宣称完整闭环已验证。
```

## 17. 实现参考资料

- Spring AI Alibaba 主仓库与版本说明：<https://github.com/alibaba/spring-ai-alibaba>
- Spring AI Alibaba `1.1.2.2` 根 POM：<https://github.com/alibaba/spring-ai-alibaba/blob/v1.1.2.2/pom.xml>
- Agent Framework 快速开始：<https://java2ai.com/docs/quick-start/>
- ReactAgent 工具与 MCP：<https://java2ai.com/docs/frameworks/agent-framework/tutorials/tools/>
- Hooks 与 Interceptors：<https://java2ai.com/docs/frameworks/agent-framework/tutorials/hooks/>
- 当前项目 A2A/MCP 设计：`docs/agent-server-a2a-mcp-design.md`
- 当前项目 Phase 3 交接：`docs/PHASE3-HANDOFF.md`
