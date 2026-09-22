package com.agentdoc.task.pojo.vo;

import com.agentdoc.task.enums.TaskLineageType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Replay 回放准入校验结果 VO。
 * <p>
 * task‑service 对指定源任务做回放可行性判定，告知上游 Evaluation 模块：是否满足回放条件、溯源链路信息、快照版本与哈希。
 * 只有 {@code replayable=true} 时，才允许创建回放任务；reasonCode 用于不可回放时的标准化错误码，便于前端展示与埋点统计。
 * 快照哈希 + schemaVersion 用于校验快照完整性、做结构兼容，保证回放时加载的输入/执行快照和评估时证据一致。
 * </p>
 */
@Schema(description = "Replay 准入结果")
public record ReplayEligibilityVO(
        @Schema(description = "是否可回放；true=准入通过，可以发起Replay")
        boolean replayable,

        @Schema(description = "不可回放原因编码，可回放时为null，用于埋点、告警、前端提示")
        String reasonCode,

        @Schema(description = "源任务ID，待回放的原始任务")
        Long sourceTaskId,

        @Schema(description = "源执行实例ID，对应Execution，可定位到单段隔离执行轨迹")
        Long sourceExecutionId,

        @Schema(description = "根任务ID，最顶层父任务，用于整条任务链溯源")
        Long rootTaskId,

        @Schema(description = "源任务的链路类型，区分普通任务/子任务/工具调用分支等", implementation = TaskLineageType.class)
        TaskLineageType sourceLineage,

        @Schema(description = "回放深度，当前任务距离根任务的链路层级，用于防止过深回放、控制成本")
        Integer replayDepth,

        @Schema(description = "输入快照结构版本号，评估回放双方按版本解析输入快照")
        Integer inputSnapshotSchemaVersion,
        @Schema(description = "输入快照完整性哈希，校验快照未篡改")
        String inputSnapshotHash,

        @Schema(description = "执行快照结构版本号，对应ExecutionArtifact执行产物快照")
        Integer executionSnapshotSchemaVersion,
        @Schema(description = "执行快照完整性哈希，和ExecutionArtifactVO.payloadSha256同源校验")
        String executionSnapshotHash
) {}