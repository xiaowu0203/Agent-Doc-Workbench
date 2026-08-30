# Agent 任务执行流程

本文用两张流程图说明 Agent Doc Workbench 中一次 Agent 任务如何运行，以及 Skill、MCP 与 Tool 在实际执行中的关系。

当前 V0.1 采用手动触发模式：用户创建任务时明确选择一个 Agent；一个任务只由一个 Agent 执行。多个 Agent 的自动规划与编排属于后续演进范围。

## 1. Agent 任务完整执行流程

```mermaid
flowchart TD
    A["用户创建任务<br/>选择 Agent、目标文档、填写指令和可选预算"]
    B["创建前检查<br/>用户权限、Agent 状态、空间归属、文档范围、Token 预算"]
    C["生成任务记录<br/>状态：待运行"]
    D["签发任务专属能力令牌<br/>限定 Agent、Space、文档和允许动作"]
    E["发送到 RabbitMQ 任务队列"]
    F["任务消费者取出任务<br/>去重、加锁、状态改为已分发"]
    G["通过 A2A 协议把任务交给 Agent Service"]
    H["冻结本次执行配置<br/>模型、Prompt、Skill、工具、外部 MCP、预算"]
    I["选择本次使用的 Skill<br/>全部绑定或 Router 智能筛选"]
    J["创建 AgentExecution<br/>状态：已提交 → 运行中"]
    K["模型开始思考"]
    L{"这一轮需要调用工具吗？"}
    M["执行工具<br/>读文档、读 Skill、提交变更、调用外部 MCP"]
    N["把工具结果交回模型<br/>进入下一轮思考"]
    O["模型给出最终结果摘要"]
    P["AgentExecution 完成"]
    Q["通过 A2A 回调同步任务结果、Token 和状态"]
    R["Workbench 任务完成"]

    A --> B --> C --> D --> E --> F --> G --> H --> I --> J --> K --> L
    L -- "需要工具" --> M --> N --> K
    L -- "不再需要工具" --> O --> P --> Q --> R

    M -. "涉及正式文档修改" .-> S["生成待审批的 ChangeRequest"]
    S --> T{"人工审核"}
    T -- "通过" --> U["合并到正式文档<br/>生成新版本"]
    T -- "拒绝" --> V["结束，不修改正式文档"]
    T -- "退回" --> W["等待后续重新处理"]
```

可以把整个过程口语化地理解为：Workbench 先确认“谁能做、能碰哪些文档、最多花多少预算”，再把任务交给选定的 Agent。Agent 根据本次冻结的配置，让模型在“思考—调用工具—读取工具结果—继续思考”之间循环，直到输出最终结果。

其中，模型提交正式文档变更只会创建 ChangeRequest，不会直接改写正式文档。后续是否合并由独立的人工审批流程决定。

## 2. 一个具体任务如何执行

假设用户创建了以下任务：

- 任务内容：审查《支付回调接入说明》，并与 API Center 中的真实接口契约核对。
- 选择的 Agent：技术文档审查 Agent。
- Agent 绑定的 Skill：`technical-doc-review`、`api-contract-verification`，以及两个与本任务无关的 Skill。
- Agent 绑定的外部 MCP：API Center MCP。
- Agent 工具白名单：允许读取文档、读取 Skill 指令、查询 API Center，以及提交文档变更申请。

执行时，Router 不需要加载四个 Skill 的全部正文，只根据轻量目录选出与任务相关的两个 Skill。系统随后读取这两个 Skill 的完整指令，并计算本次真正可用的工具集合。

```mermaid
flowchart TD
    A["用户任务<br/>审查支付回调文档并核对 API Center"]
    B["Router 查看 4 个 Skill 的名称和激活描述"]
    C["选中 Skill 301<br/>technical-doc-review"]
    D["选中 Skill 302<br/>api-contract-verification"]
    E["计算工具权限<br/>Skill 声明 ∩ Agent 白名单 ∩ MCP 绑定白名单"]
    F["读取两个 Skill 的指令正文"]
    G["读取目标文档上下文和正文"]
    H["调用 API Center 查询真实接口契约"]
    I["模型比较文档和接口契约"]
    J["生成修改后的完整文档"]
    K["提交 ChangeRequest"]
    L["模型输出审查摘要和申请 ID"]
    M["任务完成<br/>正式文档仍未修改"]
    N["人工审批并合并"]

    A --> B
    B --> C
    B --> D
    C --> E
    D --> E
    E --> F --> G --> H --> I --> J --> K --> L --> M
    M -. "独立审批流程" .-> N
```

这里三个概念的分工是：

- **Skill**：描述“这类事情应该怎么做”的流程、规则和参考资料；它可以声明执行中需要哪些工具，也可以只包含操作规范而不依赖任何工具。
- **MCP**：把平台内部或外部系统的能力，以统一协议提供给 Agent。MCP 服务可以提供一个或多个 Tool。
- **Tool**：模型在某一轮中真正发起调用的原子能力，例如读取文档、查询接口契约或提交 ChangeRequest。

因此，Skill 不等同于 Tool。Skill 主要约束执行方法；当流程需要操作数据或外部系统时，模型再调用当前权限范围内的 Tool。Tool 的执行结果会返回给模型，模型据此决定继续调用工具还是结束任务。
