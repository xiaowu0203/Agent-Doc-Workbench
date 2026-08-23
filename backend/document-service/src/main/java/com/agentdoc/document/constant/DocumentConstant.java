package com.agentdoc.document.constant;

/**
 * 文档相关常量。
 */
public final class DocumentConstant {

    private DocumentConstant() {
    }

    /** 文档初始版本号（首次保存生成版本 1） */
    public static final long INITIAL_VERSION = 0L;

    /** 变更摘要最大长度 */
    public static final int SUMMARY_MAX_LENGTH = 500;

    /** 文档片段读取单次最大长度（字符数，控 Token） */
    public static final int FRAGMENT_MAX_LENGTH = 2000;
}
