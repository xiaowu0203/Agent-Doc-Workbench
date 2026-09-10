package com.agentdoc.agent.service;

import com.agentdoc.agent.enums.McpAuthType;
import com.agentdoc.agent.mapper.McpServerMapper;
import com.agentdoc.agent.pojo.dto.McpServerCreateDTO;
import com.agentdoc.agent.pojo.entity.McpServerEntity;
import com.agentdoc.agent.pojo.vo.McpServerVO;
import com.agentdoc.agent.security.AgentConfigCryptoService;
import com.agentdoc.agent.security.McpEndpointSecurityValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpServerTemplateInstallationTest {

    @Test
    void encryptsSpaceCredentialAndPersistsTemplateSource() {
        McpServerMapper mapper = mock(McpServerMapper.class);
        AgentConfigCryptoService cryptoService = mock(AgentConfigCryptoService.class);
        McpServerService service = new McpServerService(mapper, mock(AgentMcpBindingQueryService.class),
                mock(SpaceAccessService.class), cryptoService, mock(McpEndpointSecurityValidator.class),
                mock(McpConnectionTester.class), immediateTransaction());
        when(mapper.selectCount(any())).thenReturn(0L);
        when(cryptoService.encrypt("space-secret")).thenReturn("encrypted-value");

        McpServerVO result = service.createFromTemplate(new McpServerCreateDTO(9L, "search-api",
                "Search API", "https://example.com/mcp", McpAuthType.BEARER, null, "space-secret"),
                11L, 3L);

        ArgumentCaptor<McpServerEntity> captor = ArgumentCaptor.forClass(McpServerEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getTemplateId()).isEqualTo(11L);
        assertThat(captor.getValue().getTemplateVersion()).isEqualTo(3L);
        assertThat(captor.getValue().getEncryptedAuthToken()).isEqualTo("encrypted-value");
        assertThat(captor.getValue().toString()).doesNotContain("space-secret", "encrypted-value");
        assertThat(result.authConfigured()).isTrue();
    }

    private TransactionTemplate immediateTransaction() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return new TransactionTemplate(manager);
    }
}
