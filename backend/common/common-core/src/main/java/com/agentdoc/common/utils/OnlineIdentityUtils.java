package com.agentdoc.common.utils;

import static com.agentdoc.common.constant.OnlineCapabilityConstant.*;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.OnlineDispatchIdentityDTO;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.List;
import java.util.Objects;

/** Task/A2A/Agent 使用同一完整线上身份规则；部分字段不能伪装成普通任务。 */
public final class OnlineIdentityUtils {
    private static final List<String> CLAIMS = List.of(EXPERIMENT_ID, ASSIGNMENT_ID, BINDING_SCHEMA, BINDING_HASH, SLOT_GENERATION, SLOT_PERMIT_HASH);
    private OnlineIdentityUtils() { }
    public static void requireJwt(Jwt jwt, OnlineDispatchIdentityDTO identity) {
        if (identity == null) {
            if (CLAIMS.stream().anyMatch(jwt::hasClaim)) { throw denied(); } return;
        }
        requireComplete(identity);
        List<Object> values = List.of(identity.experimentId(), identity.assignmentId(), identity.bindingSchemaVersion(),
                identity.bindingHash(), identity.generation(), identity.permitHash());
        for (int index = 0; index < CLAIMS.size(); index++) {
            Object actual = jwt.getClaim(CLAIMS.get(index));
            if (actual == null || !Objects.equals(String.valueOf(actual), String.valueOf(values.get(index)))) { throw denied(); }
        }
    }
    public static void requireComplete(OnlineDispatchIdentityDTO identity) {
        if (identity == null || identity.bindingSchemaVersion() == null || identity.bindingSchemaVersion() != OnlineProtocolUtils.SCHEMA_VERSION
                || identity.bindingHash() == null || !identity.bindingHash().matches("[0-9a-f]{64}") || identity.generation() == null
                || identity.generation() <= 0 || identity.permitHash() == null || !identity.permitHash().matches("[0-9a-f]{64}")) { throw denied(); }
        try { OnlineProtocolUtils.id(identity.experimentId()); OnlineProtocolUtils.id(identity.assignmentId()); }
        catch (RuntimeException invalid) { throw denied(); }
    }
    private static BusinessException denied() { return new BusinessException(ErrorCode.FORBIDDEN, OnlineReasonCode.BINDING_INVALID.name()); }
}
