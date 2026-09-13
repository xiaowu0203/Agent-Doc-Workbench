# 安全策略

Agent-Doc-Workbench 涉及模型凭证、外部 MCP、文档写入和任务级授权。请负责任地报告安全问题，不要在公开 Issue、讨论区或 Pull Request 中披露可利用细节、真实 Token 或用户数据。

## 支持范围

项目维护最新的 v0.1.x 补丁版本和 `main`；更早的预发布提交不单独提供安全更新。

## 私密报告漏洞

优先使用 GitHub 仓库 **Security** 页面提供的私密漏洞报告入口：

<https://github.com/xiaowu0203/Agent-Doc-Workbench/security>

如果该入口在你的账号或镜像仓库中不可用，可以创建一个不含漏洞细节的普通 Issue，请求维护者建立私密沟通渠道。不要在 Issue 中附带利用代码、密钥、数据库内容或可识别用户的信息。

报告建议包含：

- 受影响的提交、版本、模块和部署方式。
- 前置条件、最小复现步骤和实际影响。
- 是否涉及跨空间访问、权限提升、秘密泄露、文档覆盖或远程代码执行。
- 已验证的缓解方式；如有补丁，可在私密沟通建立后提供。

维护者会先确认问题是否可复现和影响范围，再协调修复与披露时间。项目当前不承诺固定响应 SLA，但会优先处理可能造成秘密泄露、越权写入或远程执行的问题。

## 重点安全边界

### 正式文档

Agent 不能直接覆盖正式文档。修改必须形成 ChangeRequest，经人工审批后合并，并使用 `baseVersion` 乐观锁阻止过期基线静默覆盖。草稿文档允许直接编辑，部署者应根据数据敏感度选择文档模式。

### 身份与权限

- 用户 JWT、平台角色、空间 RBAC 和 Task Capability 分别承担身份、平台管理、空间授权和任务执行授权，不能互相冒充。
- Task Capability 必须限制 Task、Agent、Space、Document 和 action，并在任务终止、失败或预算耗尽后拒绝继续调用。
- 前端权限只控制界面展示，服务端权限校验才是最终授权边界。

### Skill 包

- 上传的 ZIP 和 YAML 均视为不可信输入，需要校验大小、文件数、路径深度、压缩比、路径穿越和允许的目录结构。
- Skill 的 `scripts/`、`assets/` 当前只存储，不执行、不自动注入模型上下文。
- Skill 声明的工具、Agent 白名单和 MCP 绑定白名单共同收紧最终工具集合，Skill 不能扩大 Agent 权限。

### 外部 MCP 与模型凭证

- 外部 MCP 可以产生网络请求和外部副作用。仅连接受信任的服务，并使用最小工具白名单。
- MCP Token、模型 API Key 和任务 Capability 密钥必须通过环境变量或受控配置注入，不得写入仓库、日志、响应或执行快照。
- 持久化的 MCP 和模型凭证使用加密存储；备份、数据库导出和日志同样应按秘密处理。
- 外部 MCP 初始化或调用失败采用 fail-fast，避免能力缺失时静默降级产生不可预期结果。

更完整的实现说明见 [鉴权与安全方案](docs/tech/security.md)、[Agent 任务执行](docs/agent-task-execution-guide.md)、[Skill 渐进加载](docs/skill-selection-and-progressive-loading-design.md) 和 [外部 MCP 架构](docs/external-mcp-architecture-design.md)。

## 生产部署建议

- 使用 HTTPS，并设置 `SPRING_PROFILES_ACTIVE=prod`。
- 替换 MySQL、RabbitMQ、MinIO 等本地默认凭证，使用独立且可轮换的密钥。
- 为 JWT、Agent 配置和 Task Capability 使用不同密钥，不在多个环境间复用。
- 限制数据库、Redis、RabbitMQ、MinIO、管理端点和 MCP 出站网络的可访问范围。
- 定期备份并验证恢复流程；备份中可能包含加密凭证和敏感文档。
- 不要把仓库示例配置直接视为生产加固基线。

如果怀疑真实密钥已经泄露，应先在对应服务撤销或轮换密钥，再进行代码和历史扫描。删除当前文件中的值并不能使历史中已经暴露的密钥失效。
