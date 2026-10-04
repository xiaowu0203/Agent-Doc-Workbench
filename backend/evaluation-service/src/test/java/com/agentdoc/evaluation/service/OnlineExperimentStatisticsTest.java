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
