package com.agentdoc.common.feign.dto;

import java.util.List;

/**
 * task-service 请求 auth-service 签发任务能力 JWT 的内部契约。
 * <p>
 * 所有字段将被写入JWT的claim，作为后续A2A请求的校验基线；
 * 调用方请求时传入的上下文必须和令牌内claim完全匹配，否则鉴权失败。
 *
 * @param taskId                      工作台任务ID
 * @param agentId                     Agent实例ID，绑定本次执行所属Agent
 * @param spaceId                     工作空间ID，权限隔离边界
 * @param documentId                  目标文档ID，限定可操作文档范围
 * @param executionMode               执行模式，取值为 TaskExecutionMode 名称（LIVE / ISOLATED）
 * @param documentVersionSnapshot     文档版本快照号，绑定基线文档版本
 * @param documentContentSha256       文档内容SHA256哈希，校验文档基线未篡改
 * @param inputSnapshotSchemaVersion  输入快照Schema版本，用于快照序列化兼容
 * @param inputSnapshotHash           输入上下文快照哈希，核心防篡改校验字段
 * @param actions                     允许的细粒度操作动作集合，最小权限控制
 */
public record TaskCapabilityIssueDTO(
        Long taskId,
        Long agentId,
        Long spaceId,
        Long documentId,
        String executionMode,
        Long documentVersionSnapshot,
        String documentContentSha256,
        Integer inputSnapshotSchemaVersion,
        String inputSnapshotHash,
        List<String> actions) {
}
