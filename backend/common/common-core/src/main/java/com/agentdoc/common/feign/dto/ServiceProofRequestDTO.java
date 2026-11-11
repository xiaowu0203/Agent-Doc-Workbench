package com.agentdoc.common.feign.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.Set;

/**
 * 只允许登记client申请固定内部用途，不接受用户、actions或任意TTL。
 * @param audience 已批准的内部服务资源组
 * @param scopes 已批准的服务用途集合
 */
public record ServiceProofRequestDTO(String audience, Set<String> scopes) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("不支持的服务证明参数"); }
}
