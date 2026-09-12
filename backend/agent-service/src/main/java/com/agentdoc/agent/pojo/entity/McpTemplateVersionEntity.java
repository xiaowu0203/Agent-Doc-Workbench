package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mcp_template_version")
@Schema(description = "MCP 模板不可变版本")
public class McpTemplateVersionEntity extends BaseEntity {
    @Schema(description = "MCP 模板 ID") private Long templateId;
    @Schema(description = "版本号") private Integer versionNo;
    @Schema(description = "状态：0 草稿 / 1 已发布") private Integer status;
    @Schema(description = "安装后的默认展示名称") private String displayName;
    @Schema(description = "默认公网 HTTPS 端点") private String endpointUrl;
    @Schema(description = "认证类型") private String authType;
    @Schema(description = "Query API Key 参数名") private String authParamName;
    @Schema(description = "创建人用户 ID") private Long createdBy;
    @Schema(description = "发布人用户 ID") private Long publishedBy;
    @Schema(description = "发布时间") private LocalDateTime publishedAt;
}
