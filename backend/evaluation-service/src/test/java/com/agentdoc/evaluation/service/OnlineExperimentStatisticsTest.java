package com.agentdoc.evaluation.service;

import com.agentdoc.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class OnlineExperimentStatisticsTest {
    @Test
    void confidenceMatchesIndependentHighPrecisionTailEquationsIncludingRawQuantiles() throws Exception {
        Path base = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (base != null && !Files.exists(base.resolve("docs/fixtures/online-clopper-pearson-v1.json"))) { base = base.getParent(); }
        assertThat(base).isNotNull();
        var fixture = JsonUtils.parseStrict(Files.readString(base.resolve("docs/fixtures/online-clopper-pearson-v1.json")), JsonNode.class);
        var raw = OnlineExperimentStatistics.class.getDeclaredMethod("quantile", int.class, int.class); raw.setAccessible(true);
        for (var vector : fixture.path("vectors")) {
            int x = vector.path("passed").asInt(), n = vector.path("valid").asInt(); var actual = OnlineExperimentStatistics.confidence(x, n);
            double lower = vector.path("lower").asDouble(), upper = vector.path("upper").asDouble();
            assertThat(Double.parseDouble(actual.lower())).isCloseTo(lower, within(1e-10));
            assertThat(Double.parseDouble(actual.upper())).isCloseTo(upper, within(1e-10));
            if (x > 0) { assertThat((double) raw.invoke(null, x, n)).isCloseTo(lower, within(1e-10)); }
            if (x < n) { assertThat(1 - (double) raw.invoke(null, n - x, n)).isCloseTo(upper, within(1e-10)); }
        }
        assertThat(OnlineExperimentStatistics.confidence(0, 0).lower()).isNull();
        assertThat(OnlineExperimentStatistics.confidence(0, 0).upper()).isNull();
        assertThatThrownBy(() -> OnlineExperimentStatistics.confidence(2, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OnlineExperimentStatistics.confidence(0, 1001)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void matchesEveryExactSrmFixtureIncludingAsymmetricTailsAndPolicyBoundary() throws Exception {
        Path base = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (base != null && !Files.exists(base.resolve("docs/fixtures/controlled-online-ab-v2.json"))) { base = base.getParent(); }
        assertThat(base).isNotNull();
        var fixture = JsonUtils.parseStrict(Files.readString(base.resolve("docs/fixtures/controlled-online-ab-v2.json")), JsonNode.class);
        for (var vector : fixture.path("srm")) {
            var input = vector.get("input");
            var actual = OnlineExperimentStatistics.srm(input.get("n").asInt(), input.get("observed").asInt(), input.get("weight").asInt());
            assertThat(actual.status()).as(vector.get("name").asText()).isEqualTo(vector.get("status").asText());
            if (vector.get("probability").isNull()) { assertThat(actual.pValue()).isNull(); }
            else { assertThat(new BigDecimal(actual.pValue()).subtract(new BigDecimal(vector.get("probability").asText())).abs())
                    .isLessThan(new BigDecimal("0.000000000001")); }
        }
    }
}
