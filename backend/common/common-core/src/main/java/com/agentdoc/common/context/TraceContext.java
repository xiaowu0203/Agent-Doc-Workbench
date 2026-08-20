package com.agentdoc.common.context;

/**
 * TraceId 上下文：贯穿请求链路的链路追踪 ID。
 * 网关/入口生成，业务服务透传与复用。
 */
public final class TraceContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void set(String traceId) {
        HOLDER.set(traceId);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}