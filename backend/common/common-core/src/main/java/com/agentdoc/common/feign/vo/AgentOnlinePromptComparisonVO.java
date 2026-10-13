package com.agentdoc.common.feign.vo;

/** 当前权限下的完整冻结 Prompt 对照；不包含私有模板的其他正文或凭证。 */
public record AgentOnlinePromptComparisonVO(
        /** 实验 ID，规范十进制文本。 */ String experimentId,
        /** 所属空间 ID。 */ String spaceId,
        /** 原 Agent ID。 */ String agentId,
        /** 冻结协议版本。 */ Integer schemaVersion,
        /** 已验证的基线模板身份。 */ AgentOnlineConfigPairVO.TemplateIdentity baseline,
        /** 已验证的候选模板身份。 */ AgentOnlineConfigPairVO.TemplateIdentity candidate,
        /** 两份模板的依赖摘要。 */ String dependencyHash,
        /** 冻结后实际发送的完整基线系统提示词。 */ String baselineSystemPrompt,
        /** 冻结后实际发送的完整候选系统提示词。 */ String candidateSystemPrompt) { }
