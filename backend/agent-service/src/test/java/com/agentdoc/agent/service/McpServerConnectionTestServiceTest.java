package com.agentdoc.agent.service;

import com.agentdoc.agent.enums.McpAuthType;
import com.agentdoc.agent.enums.McpConnectionStatus;
import com.agentdoc.agent.mapper.McpServerMapper;
import com.agentdoc.agent.pojo.dto.McpServerUpdateDTO;
import com.agentdoc.agent.pojo.entity.McpServerEntity;
import com.agentdoc.agent.pojo.vo.McpConnectionTestVO;
import com.agentdoc.agent.pojo.vo.McpToolVO;
import com.agentdoc.agent.security.AgentConfigCryptoService;
import com.agentdoc.agent.security.McpEndpointSecurityValidator;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpServerConnectionTestServiceTest {

    @Test
    void persistsSuccessfulTestAndDiscoveredTools() {
        Fixture fixture = fixture();
        LocalDateTime testedAt = LocalDateTime.of(2026, 9, 2, 10, 30);
        McpToolVO tool = new McpToolVO("search", "搜索", "{\"type\":\"object\"}");
        when(fixture.connectionTester.test(fixture.server)).thenReturn(
                new McpConnectionTester.TestOutcome(McpConnectionStatus.SUCCESS,
                        testedAt, 125L, null, List.of(tool)));

        McpConnectionTestVO result = fixture.service.testConnection(3L);

        assertThat(result.connected()).isTrue();
        assertThat(result.tools()).containsExactly(tool);
        assertThat(fixture.server.getConnectionStatus()).isEqualTo("SUCCESS");
        assertThat(fixture.server.getLastTestedAt()).isEqualTo(testedAt);
        assertThat(fixture.server.getLastTestDurationMs()).isEqualTo(125L);
        assertThat(fixture.server.getDiscoveredToolCount()).isEqualTo(1);
        assertThat(fixture.server.getDiscoveredToolsJson()).contains("search");
        assertThat(fixture.service.tools(3L)).containsExactly(tool);
        verify(fixture.mapper).updateById(fixture.server);
    }

    @Test
    void failedTestKeepsLastSuccessfulToolSnapshot() {
        Fixture fixture = fixture();
        fixture.server.setDiscoveredToolCount(1);
        fixture.server.setDiscoveredToolsJson("[{\"name\":\"old\",\"description\":null,"
                + "\"inputSchema\":\"{}\"}]");
        LocalDateTime oldDiscoveredAt = LocalDateTime.of(2026, 9, 1, 9, 0);
        fixture.server.setToolsDiscoveredAt(oldDiscoveredAt);
        when(fixture.connectionTester.test(fixture.server)).thenReturn(
                new McpConnectionTester.TestOutcome(McpConnectionStatus.FAILED,
                        LocalDateTime.of(2026, 9, 2, 10, 30), 80L, "连接超时", List.of()));

        McpConnectionTestVO result = fixture.service.testConnection(3L);

        assertThat(result.connected()).isFalse();
        assertThat(fixture.server.getConnectionStatus()).isEqualTo("FAILED");
        assertThat(fixture.server.getLastTestError()).isEqualTo("连接超时");
        assertThat(fixture.server.getDiscoveredToolCount()).isEqualTo(1);
        assertThat(fixture.server.getToolsDiscoveredAt()).isEqualTo(oldDiscoveredAt);
    }

    @Test
    void changingConnectionConfigurationInvalidatesTestAndTools() {
        Fixture fixture = fixture();
        fixture.server.setConnectionStatus("SUCCESS");
        fixture.server.setLastTestedAt(LocalDateTime.now());
        fixture.server.setDiscoveredToolCount(1);
        fixture.server.setDiscoveredToolsJson("[]");
        fixture.server.setToolsDiscoveredAt(LocalDateTime.now());

        fixture.service.update(3L, new McpServerUpdateDTO(
                "Example", "https://api.example.com/mcp", McpAuthType.NONE, null, null, 1));

        assertThat(fixture.server.getConnectionStatus()).isEqualTo("UNTESTED");
        assertThat(fixture.server.getLastTestedAt()).isNull();
        assertThat(fixture.server.getDiscoveredToolCount()).isZero();
        assertThat(fixture.server.getDiscoveredToolsJson()).isNull();
        assertThat(fixture.server.getToolsDiscoveredAt()).isNull();
    }

    private Fixture fixture() {
        McpServerMapper mapper = mock(McpServerMapper.class);
        McpConnectionTester connectionTester = mock(McpConnectionTester.class);
        McpServerEntity server = new McpServerEntity();
        server.setId(3L);
        server.setSpaceId(2L);
        server.setServerKey("example");
        server.setDisplayName("Example");
        server.setEndpointUrl("https://example.com/mcp");
        server.setAuthType(McpAuthType.NONE.name());
        server.setStatus(1);
        server.setConfigVersion(1L);
        server.setConnectionStatus(McpConnectionStatus.UNTESTED.name());
        server.setDiscoveredToolCount(0);
        when(mapper.selectById(3L)).thenReturn(server);
        when(mapper.selectOne(any())).thenReturn(server);
        McpServerService service = new McpServerService(mapper,
                mock(AgentMcpBindingQueryService.class), mock(SpaceAccessService.class),
                mock(AgentConfigCryptoService.class), mock(McpEndpointSecurityValidator.class),
                connectionTester, immediateTransaction());
        return new Fixture(service, mapper, connectionTester, server);
    }

    private TransactionTemplate immediateTransaction() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return new TransactionTemplate(manager);
    }

    private record Fixture(McpServerService service, McpServerMapper mapper,
                           McpConnectionTester connectionTester, McpServerEntity server) {
    }
}
