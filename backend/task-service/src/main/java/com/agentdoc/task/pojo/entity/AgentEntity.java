package com.agentdoc.task.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 实体（外部 MCP Agent 配置）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent")
@Schema(description = "Agent 实体")
public class AgentEntity extends BaseLogicDeleteEntity {

    @Schema(description = "所属空间 ID")
    private Long spaceId;

    @Schema(description = "Agent 名称")
    private String name;

    @Schema(description = "Agent 描述")
    private String description;

    @Schema(description = "关联 oauth2_client 的 client_id")
    private String clientId;

    @Schema(description = "MCP 连接配置（Phase 3 加密存储）")
    private String mcpConfig;

    @Schema(description = "工具白名单（逗号分隔）")
    private String toolWhitelist;

    @Schema(description = "可读写文档范围（JSON）")
    private String docScope;

    @Schema(description = "Token 预算上限")
    private Long tokenBudget;

    @Schema(description = "状态：1 正常 / 0 禁用")
    private Integer status;

    @Schema(description = "创建人用户 ID")
    private Long createdBy;
}
