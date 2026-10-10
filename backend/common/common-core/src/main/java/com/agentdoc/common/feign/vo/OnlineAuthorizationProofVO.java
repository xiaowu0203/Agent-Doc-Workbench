package com.agentdoc.common.feign.vo;

/** Evaluation 只读权威授权投影，不返回 Prompt 或私有清单。 */
public record OnlineAuthorizationProofVO(
        /** 实验身份。 */ String experimentId,
        /** 空间身份。 */ String spaceId,
        /** 冻结清单摘要。 */ String manifestHash,
        /** 线上协议版本。 */ int schemaVersion,
        /** 当前实验状态。 */ String status,
        /** 已确认的保护授权人，启动前为空。 */ String authorizedBy) { }
