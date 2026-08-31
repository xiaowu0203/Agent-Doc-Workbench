package com.agentdoc.common.feign.dto;

import java.util.List;

/**
 * 用户信息批量查询契约。
 */
public record UserBatchQueryDTO(List<Long> userIds) {
}
