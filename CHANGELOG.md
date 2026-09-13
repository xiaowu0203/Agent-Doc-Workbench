# Changelog

本文件记录 Agent-Doc-Workbench 的用户可见变更。版本号遵循 [Semantic Versioning](https://semver.org/)，内容组织参考 [Keep a Changelog](https://keepachangelog.com/)。

## [0.1.0] - 待发布

首个开源发布候选，提供围绕文档操作、Agent 执行和人工审批构建的完整 Web 工作台。

### Added

- Space、成员、目录、草稿/正式文档、版本、回滚、归档与恢复。
- 正式文档 ChangeRequest、结构化 Diff、人工审批、冲突检测与版本化合并。
- Agent、模型、Skill、外部多 MCP、工具白名单和任务执行快照。
- `ALL_BOUND` 与 `ROUTER` 两种 Skill 选择模式，以及按需读取 Skill 指令和参考资料。
- 系统 Skill、Agent 模板、MCP 模板、空间安装、固定版本和显式升级。
- RabbitMQ 任务投递、A2A Agent Server、Workbench MCP Server 和真实模型调用链路。
- 平台超级管理员、平台用户与部门管理、空间 RBAC 和任务级 Capability。
- Token 预算、模型调用/工具调用审计、用量统计和任务执行详情。
- Vue 3 Web 界面，覆盖文档、Agent、任务、审批、Skill、MCP、权限和系统能力管理。
- GitHub Actions 后端与前端质量门禁。

### Security

- MCP 和模型凭证加密存储，列表、日志与执行快照不返回秘密明文。
- Skill ZIP 在解压前后执行大小、数量、路径、深度和压缩比校验；脚本只存储、不执行。
- 用户权限与 Task Capability 分离，Capability 绑定任务、Agent、空间、文档和动作范围。
- 正式文档写入必须经过人工审批，并使用版本条件更新阻止并发静默覆盖。

### Fixed

- 鉴权拒绝和非法路径参数分别返回 403、400，不再落入 500 兜底。
- Redis 空间投递锁按持有者标识原子释放，避免锁过期后误删其他实例持有的锁。
- 审批合并使用带 `baseVersion` 守卫的原子更新，避免并发覆盖正式文档。
- 修复既有单元测试环境依赖、表元数据初始化和不稳定的 AES-GCM 篡改测试。
- 修复 Windows `mvnw.cmd` 对普通 Maven 用户目录的空链接目标进行索引而无法启动的问题。
- 补齐 Maven 与 npm 的 Apache-2.0 项目元数据，并恢复 canonical Apache 2.0 许可证文本。
- 补齐 Nacos 3.2.2 容器必需的身份参数，并修正 Compose 健康检查端点。
- 使 Vite 开发代理正确读取 `VITE_GATEWAY_URL`，不再固定依赖本机 `9090` 端口。

### Known Limitations

- v0.1 每个任务由用户明确选择一个 Agent，不提供工作流或自动多 Agent 编排。
- 同一 Space 的任务可以并发执行；空间 Redis 锁只覆盖 A2A 投递阶段，文档写入依靠 `baseVersion` 乐观锁。
- Skill 脚本和资源只存储、读取，不在服务器上执行用户上传脚本。
- Nacos 随 Docker Compose 提供，但 v0.1 服务使用静态路由，尚未接入服务注册或统一配置。
- Docker Compose 只启动 MySQL、Redis、RabbitMQ、MinIO 和 Nacos；应用服务与前端需要按 README 单独启动。
- v0.1.0 以源码形式发布，不附带预构建 JAR、前端静态包或容器镜像。
- 从采用更早迁移编号语义的开发环境升级可能出现 Flyway checksum 冲突；v0.1.0 只保证当前 V1-V22 基线的全新部署路径。
