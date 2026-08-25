package com.agentdoc.common.feign.context;

/**
 * 异步线程中的 Feign Authorization 传递上下文。
 */
public final class AuthorizationContext {

    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private AuthorizationContext() {
    }

    public static void set(String token) {
        TOKEN.set(token);
    }

    public static String current() {
        return TOKEN.get();
    }

    public static void clear() {
        TOKEN.remove();
    }
}
