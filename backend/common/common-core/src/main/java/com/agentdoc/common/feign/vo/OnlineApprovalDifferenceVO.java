package com.agentdoc.common.feign.vo;

import java.math.BigDecimal;

/**
 * 审批前原始提案与对应合并快照的脱敏差异信封。
 * @param schemaVersion 协议版本
 * @param state COMPARABLE 或 NOT_COMPARABLE
 * @param reason 不可比较原因
 * @param algorithm 冻结差异算法版本
 * @param input 冻结输入及执行身份
 * @param changeRequestId 原始提案 ID
 * @param originalEvidenceHash 原始 changes 摘要
 * @param originalIdentityHash 原始捕获身份摘要
 * @param baseVersion 原始提案基线
 * @param originalContentHash 在冻结输入上应用原始提案得到的正文摘要
 * @param mergedVersion 对应审批合并版本
 * @param mergedContentHash 对应审批合并正文摘要
 * @param mergedBy 合并人
 * @param mergedAt 合并时间
 * @param addedCharacters 新增 code point 数，不可比较为 null
 * @param deletedCharacters 删除 code point 数，不可比较为 null
 * @param changedSegments 连续编辑片段数，不可比较为 null
 * @param humanModificationRatio 编辑幅度，不作为原始质量
 * @param identityHash 完整信封身份摘要
 */
public record OnlineApprovalDifferenceVO(int schemaVersion, String state, String reason, String algorithm,
        OnlineOriginalInputVO input, String changeRequestId, String originalEvidenceHash, String originalIdentityHash,
        Long baseVersion, String originalContentHash, Long mergedVersion, String mergedContentHash,
        String mergedBy, String mergedAt, Integer addedCharacters, Integer deletedCharacters,
        Integer changedSegments, BigDecimal humanModificationRatio, String identityHash) { }
