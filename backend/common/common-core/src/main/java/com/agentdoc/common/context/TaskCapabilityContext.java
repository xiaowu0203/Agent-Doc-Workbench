package com.agentdoc.common.context;

/**
 * 任务能力令牌的线程上下文。
 */
public final class TaskCapabilityContext {

    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private TaskCapabilityContext() {
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
