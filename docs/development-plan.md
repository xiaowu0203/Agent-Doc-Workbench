# 开发路线图（v0.1 → 开源发布）

> 基于《Agent-Doc-Workbench 项目完整开发规划文档》与技术栈定稿（docs/tech/）
> 更新时间：2026-08-20 · 状态：Phase 0 已完成

## 总体节奏

```
Phase 0 工程基建 → Phase 1 后端地基 → Phase 2 文档核心 → Phase 3 Agent 与任务
→ Phase 4 前端 → Phase 5 闭环联调 → Phase 6 开源发布准备
```

估算：约 1 个月（单人兼职），每阶段产出可验证、可交付。

---

## Phase 0：工程基建（1-2 天）

**目标**：搭建可复现的开发环境与仓库骨架

- [x] 初始化 Git 仓库 + `.gitignore` + Apache-2.0 LICENSE + 根 README
- [x] `docker-compose.yml` 本地一键启动：MySQL 5.7 / Redis 7 / RabbitMQ 3-management / MinIO / Nacos 3.2.2
- [x] 后端 Maven 多模块骨架：`common` / `gateway-service` / `auth-service` / `document-service` / `task-service`（agent / audit 先并入 document 或独立模块，接口稳定后再拆）
- [x] 前端脚手架：Vite + Vue 3 + TypeScript(strict) + Pinia + Vue Router + Element Plus + ESLint + Prettier + Husky + Vitest
- [x] `.env.example`、开发环境变量模板

**验收**：Compose 配置可解析并支持一键启动全部中间件；前后端均可启动并展示默认页；前端 Git 提交钩子已配置。

## Phase 1：后端地基（3-5 天）

**目标**：公共能力与鉴权闭环

- [ ] `common` 模块：统一响应体、全局异常、雪花 ID、上下文工具、鉴权工具
- [ ] 数据库设计定稿：space / member / document / document_version / change_request / agent / task / token_usage / audit_log（逻辑删除、雪花 ID、UTF8MB4）
- [ ] `auth-service`：注册登录、JWT(RS256) 签发与校验、Spring Authorization Server、Refresh Token 机制
- [ ] `gateway-service`：路由、JWT 校验、跨域、限流
- [ ] SpringDoc OpenAPI 接入

**验收**：注册→登录→带 Token 调业务接口全链路可用；OpenAPI 文档可访问。

## Phase 2：文档核心（4-6 天）

**目标**：空间/文档/版本/Diff 数据闭环

- [ ] 空间、成员角色（所有者/编辑者/观察者）、树形目录、文档 CRUD
- [ ] 草稿/正式双文档模式：正式文档禁止 Agent 直改
- [ ] Markdown 存储 + 版本快照（合并变更自动生成版本）+ 一键回滚
- [ ] ChangeRequest 模型与审批队列接口（结构化 changes[]，防并发覆盖）
- [ ] 文档片段读取接口（按需加载、控 Token）

**验收**：REST API 全量文档管理 + 版本回滚演示可用。

## Phase 3：Agent 与任务（5-7 天）

**目标**：单 Agent MCP 接入 + Token 预算熔断

- [ ] `agent-service`：Agent 配置、MCP 凭证加密存储、权限范围（空间/目录/文档）、工具白名单
- [ ] `task-service`：任务状态机（待运行→运行中→已完成/已终止/异常），RabbitMQ 异步消费
- [ ] `AgentRuntime` 抽象 + `McpAgentRuntime` 实现（Spring AI MCP Client，Workbench 主动调用外部 Agent）
- [ ] 变更输出 → ChangeRequest 转换：正式文档入审批队列、草稿文档直写
- [ ] Token 预算：任务级上限 + 空间全局预算 + 熔断 + 四维度用量统计（空间/文档/任务/Agent）
- [ ] 审计日志：操作主体（人/Agent）、操作类型、关联任务、不可篡改

**验收**：对接一个真实/模拟 MCP Agent，走通「下发任务→Agent 读取片段→产出变更→熔断/预算生效」链路。

## Phase 4：前端（5-7 天）

**目标**：8 个核心页面（对应 docs/ui-mockups/）

- [ ] 登录页（账号 + OAuth2 入口）
- [ ] 工作空间首页（统计卡片、最近文档/任务）
- [ ] 文档树 + 编辑页（ProseMirror + prosemirror-markdown 双向转换，正式/草稿模式徽标）
- [ ] Agent 配置页（MCP 连接、权限、白名单）
- [ ] 任务创建页（执行模式、Token 预算、熔断开关）
- [ ] Diff 审批页（diff-match-patch + prosemirror-changeset，增删高亮、批注、接受/拒绝/退回）
- [ ] 版本历史页（时间线、对比、回滚）
- [ ] Token 用量与审计日志页（图表 + 明细表）

**验收**：8 页可交互（先用 Mock 数据），UI 与效果图一致。

## Phase 5：闭环联调（3-5 天）

**目标**：跑通完整人机协作闭环（规划文档第五章 8 步流程）

- [ ] 前后端联调：建空间→建文档→配 Agent→下发任务→Diff 审批→合并→版本/审计
- [ ] 集成测试：后端 Mockito + 前端 Vitest + Playwright e2e 核心链路
- [ ] 按 UI 效果图走查还原度，修复视觉/交互差异
- [ ] 边界场景：熔断、任务终止、并发变更、权限越权

**验收**：完整演示脚本可一键走查；核心链路有自动化测试覆盖。

## Phase 6：开源发布准备（2-3 天）

**目标**：合规、干净的 v0.1 开源版本

- [ ] CONTRIBUTING、安全说明、架构说明
- [ ] 敏感信息审计：无硬编码凭证/密钥，MCP 凭证走环境变量/加密存储
- [ ] 依赖许可证检查（Apache-2.0 兼容）
- [ ] v0.1 tag + CHANGELOG + 发布说明

**验收**：GitHub 公开仓库可 clone 即跑，README 双语指引可用。

---

## 依赖与风险

- 基础设施依赖较重（Nacos/MySQL/RabbitMQ/Redis/MinIO）：Phase 0 用 Docker Compose 一次解决
- MCP 生态变化快：Phase 3 以官方 MCP Java SDK + Spring AI 为基线，接口隔离
- v0.1 串行单 Agent：任务队列用 RabbitMQ 单消费者，天然满足「同一时间仅一个任务」
- 双文档/审批是核心差异：Phase 2-3 优先保证数据模型正确，不做过度设计
