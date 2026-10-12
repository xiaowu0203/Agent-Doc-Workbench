package com.agentdoc.common.feign.vo;

import java.util.List;

/**
 * Task 授权后的完整原始提案载荷，不使用可编辑的 ChangeRequest.changes。
 * @param evidence 完整集合及冻结身份
 * @param items 与证据顺序完全一致的原始载荷
 */
public record OnlineOriginalProposalPayloadVO(OnlineOriginalChangesVO evidence, List<Item> items) {
    /** @param evidenceId 原始提案 ID @param originalChanges 捕获时的原始 JSON，保留摘要对应的精确文本 */
    public record Item(String evidenceId, String originalChanges) { }
}
