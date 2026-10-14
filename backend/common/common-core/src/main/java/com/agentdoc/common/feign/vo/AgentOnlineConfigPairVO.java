package com.agentdoc.common.feign.vo;

/** 不含正文和凭证的当前配置双模板证明。
 * @param baseline 基线身份
 * @param candidate 候选身份
 * @param dependencyHash 当前完整依赖摘要
 * @param executionTimeoutSeconds 冻结超时
 * @param maxSystemPromptBytes 实际 Prompt 上限 */
public record AgentOnlineConfigPairVO(TemplateIdentity baseline, TemplateIdentity candidate,
        String dependencyHash, Integer executionTimeoutSeconds, long maxSystemPromptBytes) {
    /** @param id 模板身份 @param schemaVersion 协议版本 @param hash 模板摘要
     * @param nonPromptHash 非提示词一致性证明 */
    public record TemplateIdentity(String id, int schemaVersion, String hash, String nonPromptHash) { }
}
