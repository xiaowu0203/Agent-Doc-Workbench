package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_template")
@Schema(description = "系统 Agent 模板")
public class AgentTemplateEntity extends BaseLogicDeleteEntity {
    @Schema(description = "稳定技术名称") private String name;
    @Schema(description = "展示名称") private String displayName;
    @Schema(description = "模板说明") private String description;
    @Schema(description = "状态：0 停用 / 1 启用") private Integer status;
    @Schema(description = "下一个版本号") private Integer nextVersionNo;
    @Schema(description = "创建人用户 ID") private Long createdBy;
}
