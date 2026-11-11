package com.agentdoc.common.feign.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * 仅受信BFF提交给Auth，不接受浏览器直传身份/角色/版本/期限。
 * @param username 本地账号
 * @param password 本次校验密码，不持久化或记录
 * @param clientIp BFF从受信连接/代理边界取得的最终客户端IP字面量
 */
public record LocalHumanCredentialsDTO(String username, String password, String clientIp) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("不支持的本地认证参数"); }
    @Override public String toString() { return "[REDACTED LOCAL LOGIN REQUEST]"; }
}
