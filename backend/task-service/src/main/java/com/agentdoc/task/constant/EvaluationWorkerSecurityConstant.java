package com.agentdoc.task.constant;

import java.util.Set;

/**
 * Worker鉴权相关安全常量
 * 仅这些入口由WorkerCapability专用验证器处理，不走OIDC用户身份鉴权链路
 * 配合 EvaluationWorkerSecurityConfig 里自定义 BearerTokenResolver 一起生效：
 * 当请求路径命中 PATHS、POST方法、携带 X_EVALUATION_WORKER_CAPABILITY 请求头时，
 * 会跳过Spring Security默认OIDC的Bearer Token解析，交由独立的 WorkerCapability 验证器做服务节点侧鉴权。
 * 注意：
 * 1. 是精确路径匹配，不是前缀匹配，子路径不会自动生效；新增接口需要手动加入该集合
 * 2. 这些接口属于 /api/task/internal 内部域，但当前网关未对这四个路径作专用封禁；
 *    请求仍可能经网关转发，下游必须独立验证WorkerCapability，不能依赖路径名称或网关代替鉴权
 * 3. 属于Evaluation Worker节点拉取评估状态、证据、文档变更、取消评估任务的专用通路
 */
public final class EvaluationWorkerSecurityConstant {
    /**
     * Worker节点可访问的接口路径集合
     * /api/task/internal/tasks/evaluation/status/query      查询评估任务状态
     * /api/task/internal/tasks/evaluation/evidence/query    查询评估证据数据
     * /api/task/internal/tasks/evaluation/document-changes/query 查询评估关联文档变更信息
     * /api/task/internal/tasks/evaluation/cancel           取消评估任务
     */
    public static final Set<String> PATHS = Set.of(
            "/api/task/internal/tasks/evaluation/status/query",
            "/api/task/internal/tasks/evaluation/evidence/query",
            "/api/task/internal/tasks/evaluation/document-changes/query",
            "/api/task/internal/tasks/evaluation/cancel");
    private EvaluationWorkerSecurityConstant() { }
}
