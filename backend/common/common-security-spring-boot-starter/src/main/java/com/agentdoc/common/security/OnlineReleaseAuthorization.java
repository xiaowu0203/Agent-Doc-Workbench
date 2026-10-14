package com.agentdoc.common.security;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.AuthUtils;
import java.util.List;

/** 非秘密发布身份可供已认证的用户/Task 或内部 CONTROL 读取。 */
public final class OnlineReleaseAuthorization {
    private OnlineReleaseAuthorization() { }
    public static void require(String control, OnlineCapabilityVerifier verifier) {
        if (control != null) {
            if (verifier == null) { throw new BusinessException(ErrorCode.FORBIDDEN); }
            verifier.verify(control, OnlineCapabilityPurpose.ONLINE_CONTROL, null, List.of());
        } else if (AuthUtils.getUserId() == null && !AuthUtils.isAgent()) { throw new BusinessException(ErrorCode.FORBIDDEN); }
    }
}
