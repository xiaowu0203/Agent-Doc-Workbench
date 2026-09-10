package com.agentdoc.agent.service;

import com.agentdoc.agent.mapper.McpServerMapper;
import com.agentdoc.agent.pojo.entity.McpServerEntity;
import com.agentdoc.agent.pojo.param.McpServerSearchParam;
import com.agentdoc.agent.security.AgentConfigCryptoService;
import com.agentdoc.agent.security.McpEndpointSecurityValidator;
import com.agentdoc.common.pojo.vo.PageVO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpServerPaginationTest {

    @Test
    void returnsBoundedPageUsingUnifiedDefaults() {
        McpServerMapper mapper = mock(McpServerMapper.class);
        McpServerEntity entity = new McpServerEntity();
        entity.setId(1L);
        entity.setSpaceId(2L);
        entity.setServerKey("demo");
        entity.setDisplayName("Demo");
        entity.setEndpointUrl("https://example.com/mcp");
        entity.setAuthType("NONE");
        entity.setConfigVersion(1L);
        entity.setStatus(1);
        entity.setConnectionStatus("UNTESTED");
        entity.setDiscoveredToolCount(0);
        Page<McpServerEntity> selected = new Page<>(1, 10, 1);
        selected.setRecords(List.of(entity));
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(selected);
        McpServerService service = new McpServerService(mapper,
                mock(AgentMcpBindingQueryService.class), mock(SpaceAccessService.class),
                mock(AgentConfigCryptoService.class), mock(McpEndpointSecurityValidator.class),
                mock(McpConnectionTester.class), immediateTransaction());
        McpServerSearchParam param = new McpServerSearchParam();
        param.setSpaceId(2L);

        PageVO<?> result = service.list(param);

        assertThat(result.pageNum()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(10);
        assertThat(result.records()).hasSize(1);
    }

    private TransactionTemplate immediateTransaction() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return new TransactionTemplate(manager);
    }
}
