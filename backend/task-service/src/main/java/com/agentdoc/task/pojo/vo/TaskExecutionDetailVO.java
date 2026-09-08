package com.agentdoc.task.pojo.vo;

import com.agentdoc.common.feign.vo.AgentExecutionAuditVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 任务执行详情聚合视图。
 */
@Schema(description = "任务执行详情")
public record TaskExecutionDetailVO(
        @Schema(description = "任务基础信息") TaskVO task,
        @Schema(description = "执行时 Agent 名称；执行尚未创建时回退当前名称") String agentName,
        @Schema(description = "任务 Token 消耗是否包含估算值") Boolean tokensEstimated,
        @Schema(description = "脱敏 Agent 执行审计；尚未分发时为空") AgentExecutionAuditVO execution,
        @Schema(description = "任务业务结果引用；尚未产生结果时为空") TaskOutputVO output) {
}
