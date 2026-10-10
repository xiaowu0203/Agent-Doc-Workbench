package com.agentdoc.task.enums;

/** 创建补偿原因；不保存签发/MQ异常中的凭证或地址。 */
public enum TaskCreationIntentReason {
    /** 原Task待补能力签发。 */ TASK_CAPABILITY_PENDING,
    /** 原Task待补消息投递。 */ TASK_MESSAGE_PENDING
}
