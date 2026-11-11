package com.agentdoc.auth.utils;

import com.agentdoc.auth.constant.LocalIdentityConstant;
import com.agentdoc.auth.constant.PlatformManagementConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;

import java.nio.charset.StandardCharsets;

/** 复用既有字符长度要求，另外约束BCrypt的UTF8字节上限。 */
public final class LocalPasswordPolicy {
    private LocalPasswordPolicy() { }

    public static boolean supported(String password) {
        return password != null && !password.isBlank()
                && password.length() >= PlatformManagementConstant.MIN_PASSWORD_LENGTH
                && password.length() <= PlatformManagementConstant.MAX_PASSWORD_LENGTH
                && password.getBytes(StandardCharsets.UTF_8).length <= LocalIdentityConstant.MAX_PASSWORD_UTF8_BYTES;
    }

    public static void requireSupported(String password) {
        if (!supported(password)) { throw new BusinessException(ErrorCode.BAD_REQUEST, "密码长度或编码不符合本地认证要求"); }
    }
}
