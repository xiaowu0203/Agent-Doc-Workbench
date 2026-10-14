package com.agentdoc.document.utils;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class OnlineApprovalDiffUtilsTest {
    @Test void emptyUnchangedAndWholeReplacementHaveExplicitDenominators() {
        assertThat(OnlineApprovalDiffUtils.compare("", "").humanModificationRatio()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(OnlineApprovalDiffUtils.compare("原文", "原文").changedSegments()).isZero();
        var replaced = OnlineApprovalDiffUtils.compare("甲乙", "丙丁");
        assertThat(replaced.addedCharacters()).isEqualTo(2);
        assertThat(replaced.deletedCharacters()).isEqualTo(2);
        assertThat(replaced.changedSegments()).isEqualTo(1);
        assertThat(replaced.humanModificationRatio()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(OnlineApprovalDiffUtils.compare("", "😀").addedCharacters()).isEqualTo(1);
        assertThat(OnlineApprovalDiffUtils.compare("😀", "").deletedCharacters()).isEqualTo(1);
    }
    @Test void surrogatePairsCrLfWhitespaceAndNormalizationAreNotSilentlyChanged() {
        var value = OnlineApprovalDiffUtils.compare(" 😀\r\n尾 ", " 😃\n尾 ");
        assertThat(value.addedCharacters()).isEqualTo(1);
        assertThat(value.deletedCharacters()).isEqualTo(2);
        assertThat(value.changedSegments()).isEqualTo(1);
        assertThat(value.humanModificationRatio()).isEqualByComparingTo("0.4285714286");
        assertThat(OnlineApprovalDiffUtils.compare("é", "é").addedCharacters()).isEqualTo(2);
        assertThat(OnlineApprovalDiffUtils.compare("x ", "x").deletedCharacters()).isEqualTo(1);
    }
    @Test void disjointEditsAndLcsTiesHaveStableSegments() {
        var separate = OnlineApprovalDiffUtils.compare("abcXYZdef", "aBcXYZdEf");
        assertThat(separate.addedCharacters()).isEqualTo(2);
        assertThat(separate.deletedCharacters()).isEqualTo(2);
        assertThat(separate.changedSegments()).isEqualTo(2);
        assertThat(OnlineApprovalDiffUtils.compare("ab", "ba").changedSegments()).isEqualTo(2);
    }
    @Test void overLimitIsMissingWhileLargeCommonPrefixIsStillExactlyComparable() {
        assertThat(OnlineApprovalDiffUtils.compare("a".repeat(2000), "b".repeat(2000))).isNull();
        assertThat(OnlineApprovalDiffUtils.compare("a".repeat(1999), "b".repeat(1999)).deletedCharacters()).isEqualTo(1999);
        assertThat(OnlineApprovalDiffUtils.compare("a".repeat(OnlineApprovalDiffUtils.MAX_CONTENT_LENGTH + 1), "")).isNull();
        var prefix = "文".repeat(5000);
        assertThat(OnlineApprovalDiffUtils.compare(prefix + "甲", prefix + "乙").deletedCharacters()).isEqualTo(1);
    }
    @Test void smallCasesMatchExhaustiveSubsequenceOracle() {
        var inputs = List.of("", "ab", "ba", "aab", "aaa", "😀a", "a😀", " ab", "ab ", "\r\n", "\n\r");
        for (String from : inputs) {
            for (String to : inputs) {
                var candidates = subsequences(from); candidates.retainAll(subsequences(to));
                int common = candidates.stream().mapToInt(value -> value.codePointCount(0, value.length())).max().orElseThrow();
                var diff = OnlineApprovalDiffUtils.compare(from, to);
                assertThat(diff.addedCharacters()).as("%s -> %s", from, to).isEqualTo(to.codePointCount(0, to.length()) - common);
                assertThat(diff.deletedCharacters()).isEqualTo(from.codePointCount(0, from.length()) - common);
            }
        }
    }
    private Set<String> subsequences(String value) {
        int[] codepoints = value.codePoints().toArray(); var values = new HashSet<String>();
        for (int mask = 0; mask < (1 << codepoints.length); mask++) {
            var text = new StringBuilder();
            for (int i = 0; i < codepoints.length; i++) { if ((mask & (1 << i)) != 0) { text.appendCodePoint(codepoints[i]); } }
            values.add(text.toString());
        }
        return values;
    }
}
