# 技术栈总览

> 本目录为原《docs / 前后端技术栈.md》的拆分重组版，信息无丢失，组织更清晰。原文件可安全删除。

## 决策摘要

|    领域    |                           核心结论                           |
| :--------: | :----------------------------------------------------------: |
|    后端    | Spring Boot 3.5 + Java 21 + Spring Cloud 2025；Maven 多模块；业务微服务 gateway/auth/document/task/agent；task-service 负责任务编排与 Workbench MCP Server，agent-service 负责 Agent Server、Spring AI Runtime 与 MCP Client；Spring MVC + MyBatis‑Plus + MySQL；REST + A2A + MCP |
|    前端    | Vue 3 + TypeScript + Vite + Pinia + Element Plus + ProseMirror |
|    鉴权    | OAuth2 Authorization Code + PKCE + JWT（RSA RS256），外部 Agent 用 Client Credentials |
| Agent 接入 | Spring AI + 官方 A2A/MCP Java SDK，业务编排自研 |
|  基础设施  | Nacos + Spring Cloud Gateway + RabbitMQ + Redis 5.0.14.1 + MinIO，Docker Compose 一键启动；定时任务 v0.1 用 Spring `@Scheduled` + Redisson 锁，XXL‑Job 待 v0.2 集群化后引入 |

## 文档导航

|            文件            |                        内容                        |
| :------------------------: | :------------------------------------------------: |
|  [backend.md](backend.md)  | 后端版本、组件、基础设施、模块划分、Agent 抽象设计 |
| [frontend.md](frontend.md) |  前端框架、编辑器、Diff 审批、状态管理、目录结构   |
| [security.md](security.md) |           OAuth2/JWT 鉴权方案与安全规则            |

## 关键原则（必须遵守）

1. **Spring AI 只做适配，业务编排自研**：审批、预算、文档变更等核心逻辑不放进通用 Agent 框架。
2. **线程模型隔离**：Gateway 用 WebFlux；Auth / Document / Task 等业务服务统一 Spring MVC + MyBatis‑Plus，不混用。gateway‑service 仅依赖 common‑core，不引入任何 xxx‑spring‑boot‑starter。
3. **文档三格式**：Markdown 持久化为主，ProseMirror JSON 用于编辑与结构化 Diff，HTML 仅按需生成展示，不以 HTML 为主存储。
4. **协同编辑延后**：v0.1 用 REST + 轮询，Yjs WebSocket 实时协同留到 v0.2，避免拖慢 MVP。
5. **前端权限只是 UI 控制**：最终权限必须由后端校验。

## v0.1 落地优先级

- 后端模块：`gateway` → `auth` → `document` → `task` / `agent`
- 前端页面：登录 → 工作空间首页 → 文档树与编辑 → Agent 配置 → 任务创建 → Diff 审批 → 版本历史 → Token 用量与审计日志
- 许可证：Apache‑2.0
