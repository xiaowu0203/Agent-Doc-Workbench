package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_template_mcp")
@Schema(description = "Agent 模板版本的 MCP 模板引用")
public class AgentTemplateMcpEntity extends BaseEntity {
    @Schema(description = "Agent 模板版本 ID") private Long templateVersionId;
    @Schema(description = "系统 MCP 模板 ID") private Long mcpTemplateId;
    @Schema(description = "固定的 MCP 模板版本 ID") private Long mcpTemplateVersionId;
    @Schema(description = "默认远端工具白名单 JSON") private String toolWhitelistJson;
}
