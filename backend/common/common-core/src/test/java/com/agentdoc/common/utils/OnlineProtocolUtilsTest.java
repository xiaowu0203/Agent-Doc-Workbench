package com.agentdoc.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.agentdoc.common.feign.dto.AgentOnlineConfigPrepareDTO;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class OnlineProtocolUtilsTest {
    @Test
    void internalTemplateIdsRejectNumericCoercionAndPreserveLargeStrings() {
        String body = "{\"experimentId\":\"9223372036854775807\",\"spaceId\":\"101\",\"agentId\":\"201\",\"requestHash\":\"hash\",\"candidateAgentPrompt\":\"candidate\"}";
        assertThat(JsonUtils.parseStrict(body, AgentOnlineConfigPrepareDTO.class).experimentId()).isEqualTo("9223372036854775807");
        assertThat(JsonUtils.parseStrict(body.replace("\"spaceId\":\"101\"", "\"spaceId\":101"), AgentOnlineConfigPrepareDTO.class)).isNull();
    }
    private JsonNode fixture() throws Exception {
        Path base = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (base != null && !Files.exists(base.resolve("docs/fixtures/controlled-online-ab-v2.json"))) { base = base.getParent(); }
        assertThat(base).isNotNull();
        return JsonUtils.parseStrict(Files.readString(base.resolve("docs/fixtures/controlled-online-ab-v2.json")), JsonNode.class);
    }

    @Test
    void matchesAllPublishedCanonicalAndUnsignedBucketVectors() throws Exception {
        var fixture = fixture();
        for (var vector : fixture.get("canonical")) {
            String canonical = OnlineProtocolUtils.canonicalNode(vector.get("value"));
            assertThat(canonical).as(vector.get("name").asText()).isEqualTo(vector.get("canonical").asText());
            assertThat(StableSnapshotUtils.sha256Utf8(canonical)).isEqualTo(vector.get("hash").asText());
        }
        for (var vector : fixture.get("buckets")) {
            var input = vector.get("input");
            int bucket = OnlineProtocolUtils.bucket(input.get("experiment_id").asText(), input.get("document_id").asText(), input.get("seed").asText());
            assertThat(bucket).isEqualTo(vector.get("bucket").asInt());
            assertThat(OnlineProtocolUtils.variant(bucket, input.get("weight").asInt())).isEqualTo(vector.get("variant").asText());
        }
        assertThat(OnlineProtocolUtils.variant(5000, 5000)).isEqualTo("BASELINE");
        assertThat(OnlineProtocolUtils.variant(4999, 5000)).isEqualTo("CANDIDATE");
    }

    @Test
    void rejectsPublishedInvalidInputsAndStrictJsonAmbiguity() throws Exception {
        for (var vector : fixture().get("invalidBuckets")) {
            var input = vector.get("input");
            assertThatThrownBy(() -> {
                OnlineProtocolUtils.canonicalNode(input);
                int bucket = OnlineProtocolUtils.bucket(input.get("experiment_id").asText(), input.get("document_id").asText(), input.get("seed").asText());
                OnlineProtocolUtils.variant(bucket, input.get("weight").asInt());
            }).as(vector.get("name").asText()).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> OnlineProtocolUtils.object("{\"a\":1,\"a\":2}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OnlineProtocolUtils.object("{} {}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OnlineProtocolUtils.object("{\"a\":0.5}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OnlineProtocolUtils.canonical("online.fixture", Map.of("invalid", "\ud800")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
