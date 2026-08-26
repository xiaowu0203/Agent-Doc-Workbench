# Agent-Doc-Workbench

> 面向个人/小团队的 Agent 活文档协作开源 Web 工作台
> 文档，作为 AI Agent 任务的唯一协作载体。

[![License](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE) ![Status](https://img.shields.io/badge/Status-Phase%204%20planned-blue)

[English](./README.en.md) | 简体中文

**仓库**
- Gitee：https://gitee.com/wu_hai123/agent-doc-workbench
- GitHub：https://github.com/xiaowu0203/Agent-Doc-Workbench
- 分支：main（Phase 0-3 稳定）· 下一阶段：Phase 4 Skill 管理

---

## 为什么做这个项目？

现有 AI 文档工具（Notion AI、WPS AI）允许 AI 直接改写文档，内容不可控、不可追溯；
编排框架（LangGraph、CrewAI）则把任务结果停留在内存里，无法沉淀。
Agent-Doc-Workbench 把「文档」作为 Agent 任务的协作载体，让每一次 AI 改动都可审核、可回滚、可溯源。

## 核心特性

- **草稿 / 正式双文档模式**：草稿区允许 Agent 自由编辑快速试错；正式文档禁止 Agent 直接改写，杜绝内容篡改
- **Diff 变更审批**：所有 Agent 修改统一生成结构化变更请求，支持全部接受 / 部分接受 / 拒绝 / 批注退回，合并后自动生成版本快照
- **Token 预算熔断**：任务级预算 + 空间全局预算，超限自动熔断，彻底解决多 Agent 成本失控
- **A2A + MCP 标准协议**：任务通过 A2A 分发到独立 Agent Server，Agent 通过 MCP 使用 Workbench 工具
- **Agent 权限管控**：任务级 Capability 绑定空间、文档和动作，Agent 不直接继承用户权限
- **全链路审计日志**：操作主体（人/Agent）、操作类型、关联任务，日志不可篡改
- **版本快照与回滚**：每次合并变更自动生成版本，一键回滚任意历史版本

## UI 效果图

| 登录页 | 工作空间首页 | Diff 审批页（核心） |
| --- | --- | --- |
| ![01-login](docs/ui-mockups/01-login.png) | ![02-workspace](docs/ui-mockups/02-workspace.png) | ![06-diff-review](docs/ui-mockups/06-diff-review.png) |

完整 8 页效果图见 [docs/ui-mockups/README.md](docs/ui-mockups/README.md)。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Spring Boot 3.5 · Java 21 · Spring Cloud 2025 · MyBatis-Plus · MySQL 5.7 |
| 消息/缓存 | RabbitMQ · Redis 7 · Redisson |
| 存储 | MinIO（对象存储） |
| 注册/配置 | Nacos 3.2.2 |
| Agent 接入 | Spring AI · 官方 A2A Java SDK · MCP Java SDK |
| 前端 | Vue 3 · TypeScript · Vite · Pinia · Element Plus · ProseMirror |
| 鉴权 | Spring Authorization Server · OAuth2 · JWT（RS256） |

详细选型与理由见 [docs/tech/](docs/tech/README.md)。

## 架构概览

```
前端 (Vue 3 + ProseMirror)
   │  OAuth2 / JWT
   ▼
Gateway (Spring Cloud Gateway · WebFlux)
   │
   ├── auth-service       用户、OAuth2、JWT
   ├── document-service   空间、目录、文档、版本、Diff 审批
   ├── task-service       Agent 任务、A2A Client、Workbench MCP Server、Token 账本
   └── agent-service      Agent/Model 配置、A2A Server、Spring AI Runtime、MCP Client
         │
         ├── A2A：task-service → agent-service
         └── MCP：agent-service → task-service
```

## 当前进度

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| Phase 0 | 工程基建：Git、Docker Compose、前后端骨架 | ✅ 已完成（2026-08-20） |
| Phase 1 | 后端地基：common 5 子模块、auth 鉴权闭环（JWT RS256 + JWKS）、gateway 路由/限流/OpenAPI 聚合、14 张表（含 Token 统计三表架构） | ✅ 已完成并合并入 main（2026-08-22） |
| Phase 2 | 文档核心：空间 / 文档 / 版本 / Diff 审批 | ✅ 已完成并合并入 main（2026-08-23） |
| Phase 3 | Agent 与任务：异步任务、真实 MCP、Token 熔断、审计 | ✅ 已完成并合并入 main（2026-08-26） |

架构设计文档见下方“文档导航”；阶段交接材料仅保留在本地工作区。

## 快速开始

> Phase 0-3 已合并入 main；下一阶段为 Phase 4 Skill 管理。

```bash
# 1. 启动基础设施（MySQL / Redis / RabbitMQ / MinIO / Nacos）
docker compose up -d
# 提示：本机已装 MySQL / Redis 时，可只启动其余三个：
# docker compose up -d rabbitmq minio nacos

# 2. 启动后端（Maven 多模块）
cd backend
./mvnw spring-boot:run -pl auth-service -am

# 3. 启动前端
cd frontend
pnpm install
pnpm dev
```

后端服务默认端口：Gateway `9090`、Auth `8081`、Document `8082`、Task `8083`、Agent `8084`。
前端环境变量模板见 `frontend/.env.example`；基础设施与敏感配置模板见 `.env.example`。

## 开发路线图

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| Phase 0 | 工程基建：Git、Docker Compose、前后端骨架 | 已完成 |
| Phase 1 | 后端地基：common、auth、gateway | 已完成 |
| Phase 2 | 文档核心：空间/文档/版本/Diff | 已完成并合并 |
| Phase 3 | Agent 与任务：异步任务、真实 MCP、Token 熔断、审计 | 已完成 |
| Phase 4 | Skill 管理：目录包、版本、Agent 绑定与运行时加载 | 待启动 |
| Phase 5 | 细粒度权限：角色绑定权限标识符、接口权限校验 | 待启动 |
| Phase 6 | 前端：核心业务、Skill、角色与权限管理页面 | 待启动 |
| Phase 7 | 闭环联调与测试 | 待启动 |
| Phase 8 | 开源发布准备 | 待启动 |

阶段路线仅作为本地工作记录；长期有效的技术约束以 `docs/tech/` 和下方架构设计文档为准。

## 文档导航

| 文档 | 说明 |
| --- | --- |
| [docs/Agent-Doc-Workbench 项目完整开发规划文档.md](docs/Agent-Doc-Workbench%20项目完整开发规划文档.md) | 产品规划：业务功能清单、MVP 范围、迭代里程碑 |
| [docs/tech/](docs/tech/README.md) | 技术栈定稿：后端、前端、鉴权方案 |
| [docs/common-modules.md](docs/common-modules.md) | Common 模块与基础设施架构 |
| [docs/database-design.md](docs/database-design.md) | 数据库设计与迁移约束 |
| [docs/agent-server-a2a-mcp-design.md](docs/agent-server-a2a-mcp-design.md) | Agent、A2A 与 MCP 架构 |
| [docs/ui-mockups/](docs/ui-mockups/README.md) | v0.1 全部页面 UI 效果图 |
| [CLAUDE.md](CLAUDE.md) | 项目记忆与协作规范（含 ADR 决策记录） |

## 开源计划

- License：Apache-2.0
- 目标：v0.1 可 clone 即跑，欢迎个人开发者、小团队试用与共建
- 规划：CONTRIBUTING 与安全说明将在 Phase 8 发布前补齐

## License

本项目采用 [Apache-2.0](LICENSE) 许可证。
