package com.agentdoc.common.feign.dto;

/** 人类初次确认；授权人只从已认证用户取得。 */
public record OnlineControlAuthorizeDTO(
        /** 实验十进制身份。 */ String experimentId,
        /** 预检确认的冻结清单摘要。 */ String manifestHash,
        /** 明确接受保护机制尽力取消在途项。 */ boolean automaticCancellationAcknowledged) { }
