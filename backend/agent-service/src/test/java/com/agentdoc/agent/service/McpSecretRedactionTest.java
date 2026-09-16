package com.agentdoc.agent.service;

import com.agentdoc.agent.enums.McpAuthType;
import com.agentdoc.agent.execution.context.ExternalMcpConnection;
import com.agentdoc.agent.pojo.dto.McpServerCreateDTO;
import com.agentdoc.agent.pojo.entity.McpServerEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpSecretRedactionTest {

    @Test
    void secretBearingObjectsDoNotExposeSensitiveConfigurationInToString() {
        String secret = "plain-or-cipher-secret";
        String endpoint = "https://user:password@example.com/mcp?api_key=query-secret";
        String authParamName = "api_key";
        McpServerEntity entity = new McpServerEntity();
        entity.setEncryptedAuthToken(secret);
        ExternalMcpConnection connection = new ExternalMcpConnection(1L, "demo", "Demo",
                endpoint, McpAuthType.QUERY_PARAM.name(), authParamName, secret, 1L, List.of("private_tool"));
        McpServerCreateDTO dto = new McpServerCreateDTO(1L, "demo", "Demo",
                "https://example.com/mcp", McpAuthType.BEARER, null, secret);

        assertThat(entity.toString()).doesNotContain(secret);
        assertThat(connection.toString())
                .contains("serverId=1", "configVersion=1", "bindingToolCount=1", "sensitiveConfiguration=[REDACTED]")
                .doesNotContain(secret, endpoint, authParamName, "private_tool", "demo", "Demo", "QUERY_PARAM");
        assertThat(dto.toString()).doesNotContain(secret);
    }
}
