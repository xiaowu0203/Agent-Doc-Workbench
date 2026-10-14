package com.agentdoc.agent.service;

import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.mapper.AgentOnlineOriginalTextMapper;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.pojo.entity.AgentOnlineOriginalTextEntity;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.OnlineReportFactsQueryDTO;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnlineReportExecutionFactsServiceTest {
    @BeforeEach void metadata() { TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "report-original"), AgentOnlineOriginalTextEntity.class); }
    private final AgentExecutionMapper executions = mock(AgentExecutionMapper.class);
    private final AgentOnlineOriginalTextMapper originals = mock(AgentOnlineOriginalTextMapper.class);
    private final AgentExecutionQueryService query = mock(AgentExecutionQueryService.class);
    private final SpaceAccessService access = mock(SpaceAccessService.class);
    private final OnlineReportExecutionFactsService service = new OnlineReportExecutionFactsService(executions, query, access, originals);
    private final OnlineTaskBindingDTO binding = new OnlineTaskBindingDTO("61", "11", "a".repeat(64), "1", "BASELINE", 9000, "50", "10", "20", "30", "40", "1", "b".repeat(64), 1, "c".repeat(64), "71", 2, "d".repeat(64), "e".repeat(64), "f".repeat(64), "100", 600, "LIVE", "ORIGINAL", 2);
    @Test void actualExecutionScopeIsVerifiedAndNoMissingExecutionIsConvertedToCompletion() {
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserIdOrException).thenReturn(502L); var request = new OnlineReportFactsQueryDTO("20", "11", List.of(binding)); when(executions.selectList(any())).thenReturn(List.of()); when(originals.selectList(any())).thenReturn(List.of());
            assertThat(service.facts(request).getFirst().state()).isEqualTo("ABSENT");
            var row = new AgentExecutionEntity(); row.setId(81L); row.setWorkbenchTaskId(10L); row.setSpaceId(20L); row.setAgentId(30L); row.setOnlineExperimentId(11L); row.setOnlineAssignmentId(61L); row.setOnlineBindingSchemaVersion(2); row.setOnlineBindingHash(OnlineProtocolUtils.hash("online.binding", binding)); row.setStatus("FAILED");
            when(executions.selectList(any())).thenReturn(List.of(row)); assertThat(service.facts(request).getFirst().status()).isEqualTo("FAILED");
            row.setOnlineAssignmentId(62L); assertThatThrownBy(() -> service.facts(request)).isInstanceOf(BusinessException.class);
        }
    }
    @Test void unauthenticatedWorkerCannotReadReportSourceWithForgedHumanFields() {
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserIdOrException).thenThrow(new BusinessException(401, "必须当前人类"));
            assertThatThrownBy(() -> service.facts(new OnlineReportFactsQueryDTO("20", "11", List.of(binding)))).isInstanceOf(BusinessException.class); verifyNoInteractions(executions, originals, query, access);
        }
    }
}
