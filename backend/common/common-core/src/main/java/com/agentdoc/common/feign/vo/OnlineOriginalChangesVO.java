package com.agentdoc.common.feign.vo;

import java.util.List;

/**
 * 完整原始变更集合，不选择最优提案。
 * @param schemaVersion 证据协议版本
 * @param state AVAILABLE、EVIDENCE_UNAVAILABLE 或 ORIGINAL_EVIDENCE_INVALID
 * @param input 当前授权后的冻结输入与执行身份
 * @param items 按稳定证据 ID 排序的全部原始提案或唯一收尾草稿
 */
public record OnlineOriginalChangesVO(int schemaVersion, String state, OnlineOriginalInputVO input,
        List<OnlineOriginalChangeItemVO> items) { }
