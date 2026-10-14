package com.agentdoc.common.feign.vo;

import com.agentdoc.common.feign.dto.ChangeItemDTO;
import java.util.List;

/**
 * Task 领域校验后的原始审批来源；只向当前有权限的人类开放。
 * @param input 冻结输入及执行身份
 * @param changeRequestId 原始提案 ID
 * @param status 当前审批状态，用于发现读取期间的状态变化
 * @param originalEvidenceHash 原始 changes 的摘要，不是审批编辑后的摘要
 * @param originalIdentityHash 原始捕获身份摘要
 * @param baseVersion 原始提案基线
 * @param changes 原始提案载荷；不可比较时不提供
 * @param mergedVersion 已确认的合并版本
 * @param mergedBy 合并人
 * @param mergedAt 合并时间
 * @param reason 不可比较原因；null 表示来源可比较
 */
public record OnlineApprovalSourceVO(OnlineOriginalInputVO input, String changeRequestId, String status,
        String originalEvidenceHash, String originalIdentityHash, Long baseVersion, List<ChangeItemDTO> changes,
        Long mergedVersion, String mergedBy, String mergedAt, String reason) { }
