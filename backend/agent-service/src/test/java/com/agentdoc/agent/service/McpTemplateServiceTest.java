package com.agentdoc.agent.service;

import com.agentdoc.agent.enums.McpAuthType;
import com.agentdoc.agent.enums.McpConnectionStatus;
import com.agentdoc.agent.enums.TemplateVersionStatus;
import com.agentdoc.agent.mapper.McpTemplateMapper;
import com.agentdoc.agent.mapper.McpTemplateVersionMapper;
import com.agentdoc.agent.pojo.dto.McpServerCreateDTO;
import com.agentdoc.agent.pojo.dto.McpTemplateInstallDTO;
import com.agentdoc.agent.pojo.dto.McpTemplateVersionCreateDTO;
import com.agentdoc.agent.pojo.entity.McpTemplateEntity;
import com.agentdoc.agent.pojo.entity.McpTemplateVersionEntity;
import com.agentdoc.agent.pojo.vo.McpConnectionTestVO;
import com.agentdoc.agent.pojo.vo.McpServerVO;
import com.agentdoc.agent.security.McpEndpointSecurityValidator;
import com.agentdoc.common.constant.JwtConstant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpTemplateServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updatesDraftThenPublishesDisablesAndRestoresVersion() {
        McpTemplateMapper mapper = mock(McpTemplateMapper.class);
        McpTemplateVersionMapper versionMapper = mock(McpTemplateVersionMapper.class);
        McpTemplateService service = service(mapper, versionMapper, mock(McpServerService.class));
        McpTemplateEntity template = template();
        McpTemplateVersionEntity version = version(31L, 1, "https://example.com/old");
        version.setStatus(TemplateVersionStatus.DRAFT.getCode());
        when(versionMapper.selectById(31L)).thenReturn(version);
        when(mapper.selectById(11L)).thenReturn(template);
        login(1001L);

        service.updateVersion(31L, new McpTemplateVersionCreateDTO(
                "Search API v1", "https://example.com/new", McpAuthType.NONE, null));
        assertThat(version.getEndpointUrl()).isEqualTo("https://example.com/new");

        service.publish(31L);
        assertThat(version.getStatus()).isEqualTo(TemplateVersionStatus.PUBLISHED.getCode());
        service.disableVersion(31L);
        assertThat(version.getStatus()).isEqualTo(TemplateVersionStatus.DISABLED.getCode());
        service.enableVersion(31L);
        assertThat(version.getStatus()).isEqualTo(TemplateVersionStatus.PUBLISHED.getCode());
    }

    @Test
    void keepsPublishedHistoricalVersionResolvableAfterNewVersionExists() {
        McpTemplateMapper mapper = mock(McpTemplateMapper.class);
        McpTemplateVersionMapper versionMapper = mock(McpTemplateVersionMapper.class);
        McpTemplateService service = service(mapper, versionMapper, mock(McpServerService.class));
        McpTemplateEntity template = template();
        McpTemplateVersionEntity historical = version(31L, 1, "https://example.com/v1");
        when(versionMapper.selectBatchIds(any())).thenReturn(List.of(historical));
        when(mapper.selectBatchIds(any())).thenReturn(List.of(template));

        assertThat(service.requirePublishedVersions(List.of(31L), true)).containsKey(31L);
    }

    @Test
    void installsCredentialOnlyIntoIndependentSpaceConnectionAndRunsDiscovery() {
        McpTemplateMapper mapper = mock(McpTemplateMapper.class);
        McpTemplateVersionMapper versionMapper = mock(McpTemplateVersionMapper.class);
        McpServerService mcpServerService = mock(McpServerService.class);
        SkillAuditLogService auditLogService = mock(SkillAuditLogService.class);
        McpTemplateService service = service(mapper, versionMapper, mcpServerService, auditLogService);
        McpTemplateEntity template = template();
        McpTemplateVersionEntity version = version(31L, 3, "https://example.com/mcp");
        McpServerVO installed = serverVO();
        when(mapper.selectById(11L)).thenReturn(template);
        when(mapper.selectBatchIds(any())).thenReturn(List.of(template));
        when(versionMapper.selectById(31L)).thenReturn(version);
        when(versionMapper.selectBatchIds(any())).thenReturn(List.of(version));
        when(mcpServerService.createFromTemplate(any(), any(), any())).thenReturn(installed);
        when(mcpServerService.testConnection(21L)).thenReturn(new McpConnectionTestVO(21L, true,
                McpConnectionStatus.SUCCESS, LocalDateTime.now(), 10L, null, List.of()));
        when(mcpServerService.managementDetail(21L)).thenReturn(installed);

        McpServerVO result = service.install(9L, new McpTemplateInstallDTO(31L, "space-secret"));

        ArgumentCaptor<McpServerCreateDTO> captor = ArgumentCaptor.forClass(McpServerCreateDTO.class);
        verify(mcpServerService).createFromTemplate(captor.capture(), org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.eq(31L));
        assertThat(captor.getValue().authToken()).isEqualTo("space-secret");
        assertThat(result.authConfigured()).isTrue();
        verify(mcpServerService).testConnection(21L);
        verify(mapper, never()).updateById(any(McpTemplateEntity.class));
        verify(auditLogService).record(eq(9L), eq("MCP_TEMPLATE_INSTALLED"), eq("mcp_server"), eq(21L),
                argThat(detail -> detail.equals(Map.of("templateId", 11L, "templateVersionId", 31L))
                        && !detail.containsValue("space-secret")));
    }

    private McpServerVO serverVO() {
        return new McpServerVO(21L, 11L, 31L, 9L, "search-api", "Search API",
                "https://example.com/mcp", McpAuthType.BEARER, null, true, 1L, 1,
                McpConnectionStatus.SUCCESS, LocalDateTime.now(), 10L, null, 2, LocalDateTime.now());
    }

    private void login(long userId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(String.valueOf(userId))
                .claim(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_USER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, null, List.of()));
    }

    private McpTemplateService service(McpTemplateMapper mapper, McpTemplateVersionMapper versionMapper,
                                       McpServerService mcpServerService) {
        return service(mapper, versionMapper, mcpServerService, mock(SkillAuditLogService.class));
    }

    private McpTemplateService service(McpTemplateMapper mapper, McpTemplateVersionMapper versionMapper,
                                       McpServerService mcpServerService, SkillAuditLogService auditLogService) {
        return new McpTemplateService(mapper, versionMapper, mock(PlatformAccessService.class),
                mock(McpEndpointSecurityValidator.class), mcpServerService, auditLogService,
                immediateTransaction());
    }

    private McpTemplateEntity template() {
        McpTemplateEntity template = new McpTemplateEntity();
        template.setId(11L);
        template.setServerKey("search-api");
        template.setStatus(1);
        return template;
    }

    private McpTemplateVersionEntity version(Long id, int versionNo, String endpointUrl) {
        McpTemplateVersionEntity version = new McpTemplateVersionEntity();
        version.setId(id);
        version.setTemplateId(11L);
        version.setVersionNo(versionNo);
        version.setStatus(1);
        version.setDisplayName("Search API");
        version.setEndpointUrl(endpointUrl);
        version.setAuthType(McpAuthType.BEARER.name());
        return version;
    }

    private TransactionTemplate immediateTransaction() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return new TransactionTemplate(manager);
    }
}
