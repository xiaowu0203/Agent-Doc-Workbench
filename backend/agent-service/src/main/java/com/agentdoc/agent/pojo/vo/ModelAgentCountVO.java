package com.agentdoc.agent.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 模型关联 Agent 数量。
 */
@Data
@Schema(description = "模型关联 Agent 数量")
public class ModelAgentCountVO {

    @Schema(description = "模型 ID")
    private Long modelId;

    @Schema(description = "引用该模型的 Agent 数量")
    private Long agentCount;
}
