package com.agentdoc.agent.service;

import com.agentdoc.agent.enums.McpAuthType;
import com.agentdoc.agent.mapper.AgentMcpBindingMapper;
import com.agentdoc.agent.mapper.McpServerMapper;
import com.agentdoc.agent.pojo.dto.AgentMcpBindingItemDTO;
import com.agentdoc.agent.pojo.dto.AgentMcpBindingReplaceDTO;
import com.agentdoc.agent.pojo.dto.McpServerCreateDTO;
import com.agentdoc.agent.pojo.dto.McpServerUpdateDTO;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.McpServerEntity;
import com.agentdoc.agent.security.AgentConfigCryptoService;
import com.agentdoc.agent.security.McpEndpointSecurityValidator;
import com.agentdoc.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpConcurrencyContractTest {

    @Test
    void locksMcpServersWhenReplacingBindings() {
        AgentService agentService = mock(AgentService.class);
        SpaceAccessService spaceAccessService = mock(SpaceAccessService.class);
        AgentMcpBindingMapper bindingMapper = mock(AgentMcpBindingMapper.class);
        McpServerService mcpServerService = mock(McpServerService.class);
        AgentMcpBindingService service = new AgentMcpBindingService(
                agentService, spaceAccessService, bindingMapper, mcpServerService, immediateTransaction());
        AgentEntity agent = new AgentEntity();
        agent.setId(1L);
        agent.setSpaceId(2L);
        agent.setConfigVersion(1L);
        McpServerEntity server = server(3L, 2L);
        when(agentService.require(1L)).thenReturn(agent);
        when(agentService.requireForUpdate(1L)).thenReturn(agent);
        when(mcpServerService.findByIdsForUpdate(List.of(3L))).thenReturn(List.of(server));
        when(bindingMapper.selectList(any())).thenReturn(List.of());

        service.replace(1L, new AgentMcpBindingReplaceDTO(
                List.of(new AgentMcpBindingItemDTO(3L, null))));

        verify(mcpServerService).findByIdsForUpdate(List.of(3L));
    }

    @Test
    void locksMcpServerBeforeUpdatingConfigVersion() {
        McpServerMapper mapper = mock(McpServerMapper.class);
        McpServerService service = new McpServerService(mapper, mock(AgentMcpBindingQueryService.class),
                mock(SpaceAccessService.class), mock(AgentConfigCryptoService.class),
                mock(McpEndpointSecurityValidator.class), mock(McpConnectionTester.class),
                immediateTransaction());
        McpServerEntity server = server(3L, 2L);
        server.setConfigVersion(4L);
        when(mapper.selectById(3L)).thenReturn(server);
        when(mapper.selectOne(any())).thenReturn(server);

        service.update(3L, new McpServerUpdateDTO(
                "Updated", "https://example.com/mcp", McpAuthType.NONE, null, null, 1));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<McpServerEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectOne(captor.capture());
        assertThat(captor.getValue().getCustomSqlSegment()).contains("FOR UPDATE");
        assertThat(server.getConfigVersion()).isEqualTo(5L);
    }

    @Test
    void locksMcpServersInStableIdOrder() {
        McpServerMapper mapper = mock(McpServerMapper.class);
        McpServerService service = new McpServerService(mapper, mock(AgentMcpBindingQueryService.class),
                mock(SpaceAccessService.class), mock(AgentConfigCryptoService.class),
                mock(McpEndpointSecurityValidator.class), mock(McpConnectionTester.class),
                immediateTransaction());
        when(mapper.selectList(any())).thenReturn(List.of());

        service.findByIdsForUpdate(List.of(5L, 3L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<McpServerEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(captor.capture());
        assertThat(captor.getValue().getCustomSqlSegment())
                .contains("ORDER BY id ASC", "FOR UPDATE");
    }

    @Test
    void translatesUniqueConstraintRaceToBusinessConflict() {
        McpServerMapper mapper = mock(McpServerMapper.class);
        McpServerService service = new McpServerService(mapper, mock(AgentMcpBindingQueryService.class),
                mock(SpaceAccessService.class), mock(AgentConfigCryptoService.class),
                mock(McpEndpointSecurityValidator.class), mock(McpConnectionTester.class),
                immediateTransaction());
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> service.create(new McpServerCreateDTO(
                2L, "example", "Example", "https://example.com/mcp", McpAuthType.NONE, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("serverKey");
    }

    private McpServerEntity server(Long id, Long spaceId) {
        McpServerEntity server = new McpServerEntity();
        server.setId(id);
        server.setSpaceId(spaceId);
        server.setServerKey("example");
        server.setDisplayName("Example");
        server.setEndpointUrl("https://example.com/mcp");
        server.setAuthType(McpAuthType.NONE.name());
        server.setStatus(1);
        server.setConfigVersion(1L);
        server.setConnectionStatus("UNTESTED");
        server.setDiscoveredToolCount(0);
        return server;
    }

    private TransactionTemplate immediateTransaction() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return new TransactionTemplate(manager);
    }
}
