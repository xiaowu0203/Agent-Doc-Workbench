# 参与贡献

感谢你愿意参与 Agent-Doc-Workbench。项目欢迎 Bug 修复、文档改进、测试补充和经过讨论的功能贡献。

## 开始之前

- 安全漏洞不要通过公开 Issue 报告，请遵循 [安全策略](SECURITY.md)。
- 较大的功能、架构或协议变更，请先创建 Issue，说明问题、方案、取舍和兼容影响。
- 一个 Pull Request 应聚焦一个目标；不要夹带无关重构、格式化或依赖升级。
- 当前稳定分支为 `main`。请从最新 `main` 创建描述性分支，不要直接向 `main` 推送。

## 开发环境

与 CI 对齐的环境为：

- JDK 21
- Node.js 22
- pnpm 10.24.0
- Docker 与 Docker Compose v2
- Maven Wrapper（仓库已提供，无需安装全局 Maven）

基础设施、后端和前端的启动方式见 [README](README.md#快速开始)。复制环境变量模板时不要把真实密码、模型密钥、MCP Token 或私钥提交到仓库。

## 代码与架构约束

- Controller 负责协议适配、校验和权限入口；业务用例与事务边界放在 Service。
- Java DTO/VO 后缀使用全大写形式，例如 `UserDTO`、`UserVO`。
- JSON 处理使用 `common-core` 的统一工具，不在业务代码中重复创建序列化配置。
- 正式文档必须经过 ChangeRequest 和人工审批；任何贡献都不能绕过该边界让 Agent 直接覆盖正式内容。
- 人类用户权限、空间 RBAC 和任务 Capability 是不同的授权层，不能互相替代。
- Skill 包中的脚本当前只存储、不执行；引入脚本执行能力需要独立安全设计与评审。
- 一个任务由用户明确选择一个 Agent 执行。v0.1 不实现自动多 Agent 编排。

详细设计与模块边界见 [技术文档索引](docs/tech/README.md) 和 [项目文档导航](README.md#文档导航)。

## 数据库迁移

- 已提交并执行的 Flyway 迁移视为不可变历史，禁止修改来迁就新代码。
- 当前最新迁移为 V22；新的结构或数据修正从 V23 及更高版本继续追加。
- 迁移应支持从全新数据库按顺序执行，不依赖手工 SQL。
- 数据结构变更必须同步更新 [数据库设计文档](docs/database-design.md)。

## 验证改动

后端完整测试：

```bash
cd backend
./mvnw -B -ntp -fae test
```

Windows PowerShell 或 Command Prompt 使用：

```powershell
cd backend
.\mvnw.cmd -B -ntp -fae test
```

前端质量门禁：

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm type-check
pnpm lint
pnpm test
pnpm build
```

开发过程中可以先运行受影响模块的定向测试，但提交 Pull Request 前应确保与改动相关的完整门禁通过。不要用“能够编译”代替行为测试。

## 提交与 Pull Request

提交信息采用：

```text
类型(范围): 简述
```

常用类型包括 `feat`、`fix`、`docs`、`refactor`、`test` 和 `chore`。示例：

```text
fix(document): 拒绝过期基线的并发合并
```

Pull Request 请说明：

- 要解决的问题及不处理的范围。
- 关键实现与设计取舍。
- 执行过的验证命令及实际结果。
- 数据库、配置、权限、安全和兼容性影响。
- UI 改动的必要截图。

提交前确认没有加入密钥、日志、构建产物、个人路径、测试数据库或本地工作文档。贡献一经提交，即表示你同意按本项目的 [Apache License 2.0](LICENSE) 提供该贡献。
