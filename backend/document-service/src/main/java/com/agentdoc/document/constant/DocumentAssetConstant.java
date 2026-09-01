package com.agentdoc.document.constant;

import java.util.Map;

/**
 * 文档附件约束。
 */
public final class DocumentAssetConstant {

    /** 图片最大大小：10 MiB。 */
    public static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024L;

    /** 当前支持的图片 MIME 类型及其文件后缀。 */
    public static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/gif", "gif",
            "image/webp", "webp");

    /** 图片文件头长度。 */
    public static final int IMAGE_HEADER_LENGTH = 12;

    private DocumentAssetConstant() {
    }
}
