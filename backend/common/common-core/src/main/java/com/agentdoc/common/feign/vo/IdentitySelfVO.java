package com.agentdoc.common.feign.vo;

import java.util.List;

/**
 * 当前已认证人类用户的最小业务身份与本地平台角色。
 *
 * @param userId 本地用户标识
 * @param platformRoles 当前平台角色标识
 * @param authVersion 当前用户级人类认证版本
 */
public record IdentitySelfVO(Long userId, List<String> platformRoles, Long authVersion) { }
