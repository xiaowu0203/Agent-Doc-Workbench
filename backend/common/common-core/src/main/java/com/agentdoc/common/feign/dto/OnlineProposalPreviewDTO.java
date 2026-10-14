package com.agentdoc.common.feign.dto;

import java.util.List;

/**
 * 不可变原始提案的只读校验载荷。
 * @param evidenceId 原始提案 ID
 * @param baseVersion 原始提案基线
 * @param changes 审批前变更项，只在受控服务调用中传递
 */
public record OnlineProposalPreviewDTO(Long evidenceId, Long baseVersion, List<ChangeItemDTO> changes) { }
