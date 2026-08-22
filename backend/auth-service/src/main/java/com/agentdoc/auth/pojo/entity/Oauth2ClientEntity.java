package com.agentdoc.auth.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OAuth2 客户端实体（Agent / 第三方凭证，Phase 3 Client Credentials 使用）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oauth2_client")
@Schema(description = "OAuth2 客户端实体")
public class Oauth2ClientEntity extends BaseLogicDeleteEntity {

    @Schema(description = "客户端 ID")
    private String clientId;

    @Schema(description = "客户端密钥哈希")
    private String clientSecretHash;

    @Schema(description = "客户端名称")
    private String clientName;

    @Schema(description = "授权类型（逗号分隔）")
    private String grantTypes;

    @Schema(description = "授权作用域（逗号分隔）")
    private String scopes;

    @Schema(description = "回调地址（逗号分隔）")
    private String redirectUris;

    @Schema(description = "状态：0 禁用 / 1 启用")
    private Integer status;
}
