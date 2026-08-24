package com.agentdoc.task.enums;

import lombok.Getter;

/**
 * Agent 任务状态。
 */
@Getter
public enum TaskStatus {

    PENDING(0, "待运行"),
    RUNNING(1, "运行中"),
    COMPLETED(2, "已完成"),
    TERMINATED(3, "已终止"),
    FAILED(4, "异常失败");

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
}
