package com.agentdoc.common.feign.vo;

/**
 * 用户展示用最小信息投影，不包含敏感字段。
 */
public record UserRefVO(Long id, String username, String nickname) {
}
