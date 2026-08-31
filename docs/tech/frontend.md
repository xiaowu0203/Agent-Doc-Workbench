# 前端技术栈

## 核心框架

| 框架 | 用途 |
| ---- | ---- |
| Vue 3 | 前端框架 |
| TypeScript | 语言（strict: true） |
| Vite | 构建工具 |
| Vue Router | 路由与权限守卫 |
| Pinia | 状态管理 |
| Element Plus + @element-plus/icons-vue | UI 组件库 |
| Axios | HTTP 请求 |

## 编辑器（ProseMirror）

### 依赖包

| 包 | 用途 |
| ---- | ---- |
| prosemirror-state | 编辑器状态管理 |
| prosemirror-view | 编辑器视图渲染 |
| prosemirror-model | 文档模型 |
| prosemirror-schema-basic | 基础节点与标记 |
| prosemirror-schema-list | 列表节点 |
| prosemirror-tables | 表格支持 |
| prosemirror-markdown | Markdown ↔ ProseMirror 双向转换 |
| prosemirror-history | 撤销/重做 |
| prosemirror-keymap | 快捷键绑定 |

### 支持功能

标题、多级列表、表格、代码块、图片、超链接、文本样式调整。

### 文档存储三格式

| 格式 | 用途 | 优先级 |
| ---- | ---- | ------ |
| **Markdown** | 持久化存储、导入导出、Agent 读取 | 主存储 |
| ProseMirror JSON | 前端编辑、结构化 Diff 比较 | 辅助 |
| HTML | 展示与预览，按需生成 | 按需 |

> 不建议只存 HTML，否则后续 Agent 处理、Markdown 导入导出和版本比较都会变复杂。

## 协同编辑

| 技术 | 用途 | 启用版本 |
| ---- | ---- | -------- |
| Yjs | 协同编辑数据模型 | v0.2 |
| y-prosemirror | ProseMirror 与 Yjs 适配 | v0.2 |
| y-websocket | 实时同步 | v0.2 |
| REST + 轮询 | 简易同步 | **v0.1（当前）** |

v0.1 使用普通 REST 接口加轮询同步，保留协同编辑的数据结构，但不会被实时协同基础设施拖慢。

## Diff 审批

### 依赖

| 库 | 用途 |
| ---- | ---- |
| diff-match-patch | 文本级增删改比较 |
| prosemirror-changeset | ProseMirror 结构化变更跟踪 |
| 自定义组件 | 全部接受、部分接受、拒绝、修改后接受、批注退回 |

### 变更请求数据结构

```
变更请求
├── requestId
├── documentId
├── taskId
├── agentId
├── baseVersion
├── changes[]
│   ├── operation
│   ├── position
│   ├── oldContent
│   └── newContent
├── status
└── createdAt
```

结构化变更信息避免多人或多次 Agent 操作时直接覆盖最新文档。

## Markdown 与文件处理

| 库 | 用途 |
| ---- | ---- |
| markdown-it | Markdown 预览/渲染 |
| DOMPurify | 清理 HTML，防止 XSS |
| file-saver | Markdown / JSON 文件下载 |
| JSZip | 浏览器端组装 Skill 标准 ZIP 包 |

MinIO 文件通过后端生成临时签名 URL，前端不直接持有永久对象存储凭证。

## 数据可视化

| 库 | 用途 |
| ---- | ---- |
| ECharts | Token 用量、趋势和后续审计统计图表 |

ECharts 在用量页面开始开发时引入，不在前端基础层提前安装。

## 状态管理

```
stores/
├── app.ts           # 侧栏折叠、全局加载等少量应用状态
├── auth.ts          # 用户、Token、登录状态
├── workspace.ts     # 当前空间、成员、角色、有效权限
├── document.ts      # 文档树、当前文档、版本（文档页面接入时创建）
├── editor.ts        # 编辑器状态、保存状态（编辑器接入时创建）
├── task.ts          # 跨页面任务和执行状态（任务页面接入时创建）
├── approval.ts      # 跨页面 Diff 审批状态（审批页面接入时创建）
└── notification.ts  # 站内通知（通知能力具备后创建）
```

