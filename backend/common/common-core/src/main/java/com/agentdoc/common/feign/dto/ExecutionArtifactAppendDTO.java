package com.agentdoc.common.feign.dto;

/**
 * Agent Runtime 向 task-service 追加隔离执行产物的内部契约。
 * <p>
 * 仅 ISOLATED 隔离捕获模式使用，不产生真实文档变更；
 * 保存工具调用候选输出，用于快照哈希生成、回放与审计校验。
 *
 * @param executionId        Agent执行记录ID，归属本次执行会话
 * @param sourceTaskId       工作台顶层任务ID
 * @param sequenceNo         产物自增序号，保证捕获顺序稳定、快照可复现
 * @param sourceToolCallId   关联的工具调用ID，可为null；用于绑定原始工具调用记录
 * @param artifactType       产物类型，如 CHANGE_PROPOSAL / DRAFT_CHANGES
 * @param schemaVersion      payloadJson 的结构版本，用于序列化向前兼容
 * @param payloadJson        工具入参原始JSON报文
 * @param payloadSha256      payloadJson的SHA256哈希，防篡改，参与执行快照校验
 */
public record ExecutionArtifactAppendDTO(
        Long executionId,
        Long sourceTaskId,
        Integer sequenceNo,
        Long sourceToolCallId,
        String artifactType,
        Integer schemaVersion,
        String payloadJson,
        String payloadSha256) {
}
