package com.agentdoc.task.constant;

/** 与既有遥测部署对应的诊断读取安全上限。 */
public final class TaskTraceConstant {
    public static final int RETENTION_DAYS = 14;
    public static final int MAX_PAYLOAD_BYTES = 2 * 1024 * 1024;
    public static final int MAX_RAW_SPANS = 10000;
    public static final int MAX_VIEW_SPANS = 2000;
    public static final int CONNECT_TIMEOUT_MS = 2000;
    public static final int READ_TIMEOUT_MS = 5000;
    private TaskTraceConstant() { }
}
