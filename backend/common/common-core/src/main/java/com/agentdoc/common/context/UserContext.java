package com.agentdoc.common.context;

/**
 * 用户上下文：ThreadLocal 持有当前请求的登录主体。
 * 请求结束时必须调用 clear()，防止线程复用污染。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.userId();
    }

    public static boolean isLoggedIn() {
        return HOLDER.get() != null;
    }

    public static void clear() {
        HOLDER.remove();
    }
}