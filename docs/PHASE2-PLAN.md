# Phase 2「文档核心」开发计划（2026-08-22）

> **状态说明（2026-08-23）**：Phase 2 已完成。本计划保留启动时的设计基线，其中 `X-User-*` / `UserContextFilter` / `JwtHeaderVerifyFilter` 身份方案已被模型 B（业务服务使用 Spring Security Resource Server 自行解析 JWT）替代；最终实现与 Phase 3 交接以 `docs/PHASE3-HANDOFF.md` 为准。

> 原始状态：**已评审确认，待开工**（以下内容为当时计划快照）
> 依据：`docs/PHASE2-HANDOFF.md` 第七/八/九节、`docs/development-plan.md` Phase 2、CLAUDE.md 开发规范
> 分支基线：`phase-2`（工作树干净，已推送 gitee / github）

## 一、目标与验收

- **目标**：空间 / 文档 / 版本 / Diff 数据闭环
- **验收**：REST API 全量文档管理 + 版本回滚演示可用
- **范围红线**：
  - v0.1 不做多 Agent / 编排 / A2A（数据库预留字段即可）
  - 审计日志写入、Token 聚合统计逻辑留 Phase 3（表已建）
  - 前端 UI 留 Phase 4（本阶段仅后端 API）
  - 正式文档禁止 Agent 直改：本阶段通过「权限模型 + 审批合并唯一通道」体现，Agent 实体 Phase 3 才接入

## 二、现状盘点（已核实）

| 项 | 现状 |
| --- | --- |
| 分支 | `phase-2` 已建并推送，工作树干净 |
| 表结构 | 5 张核心表（space / member / document / document_version / change_request）字段齐备，**无需新迁移** |
| 实体 | document-service 4 实体、task-service 5 实体已就绪；**缺 3 个新实体**（ModelEntity / TokenUsageDetailEntity / TokenDailySnapshotEntity） |
| Service/Controller | document-service、task-service **空白**（仅实体 + Mapper + 启动类） |
| 鉴权链路 | gateway 统一校验 JWT 并透传 `X-User-*` 头；common-web `UserContextFilter` 填充上下文 + `RequireLogin/RequirePermission` 注解已闭环 |
| 服务发现 | **未接 Nacos**，gateway 静态路由，无 Feign——首个跨服务调用在 Phase 2 出现 |

## 三、技术决策（已评审确认）

### 决策 1：业务服务鉴权加固 —— 方案 A（轻量）
- common-web-spring-boot-starter 内新增**可选** `JwtHeaderVerifyFilter`：配置 `agent-doc.security.jwks-url` 时才启用（默认关闭，兼容 Phase 1 既有服务）
- 职责：校验请求头 JWT（RS256 签名 + 过期），并与 `X-User-Id` 头比对一致性，防止绕过 gateway 直连业务服务端口伪造身份头
- 优点：不新建模块、不引入 Spring Security Resource Server 全套，改动最小
- 备选（已弃）：新建 common-security-spring-boot-starter——纵深防御更强但多一个模块维护，Phase 2 不做过度设计

### 决策 2：审批合并跨服务调用 —— 方案 A（完整闭环）
- 新建 `common-feign-spring-boot-starter`（兑现 CLAUDE.md 规范 11「首个跨服务调用出现时再建」）+ `DocumentFeign` 契约接口（落位 common-core 的 `com.agentdoc.common.feign` 包）
- Feign 直连静态 URL（`document-service` 地址配置化），**不引入服务发现**（保持 Phase 1 静态风格，v0.1 轻量）
- 合并接口：校验 baseVersion → 应用变更到正式文档 → version + 1 → 生成版本快照 → 返回新版本号
- 任务：审批通过后经 Feign 调用完成合并，Diff 闭环完整，验收达标

### 决策 3：PageParam.toPage() 落位
- 首个分页接口出现时（Step 4 版本列表），将 MyBatis-Plus `Page` 转换收敛到 common-core（落实规范 10 遗留项）

## 四、开发步骤（Step 0-8）

