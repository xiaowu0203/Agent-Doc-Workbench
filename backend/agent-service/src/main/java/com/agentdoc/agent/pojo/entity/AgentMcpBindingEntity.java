package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_mcp_binding")
@Schema(description = "Agent MCP 绑定实体")
public class AgentMcpBindingEntity extends BaseLogicDeleteEntity {
    @Schema(description = "Agent ID")
    private Long agentId;
    @Schema(description = "MCP Server ID")
    private Long mcpServerId;
    @Schema(description = "远端原始工具白名单 JSON")
    private String toolWhitelistJson;
    @Schema(description = "绑定是否启用")
    private Boolean enabled;
}
