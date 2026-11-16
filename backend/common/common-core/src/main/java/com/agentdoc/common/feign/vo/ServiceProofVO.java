package com.agentdoc.common.feign.vo;

import java.time.Instant;

/**
 * 只交给登记SERVICE的受控协议响应，不进入普通业务出参。
 * @param credential 固定内部用途的服务证明
 * @param issuedAt 签发时刻
 * @param expiresAt 固定到期，不提供Refresh
 */
public record ServiceProofVO(String credential, Instant issuedAt, Instant expiresAt) {
    @Override public String toString() { return "[REDACTED SERVICE PROOF]"; }
}
