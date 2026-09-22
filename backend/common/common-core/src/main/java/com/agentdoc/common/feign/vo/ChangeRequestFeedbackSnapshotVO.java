package com.agentdoc.common.feign.vo;

import java.time.LocalDateTime;

/**
 * ChangeRequest 最终审批事实的脱敏、不可变导入投影。
 * <p>
 * 用于评估指标计算，保存审批结果快照；只导入审批结论与校验哈希，不携带完整明文评论内容，
 * 保证评估回放时审批事实不可篡改、可复现。
 *
 * @param changeRequestId 变更请求ID
 * @param spaceId         所属工作空间ID
 * @param taskId          关联工作台任务ID
 * @param executionId     生成该变更请求的Agent执行ID
 * @param status          审批状态（如已审核、撤回等）
 * @param resolutionType  审批结论类型：通过/拒绝等
 * @param reviewCommentHash 审批意见明文的SHA256哈希，用于校验评论未被篡改
 * @param reviewedBy      审批人用户ID
 * @param reviewedAt      审批时间
 * @param revisionNo      变更请求修订版本号
 * @param sourceHash      上游关联上下文哈希，用于链路溯源与一致性校验
 */
public record ChangeRequestFeedbackSnapshotVO(
        Long changeRequestId,
        Long spaceId,
        Long taskId,
        Long executionId,
        String status,
        String resolutionType,
        String reviewCommentHash,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        Integer revisionNo,
        String sourceHash) {
}
