package com.agentdoc.auth.entity;

import com.agentdoc.common.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OAuth2 客户端实体（Agent / 第三方凭证，Phase 3 Client Credentials 使用）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oauth2_client")
public class Oauth2ClientEntity extends BaseLogicDeleteEntity {

    private String clientId;

    private String clientSecretHash;

    private String clientName;

    private String grantTypes;

    private String scopes;

    private String redirectUris;

    private Integer status;
}
