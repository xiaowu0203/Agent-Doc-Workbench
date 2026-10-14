package com.agentdoc.task.mcp;

import com.agentdoc.common.enums.ChangeOp;
import com.agentdoc.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springaicommunity.mcp.method.tool.utils.JsonSchemaGenerator;

import java.util.Arrays;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.*;

/** 使用实际 MCP 注解扫描器，防止 schema 公布无法被 Jackson 接受的枚举。 */
class WorkbenchMcpSchemaTest {
    @ParameterizedTest
    @CsvSource({"proposeChanges,append", "proposeChanges,replace", "applyDraftChanges,append", "applyDraftChanges,replace"})
    void publishedEnumMatchesActualPayloadAndDomainCodes(String method, String operation) throws Exception {
        var schema = JsonUtils.parse(JsonSchemaGenerator.generateForMethodInput(
                WorkbenchMcpTools.class.getMethod(method, McpChangeProposal.class)), JsonNode.class);
        var item = schema.path("properties").path("proposal").path("properties").path("changes").path("items");
        var values = StreamSupport.stream(item.path("properties").path("op").path("enum").spliterator(), false)
                .map(JsonNode::asText).toList();
        assertThat(values).containsExactlyElementsOf(Arrays.stream(ChangeOp.values()).map(ChangeOp::getCode).toList());
        assertThat(values).contains(operation);
        var payload = JsonUtils.parseStrict("{\"baseVersion\":0,\"changes\":[{\"op\":\"" + operation
                + "\",\"oldText\":\"\",\"newText\":\"synthetic\"}],\"summary\":\"schema verification\"}", McpChangeProposal.class);
        assertThat(payload.baseVersion()).isZero();
        assertThat(payload.changes().getFirst().op()).isEqualTo(ChangeOp.fromCode(operation));
        assertThat(JsonUtils.parse(JsonUtils.toJson(payload), JsonNode.class).path("changes").get(0).path("op").asText()).isEqualTo(operation);
    }

    @ParameterizedTest @ValueSource(strings = {"APPEND", "unknown"})
    void unsupportedWireValuesRemainRejected(String operation) {
        assertThat(JsonUtils.parseStrict("{\"baseVersion\":0,\"changes\":[{\"op\":\"" + operation
                + "\",\"oldText\":\"\",\"newText\":\"synthetic\"}],\"summary\":\"schema verification\"}", McpChangeProposal.class)).isNull();
    }
}
