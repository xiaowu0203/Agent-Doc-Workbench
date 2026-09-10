package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mcp_template")
@Schema(description = "系统 MCP 模板")
public class McpTemplateEntity extends BaseLogicDeleteEntity {
    @Schema(description = "稳定技术标识") private String serverKey;
    @Schema(description = "展示名称") private String displayName;
    @Schema(description = "模板说明") private String description;
    @Schema(description = "默认公网 HTTPS 端点") private String endpointUrl;
    @Schema(description = "认证类型") private String authType;
    @Schema(description = "Query API Key 参数名") private String authParamName;
    @Schema(description = "模板配置版本") private Long configVersion;
    @Schema(description = "状态：0 停用 / 1 启用") private Integer status;
    @Schema(description = "平台最近发现的工具数量") private Integer discoveredToolCount;
    @Schema(description = "平台最近发现的工具 JSON") private String discoveredToolsJson;
    @Schema(description = "平台工具发现时间") private LocalDateTime toolsDiscoveredAt;
    @Schema(description = "创建人用户 ID") private Long createdBy;
}
