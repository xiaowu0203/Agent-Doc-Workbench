package com.agentdoc.task.enums;

import lombok.Getter;

import java.util.List;

/**
 * Agent 任务状态。
 */
@Getter
public enum TaskStatus {

    PENDING(0, "待运行"),
    RUNNING(1, "运行中"),
    COMPLETED(2, "已完成"),
    TERMINATED(3, "已终止"),
    FAILED(4, "异常失败"),
    DISPATCHED(5, "已分发"),
    WAITING_INPUT(6, "等待输入"),
    WAITING_AUTH(7, "等待授权"),
    CANCELING(8, "取消中");

    private final int code;
    private final String name;

    TaskStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static TaskStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (TaskStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知任务状态：" + code);
    }

    public boolean getCodeEquals(Integer value) {
        return value != null && code == value;
    }

    public boolean allowsCapabilityAccess() {
        return this == DISPATCHED || this == RUNNING || this == WAITING_INPUT || this == WAITING_AUTH;
    }

    public boolean canTerminate() {
        return this == PENDING || allowsCapabilityAccess();
    }

    public static List<Integer> remoteActiveCodes() {
        return List.of(DISPATCHED.code, RUNNING.code, WAITING_INPUT.code, WAITING_AUTH.code, CANCELING.code);
    }
}
