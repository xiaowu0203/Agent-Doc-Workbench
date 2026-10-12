package com.agentdoc.common.feign.vo;

import java.util.List;

/**
 * 只读预览结果；预览本身不能证明载荷来自原始产物，调用方必须另行核验 Task 证据。
 * @param inputContent 完整冻结输入正文
 * @param items 保持请求顺序的全部预览
 */
public record OnlineProposalReviewPreviewVO(String inputContent, List<Item> items) {
    /** @param evidenceId 提案 ID @param valid 结构及基线是否有效 @param contentHash 预览正文摘要 @param content 完整预览正文，无效时为 null */
    public record Item(Long evidenceId, boolean valid, String contentHash, String content) { }
}