| Step | 内容 | 交付 / 验收 |
| --- | --- | --- |
| 0 | 基线确认：JDK 21、中间件可用、后端编译测试全绿 | 无代码产出 |
| 1 | **身份与权限骨架**：决策 1 落地（JwtHeaderVerifyFilter）；新增 `SpaceRole` 枚举（OWNER/EDITOR/VIEWER）+ document-service 空间成员权限校验工具 | 无 Token 401 / 伪造 X-User 头被拒 |
| 2 | **空间与成员**：空间 CRUD（创建自动成为 OWNER、我参与的空间列表、更新、禁用）；成员管理（加人 / 改角色 / 移除，最后一个 OWNER 不可移除，非 OWNER 不可管理成员） | curl：建空间→加成员→改角色→越权 403 |
| 3 | **文档核心**：CRUD（创建指定正式/草稿 + 父目录、重命名、移动防环、逻辑删除、归档 / 恢复、回收站列表）；树形目录接口；双模式写入约束（正式 / 草稿文档人可编辑，Agent 通道 Phase 3 走审批） | curl：建树→编辑→移动→归档→恢复 |
| 4 | **版本快照与回滚**：编辑保存自动生成 version_no 递增快照（事务内）；版本分页列表 / 详情；版本对比（简化文本级）；**回滚 = 生成新版本，不删历史快照**；baseVersion 乐观校验防并发覆盖；PageParam.toPage() 落位（决策 3） | curl：编辑→版本→回滚→再编辑，版本列表正确 |
| 5 | **ChangeRequest 模型与队列**（task-service）：补 3 个新实体（Model / TokenUsageDetail / TokenDailySnapshot，聚合逻辑留 Phase 3）；提交（结构化 changes[] + baseVersion）、分页查询（按空间 / 文档 / 状态）、审批（通过 / 拒绝 / 退回 + 批注）；状态机校验（仅待审批可流转） | curl：提交→查询→通过 / 拒绝 / 退回 |
| 6 | **审批合并闭环**：决策 2 落地（common-feign starter + DocumentFeign）；审批通过后 Feign 合并 → baseVersion 校验 → 应用变更 → version + 1 → 生成版本快照；冲突返回明确错误 | curl：审批通过→正式文档更新 + 新版本；冲突场景报错 |
| 7 | **文档片段读取**：`GET /documents/{id}/fragments?start=&length=` 按偏移读取 Markdown 片段（Phase 3 MCP 控 Token 用）；空间成员可读 | curl 片段读取 |
| 8 | **全链路验证 + 文档同步**：端到端脚本（注册→登录→建空间→文档树→编辑→版本→回滚→提交变更→审批→合并）；Service 层 Mockito 单测；更新 PHASE2 交接 / 路线图状态，必要时预写 PHASE3-HANDOFF | 全链路演示可用 |

## 五、代码规范遵循（强制）

- pojo 分层（entity / dto / vo 父包 `pojo`），DTO/VO 后缀全大写（`SpaceVO` 等）；请求入参 DTO、出参 VO
- 类转换全部收敛实体类：Entity 自带 `toVO()`/`toDTO()`，DTO/VO 自带 `toEntity()`；Controller/Service 禁止字段搬运
- 枚举进 `enums` 包（`SpaceRole` / `DocType` / `DocStatus` / `ChangeRequestStatus` 等）；常量进 `constant` 包；**禁止魔法值**
- 字段统一 `@Schema(description=...)`（common-core 纯库用 javadoc，不引 springdoc）
- 分页统一 `PageParam`（pageNum / pageSize + validate()），接口先校验
- 实体统一 Lombok `@Data`，禁止手写 getter/setter
- 逻辑删除 / 雪花 ID 由基类承担（常规表 `BaseLogicDeleteEntity`；document_version 继承 `BaseEntity` + 自持 `@TableLogic`，Token 流水表继承 `BaseEntity`）
- 网关仍仅依赖 common-core；改 common 后先 `mvnw install` 再编业务服务
- 不建审计写入（Phase 3 统一）；不新建业务 Controller 之外的魔法逻辑

## 六、风险与注意

- **Maven 双仓库**：IDEA 用系统 Maven 3.8.4（`D:\maven\...`），命令行 mvnw 用 `~/.m2`；新 artifact 解析失败先查仓库
- **DSH 沙箱**：构建 / 清理写 target 需全权限执行（workspace-write 或按需升级）
- **Flyway checksum**：已执行迁移（V1/V2/V3）不可修改；如确需表变更走 V4+ 增量脚本
- **本机中间件**：MySQL 5.7（JSON 类型可用、无降序索引）/ Redis 5.0.14.1；Docker 当前不可用
- **JDK**：命令行需显式 `$env:JAVA_HOME='C:\Program Files\Java\jdk-21'`（默认 JDK8）
- **端到端验证**：Windows curl 中文按 GBK 发送，需 `--data-binary @file` + `charset=UTF-8`（纯 ASCII 可规避）

## 七、预估与节奏

- 工期 4-6 天（单人兼职，与路线图一致）
- 每 Step 独立可验证，Step 2-4 完成后即满足验收「REST API 全量文档管理 + 版本回滚」
- Step 6 完成后满足「Diff 数据闭环」
- Phase 3 交接点：DocumentFeign 契约（含预留权限校验接口）、文档片段读取接口、ChangeRequest 审批队列、3 个新实体