Store 按页面需要逐步创建，不为了目录完整提前增加空 Store。只有跨路由或需要统一生命周期的状态进入 Pinia；列表筛选、分页、抽屉开关和临时表单默认保留在页面或 composable 内。

## 测试与代码质量

| 工具 | 用途 |
| ---- | ---- |
| Vitest | 单元测试 |
| Vue Test Utils | 组件测试 |
| Playwright | 端到端测试 |
| ESLint | 代码检查 |
| Prettier | 代码格式化 |
| Husky + lint-staged | 提交前检查 |
| pnpm | 依赖管理 |

## 前端目录结构

```
frontend/
├── src/
│   ├── api/             # Axios 实例、Result 解包、认证刷新和错误模型
│   ├── assets/          # 图片、字体、全局资源
│   ├── layouts/         # 主布局、登录布局
│   ├── router/          # 路由和权限守卫
│   ├── stores/          # Pinia 跨页面状态
│   ├── shared/          # 无业务归属的组件、composable、常量、类型和工具
│   ├── features/        # auth/workspace/document/agent/skill/mcp/task/approval 等业务切片
│   ├── editor/          # ProseMirror 编辑器核心、schema 和转换逻辑
│   ├── diff/            # 可被审批和版本历史复用的 Diff 展示原语
│   ├── views/           # 轻量路由页，组合 feature 组件
│   ├── styles/          # 设计令牌、Element Plus 覆盖和全局样式
│   ├── App.vue
│   └── main.ts
├── public/
├── tests/
│   ├── unit/
│   └── e2e/
├── package.json
├── tsconfig.json
├── vite.config.ts
└── .env.example
```

## v0.1 页面（按优先级）

1. 登录页
2. 工作空间首页
3. 文档树和文档编辑页
4. Agent 配置页
5. Agent 任务创建页
6. Diff 审批页
7. 文档版本历史页
8. Token 用量和审计日志页

## Phase 5/6 权限页面与前端控制

Phase 5 的后端已提供平台角色与空间 RBAC，Phase 6 负责把权限能力接入页面和组件：

1. 空间成员与角色页：展示成员角色、角色权限标识符及变更操作；只有拥有 `member:read` / `role:read` 的用户显示对应内容，拥有 `member:manage` / `role:manage` 的用户显示编辑入口。
2. 平台角色管理页：对应 Auth Service 的 `/api/platform/roles`，仅当用户 JWT 的 `platformRoles` 包含 `PLATFORM_SUPER_ADMIN` 时显示入口和操作按钮。
3. 权限目录请求使用 `/api/document/spaces/{spaceId}/permissions`，必须携带当前空间上下文。
4. `OWNER` 是受保护的默认空间角色；`EDITOR`、`VIEWER` 可由有权限的用户调整，`VIEWER` 默认不显示成员和角色管理内容。

前端权限只负责路由、菜单和按钮显隐；接口请求仍必须携带 Access Token，并由后端 Controller 的 `@PreAuthorize` 和业务授权服务做最终判定。用户平台角色绑定目前通过数据库初始化，尚未提供用户平台角色管理页。部门管理和按部门统计属于后续迭代，不应在 Phase 6 页面中暗示为已实现后端能力。

## 前端技术栈汇总

```
Vue 3 / TypeScript / Vite / Vue Router / Pinia / Element Plus / Axios
ProseMirror（state/view/model/schema-basic/schema-list/tables/markdown/history/keymap）
prosemirror-markdown / Yjs（v0.2）/ diff-match-patch / prosemirror-changeset
markdown-it / DOMPurify / file-saver
JSZip / ECharts
Vitest / Vue Test Utils / Playwright / ESLint / Prettier / Husky / pnpm
```
