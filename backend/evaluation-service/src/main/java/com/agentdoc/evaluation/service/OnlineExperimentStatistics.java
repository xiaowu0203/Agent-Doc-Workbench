package com.agentdoc.evaluation.service;

import static com.agentdoc.common.enums.OnlineReasonCode.*;

import com.agentdoc.evaluation.constant.OnlineExperimentConstant;
import com.agentdoc.evaluation.pojo.vo.OnlineSrmVO;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/** 概率排序的双侧精确二项 SRM；范围≤1000，无浮点尾概率截断。 */
public final class OnlineExperimentStatistics {
    private static final BigInteger BPS = BigInteger.valueOf(10000);
    private OnlineExperimentStatistics() { }

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
