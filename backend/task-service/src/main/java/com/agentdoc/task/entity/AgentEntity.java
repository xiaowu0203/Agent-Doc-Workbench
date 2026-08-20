package com.agentdoc.task.entity;

import com.agentdoc.common.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 实体（外部 MCP Agent 配置）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent")
public class AgentEntity extends BaseLogicDeleteEntity {

    private Long spaceId;

    private String name;

    private String description;

    /** 关联 oauth2_client 的 client_id */
    private String clientId;

    /** MCP 连接配置（Phase 3 加密存储） */
    private String mcpConfig;

    /** 工具白名单（逗号分隔） */
    private String toolWhitelist;

    /** 可读写文档范围（JSON） */
    private String docScope;

    private Long tokenBudget;

    /** 状态：1 正常 / 0 禁用 */
    private Integer status;

    private Long createdBy;
}
