package com.agentdoc.task.enums;

/** 创建意图独立于执行终态；投递失败仍保留原身份。 */
public enum TaskCreationIntentStatus {
    /** 身份已预留，尚未冻结输入。 */ PREPARING,
    /** 输入已经冻结，不再读取新版解析预算。 */ FROZEN,
    /** 原Task已落库，待补签发/投递。 */ CREATED,
    /** MQ已确认发布；重复创建只查询原Task。 */ PUBLISHED
}
