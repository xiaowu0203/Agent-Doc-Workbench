package com.agentdoc.common.feign.dto;

import com.agentdoc.common.enums.ChangeOp;

/**
 * 结构化变更项。
 * v0.1 简化操作集：全文替换 / 末尾追加。
 * @param op      变更操作（replace 全文替换 / append 末尾追加），非空
 * @param oldText 原文（replace 时可用于一致性校验），可空
 * @param newText 新内容（replace 为新全文 / append 为追加内容），非空
 */
public record ChangeItemDTO(ChangeOp op, String oldText, String newText) {
}
