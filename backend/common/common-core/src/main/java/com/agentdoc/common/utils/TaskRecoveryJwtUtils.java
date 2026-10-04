package com.agentdoc.common.utils;

import com.agentdoc.common.constant.TaskRecoveryConstant;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

/** 恢复凭证的通用入口拒绝规则；专用 verifier 不调用此规则。 */
public final class TaskRecoveryJwtUtils {
    private TaskRecoveryJwtUtils() { }

    /** 防止恢复 JWT 被当成人类、Agent 或 Worker 凭证使用。 */
    public static Jwt rejectGeneralAccess(Jwt jwt) {
        if (jwt.hasClaim(TaskRecoveryConstant.PURPOSE)
                || (jwt.getAudience() != null && (jwt.getAudience().contains(TaskRecoveryConstant.A2A_AUDIENCE)
                || jwt.getAudience().contains(TaskRecoveryConstant.DRAFT_AUDIENCE)))) {
            throw new JwtException("恢复凭证不能用于普通鉴权入口");
        }
        return jwt;
    }
}
