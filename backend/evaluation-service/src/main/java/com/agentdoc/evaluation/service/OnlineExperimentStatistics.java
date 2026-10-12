package com.agentdoc.evaluation.service;

import static com.agentdoc.common.enums.OnlineReasonCode.*;

import com.agentdoc.evaluation.constant.OnlineExperimentConstant;
import com.agentdoc.evaluation.pojo.vo.OnlineSrmVO;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/** 概率排序的双侧精确二项 SRM；范围≤1000，无浮点尾概率截断。 */
public final class OnlineExperimentStatistics {
    private static final BigInteger BPS = BigInteger.valueOf(10000);
    private OnlineExperimentStatistics() { }

    /** 双侧 95% Clopper–Pearson；反演二项尾概率，空分母没有区间。 */
    public static Interval confidence(int passed, int valid) {
        if (valid < 0 || passed < 0 || passed > valid || valid > OnlineExperimentConstant.MAX_DOCUMENTS) {
            throw new IllegalArgumentException("无效独立文档计数");
        }
        if (valid == 0) { return new Interval(null, null); }
        return new Interval(decimal(passed == 0 ? 0 : quantile(passed, valid)),
                decimal(passed == valid ? 1 : 1 - quantile(valid - passed, valid)));
    }

    private static String decimal(double value) { return BigDecimal.valueOf(value).setScale(12, RoundingMode.HALF_UP).toPlainString(); }

    private static double quantile(int successes, int total) {
        double low = 0, high = 1;
        for (int step = 0; step < 48; step++) {
            double p = (low + high) / 2;
            // 对数计算每个概率，再求尾和；n≤1000，避免 p^n 下溢丢失中部概率。
            double tail = 0, logCombination = 0;
            for (int k = 0; k <= total; k++) {
                if (k >= successes) { tail += Math.exp(logCombination + k * Math.log(p) + (total - k) * Math.log1p(-p)); }
                if (k < total) { logCombination += Math.log(total - k) - Math.log(k + 1); }
            }
            if (tail < 0.025) { low = p; } else { high = p; }
        }
        return (low + high) / 2;
    }

    /** 固定十二位十进制区间边界。 */
    public record Interval(String lower, String upper) { }

    public static OnlineSrmVO srm(int documents, int candidate, int weight) {
        if (documents < 0 || candidate < 0 || candidate > documents || weight < 1 || weight >= 10000
                || documents > OnlineExperimentConstant.MAX_DOCUMENTS) { throw new IllegalArgumentException(SRM_INVALID.name()); }
        if (documents < OnlineExperimentConstant.SRM_MIN_DOCUMENTS
                || (long) documents * weight < (long) OnlineExperimentConstant.SRM_MIN_EXPECTED_COUNT * 10000
                || (long) documents * (10000 - weight) < (long) OnlineExperimentConstant.SRM_MIN_EXPECTED_COUNT * 10000) {
            return new OnlineSrmVO("NOT_ENOUGH_UNITS", null);
        }
        BigInteger q = BigInteger.valueOf(weight);
        BigInteger r = BPS.subtract(q);
        BigInteger[] probability = new BigInteger[documents + 1];
        BigInteger combination = BigInteger.ONE;
        for (int k = 0; k <= documents; k++) {
            probability[k] = combination.multiply(q.pow(k)).multiply(r.pow(documents - k));
            if (k < documents) { combination = combination.multiply(BigInteger.valueOf(documents - k))
                    .divide(BigInteger.valueOf(k + 1)); }
        }
        BigInteger tail = BigInteger.ZERO;
        for (BigInteger value : probability) {
            if (value.compareTo(probability[candidate]) <= 0) { tail = tail.add(value); }
        }
        BigInteger denominator = BPS.pow(documents);
        boolean detected = tail.multiply(BigInteger.valueOf(1000)).compareTo(denominator) < 0;
        String p = new BigDecimal(tail).divide(new BigDecimal(denominator), MathContext.DECIMAL128).stripTrailingZeros().toPlainString();
        return new OnlineSrmVO(detected ? "DETECTED" : "PASS", p);
    }
}
