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
    void secretBearingObjectsDoNotExposeTokenInToString() {
        String secret = "plain-or-cipher-secret";
        McpServerEntity entity = new McpServerEntity();
        entity.setEncryptedAuthToken(secret);
        ExternalMcpConnection connection = new ExternalMcpConnection(1L, "demo", "Demo",
                "https://example.com/mcp", McpAuthType.BEARER.name(), secret, 1L, List.of());
        McpServerCreateDTO dto = new McpServerCreateDTO(1L, "demo", "Demo",
                "https://example.com/mcp", McpAuthType.BEARER, secret);

        assertThat(entity.toString()).doesNotContain(secret);
        assertThat(connection.toString()).doesNotContain(secret);
        assertThat(dto.toString()).doesNotContain(secret);
    }
}
