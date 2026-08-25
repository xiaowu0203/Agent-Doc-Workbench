package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent")
@Schema(description = "Agent 配置实体")
public class AgentEntity extends BaseLogicDeleteEntity {

    @Schema(description = "空间 ID")
    private Long spaceId;
    @Schema(description = "Agent 名称")
    private String name;
    @Schema(description = "Agent 描述")
    private String description;
    @Schema(description = "系统提示词")
    private String systemPrompt;
    @Schema(description = "模型 ID")
    private Long modelId;
    @Schema(description = "Token 预算上限")
    private Long tokenBudget;
    @Schema(description = "文档访问范围")
    private String docScope;
    @Schema(description = "最大工具迭代次数")
    private Integer maxIterations;
    @Schema(description = "执行超时时间（秒）")
    private Integer executionTimeoutSeconds;
    @Schema(description = "配置版本号")
    private Long configVersion;
    @Schema(description = "状态：0 禁用 / 1 启用")
    private Integer status;
    @Schema(description = "创建人用户 ID")
    private Long createdBy;
}
