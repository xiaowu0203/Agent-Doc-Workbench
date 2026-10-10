package com.agentdoc.evaluation.constant;

import java.util.Set;

/** 执行安全投影的稳定状态；不作为部署开关。 */
public final class OnlineSafetyConstant {
    public static final int INPUT_SCHEMA_VERSION = 1;
    public static final String PENDING = "PENDING";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String WAITING = "WAITING";
    public static final String ACQUIRED = "ACQUIRED";
    public static final String RELEASED = "RELEASED";
    public static final String NONE = "NONE";
    public static final String REQUESTED = "REQUESTED";
    public static final String UNKNOWN = "UNKNOWN";
    public static final String RESERVED = "RESERVED";
    public static final String SETTLED = "SETTLED";
    public static final String LIVE = "LIVE";
    public static final String ORIGINAL = "ORIGINAL";
    public static final Set<String> EXECUTION_TERMINAL = Set.of("COMPLETED", "FAILED", "CANCELED", "TIMED_OUT");
    public static final Set<String> HEALTH_TERMINAL = Set.of("COMPLETED", "FAILED", "TIMED_OUT");
    private OnlineSafetyConstant() { }
}
