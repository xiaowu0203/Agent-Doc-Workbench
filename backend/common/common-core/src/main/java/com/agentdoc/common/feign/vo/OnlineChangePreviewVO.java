package com.agentdoc.common.feign.vo;

/**
 * 当前人类授权下对冻结版本进行的只读预览。
 * @param evidenceId 原始提案 ID
 * @param valid 原始结构与基线是否可应用
 * @param contentHash 可应用时的原始目标正文摘要
 */
public record OnlineChangePreviewVO(Long evidenceId, boolean valid, String contentHash) { }
