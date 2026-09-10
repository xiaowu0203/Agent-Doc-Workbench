package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

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
    @Schema(description = "Query API Key 参数名")
    private String authParamName;
    @Schema(description = "加密认证令牌", accessMode = Schema.AccessMode.WRITE_ONLY)
    @ToString.Exclude
    private String encryptedAuthToken;
    @Schema(description = "配置版本号")
    private Long configVersion;
    @Schema(description = "状态：0 禁用 / 1 启用")
    private Integer status;
    @Schema(description = "最近连接测试状态：UNTESTED / SUCCESS / FAILED")
    private String connectionStatus;
    @Schema(description = "最近一次连接测试完成时间")
    private LocalDateTime lastTestedAt;
    @Schema(description = "最近一次握手与工具发现总耗时，毫秒")
    private Long lastTestDurationMs;
    @Schema(description = "最近一次连接失败错误摘要")
    private String lastTestError;
    @Schema(description = "最近一次成功发现的工具数量")
    private Integer discoveredToolCount;
    @Schema(description = "最近一次成功发现的工具定义 JSON 快照", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String discoveredToolsJson;
    @Schema(description = "当前工具快照发现时间")
    private LocalDateTime toolsDiscoveredAt;
}
