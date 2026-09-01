package com.agentdoc.document.constant;

/**
 * 文档相关常量。
 */
public final class DocumentConstant {

    private DocumentConstant() {
    }

    /** 文档初始版本号（首次保存生成版本 1） */
    public static final long INITIAL_VERSION = 0L;

    /** 文档版本递增步长 */
    public static final long VERSION_INCREMENT = 1L;

    /** 空间必须保留的最少所有者数量 */
    public static final long MIN_OWNER_COUNT = 1L;

    /** 变更摘要最大长度 */
    public static final int SUMMARY_MAX_LENGTH = 500;

    /** 文档片段读取单次最大长度（字符数，控 Token） */
    public static final int FRAGMENT_MAX_LENGTH = 2000;

    /** 文档片段接口默认读取长度 */
    public static final String DEFAULT_FRAGMENT_LENGTH = "500";

    /** 目录最大嵌套层级，根目录为第 1 层。 */
    public static final int MAX_DIRECTORY_DEPTH = 3;
}
