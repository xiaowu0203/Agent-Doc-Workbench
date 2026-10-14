package com.agentdoc.document.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 冻结 code point LCS 算法；有限资源内做精确比较，超限不生成近似零分。 */
public final class OnlineApprovalDiffUtils {
    public static final int MAX_CONTENT_LENGTH = 2_000_000;
    public static final long MAX_MATRIX_CELLS = 4_000_000;
    private static final int RATIO_SCALE = 10;
    private OnlineApprovalDiffUtils() { }

    /**
     * @return 精确差异；超过冻结资源界限返回 null
     */
    public static Difference compare(String original, String merged) {
        if (original.length() > MAX_CONTENT_LENGTH || merged.length() > MAX_CONTENT_LENGTH) { return null; }
        int[] from = original.codePoints().toArray(), to = merged.codePoints().toArray();
        int prefix = 0;
        while (prefix < from.length && prefix < to.length && from[prefix] == to[prefix]) { prefix++; }
        int endFrom = from.length, endTo = to.length;
        while (endFrom > prefix && endTo > prefix && from[endFrom - 1] == to[endTo - 1]) { endFrom--; endTo--; }
        int rows = endFrom - prefix, columns = endTo - prefix;
        if ((long) (rows + 1) * (columns + 1) > MAX_MATRIX_CELLS) { return null; }
        int[] lcs = new int[(rows + 1) * (columns + 1)];
        int stride = columns + 1;
        for (int i = rows - 1; i >= 0; i--) {
            for (int j = columns - 1; j >= 0; j--) {
                lcs[i * stride + j] = from[prefix + i] == to[prefix + j] ? 1 + lcs[(i + 1) * stride + j + 1]
                        : Math.max(lcs[(i + 1) * stride + j], lcs[i * stride + j + 1]);
            }
        }
        int i = 0, j = 0, added = 0, deleted = 0, segments = 0;
        boolean editing = false;
        while (i < rows || j < columns) {
            if (i < rows && j < columns && from[prefix + i] == to[prefix + j]) {
                i++; j++; editing = false;
            } else {
                if (!editing) { segments++; editing = true; }
                if (i < rows && (j == columns || lcs[(i + 1) * stride + j] >= lcs[i * stride + j + 1])) { i++; deleted++; }
                else { j++; added++; }
            }
        }
        int denominator = from.length + added;
        var ratio = denominator == 0 ? BigDecimal.ZERO.setScale(RATIO_SCALE)
                : BigDecimal.valueOf(added + deleted).divide(BigDecimal.valueOf(denominator), RATIO_SCALE, RoundingMode.HALF_UP);
        return new Difference(added, deleted, segments, ratio);
    }

    /**
     * @param addedCharacters 新增 code point 数
     * @param deletedCharacters 删除 code point 数
     * @param changedSegments 连续增删片段数
     * @param humanModificationRatio 冻结分母上的编辑幅度
     */
    public record Difference(int addedCharacters, int deletedCharacters, int changedSegments, BigDecimal humanModificationRatio) { }
}
