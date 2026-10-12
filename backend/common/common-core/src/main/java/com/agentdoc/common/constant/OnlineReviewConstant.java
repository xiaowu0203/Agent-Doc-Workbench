package com.agentdoc.common.constant;

/** 完整评审正文的协议上限，按 Java UTF-16 字符计；超过上限拒绝，不截断。 */
public final class OnlineReviewConstant {
    public static final int MAX_CONTENT_LENGTH = 2_000_000;
    public static final int MAX_TOTAL_LENGTH = 8_000_000;
    private OnlineReviewConstant() { }
}
