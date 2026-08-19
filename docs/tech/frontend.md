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

MinIO 文件通过后端生成临时签名 URL，前端不直接持有永久对象存储凭证。

## 状态管理

```
stores/
├── auth.ts          # 用户、Token、登录状态
├── workspace.ts     # 当前空间、成员、角色
├── document.ts      # 文档树、当前文档、版本
├── editor.ts        # 编辑器状态、保存状态
├── task.ts          # Agent 任务和执行状态
├── approval.ts      # Diff 审批队列
└── notification.ts  # 站内通知
```

## 测试与代码质量

| 工具 | 用途 |
| ---- | ---- |
| Vitest | 单元测试 |
| Vue Test Utils | 组件测试 |
| Playwright / Tiptap | 端到端测试 |
| ESLint | 代码检查 |
| Prettier | 代码格式化 |
| Husky + lint-staged | 提交前检查 |
| pnpm | 依赖管理 |

## 建议的前端目录结构

```
frontend/
├── src/
│   ├── api/             # Axios 实例和接口请求
│   ├── assets/          # 图片、字体、全局资源
│   ├── components/      # 通用组件
│   ├── composables/     # Vue composables
│   ├── layouts/         # 主布局、登录布局
│   ├── router/          # 路由和权限守卫
│   ├── stores/          # Pinia 状态
│   ├── types/           # TypeScript 类型
│   ├── views/           # 页面
│   ├── editor/          # ProseMirror 编辑器
│   ├── diff/            # Diff 展示和审批组件
│   ├── utils/           # 工具函数
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

## 前端技术栈汇总

```
Vue 3 / TypeScript / Vite / Vue Router / Pinia / Element Plus / Axios
ProseMirror（state/view/model/schema-basic/schema-list/tables/markdown/history/keymap）
prosemirror-markdown / Yjs（v0.2）/ diff-match-patch / prosemirror-changeset
markdown-it / DOMPurify / file-saver
Vitest / Vue Test Utils / Playwright / ESLint / Prettier / Husky / pnpm
```
