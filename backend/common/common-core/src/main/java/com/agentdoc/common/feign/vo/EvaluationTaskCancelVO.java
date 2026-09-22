package com.agentdoc.common.feign.vo;

/**
 * WorkerCapability 批量取消单项结果。
 * <p>
 * 批量取消评估任务接口中，单条任务的返回明细，标记该任务是否成功取消。
 *
 * @param taskId     评估任务ID
 * @param accepted   是否接受本次取消请求
 * @param status     取消后任务状态
 * @param reasonCode 结果原因码，失败时用于区分取消拒绝理由；成功可为null
 */
public record EvaluationTaskCancelVO(
        Long taskId,
        boolean accepted,
        String status,
        String reasonCode) {
}
