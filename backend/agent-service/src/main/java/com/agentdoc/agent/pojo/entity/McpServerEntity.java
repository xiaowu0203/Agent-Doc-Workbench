package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mcp_server")
@Schema(description = "MCP Server 配置实体")
public class McpServerEntity extends BaseLogicDeleteEntity {
    @Schema(description = "所属空间 ID")
    private Long spaceId;
    @Schema(description = "空间内唯一技术标识")
    private String serverKey;
    @Schema(description = "展示名称")
    private String displayName;
    @Schema(description = "公网 HTTPS 端点")
    private String endpointUrl;
    @Schema(description = "认证类型")
    private String authType;
    @Schema(description = "加密认证令牌", accessMode = Schema.AccessMode.WRITE_ONLY)
    @ToString.Exclude
    private String encryptedAuthToken;
    @Schema(description = "配置版本号")
    private Long configVersion;
    @Schema(description = "状态：0 禁用 / 1 启用")
    private Integer status;
}
