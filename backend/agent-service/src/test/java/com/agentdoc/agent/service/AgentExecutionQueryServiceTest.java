package com.agentdoc.agent.service;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.common.feign.dto.AgentToolCallSearchDTO;
import com.agentdoc.common.feign.dto.AgentToolUsageQueryDTO;
import com.agentdoc.common.utils.AuthUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;

import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.mapper.AgentExecutionModelCallMapper;
import com.agentdoc.agent.mapper.AgentExecutionToolCallMapper;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.pojo.entity.AgentExecutionToolCallEntity;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.vo.AgentExecutionAuditVO;
import com.agentdoc.common.feign.vo.AgentExecutionTokenUsageVO;
import com.agentdoc.common.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.USAGE_READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;

class AgentExecutionQueryServiceTest {

    private final AgentExecutionMapper executionMapper = mock(AgentExecutionMapper.class);
    private final AgentExecutionModelCallMapper modelCallMapper = mock(AgentExecutionModelCallMapper.class);
    private final AgentExecutionToolCallMapper toolCallMapper = mock(AgentExecutionToolCallMapper.class);
    private final SpaceAccessService spaceAccessService = mock(SpaceAccessService.class);
    private final OnlineEvaluationFeign online = mock(OnlineEvaluationFeign.class);
    private final AgentExecutionQueryService service = new AgentExecutionQueryService(
            executionMapper, modelCallMapper, toolCallMapper, spaceAccessService, online);

    @BeforeAll static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "tool-query"), AgentExecutionEntity.class);
    }

    private AgentToolCallSearchDTO searchRequest(int size) {
        return new AgentToolCallSearchDTO(new AgentToolUsageQueryDTO(7L, LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 10, 2, 0, 0), null, null, null, TaskExecutionMode.ISOLATED, false), 2, size);
    }

    @Test void independentToolPageReadsOnlyReturnedExecutionIdentitiesAndRecordsExposureBeforeReturning() {
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserId).thenReturn(502L);
            var call = new AgentExecutionToolCallEntity(); call.setExecutionId(3L); call.setToolName("document_read");
            var execution = execution(); execution.setOnlineExperimentId(91L);
            when(toolCallMapper.search(any(Page.class), any())).thenReturn(new Page<AgentExecutionToolCallEntity>(2, 10).setRecords(List.of(call)).setTotal(31));
            when(executionMapper.selectList(any())).thenReturn(List.of(execution));
            when(online.recordReviewExposure(any())).thenReturn(Result.ok());
            var result = service.searchToolCalls(searchRequest(10));
            assertThat(result.total()).isEqualTo(31); assertThat(result.records().getFirst().workbenchTaskId()).isEqualTo(11L);
            var order = inOrder(spaceAccessService, toolCallMapper, executionMapper, online);
            order.verify(spaceAccessService).requirePermission(7L, USAGE_READ); order.verify(spaceAccessService).requirePermission(7L, TASK_READ);
            order.verify(toolCallMapper).search(any(Page.class), eq(searchRequest(10).query()));
            order.verify(executionMapper).selectList(any()); order.verify(online).recordReviewExposure(any());
            verifyNoInteractions(modelCallMapper);
            when(online.recordReviewExposure(any())).thenReturn(Result.fail(ErrorCode.CONFLICT, "知情失败"));
            assertThatThrownBy(() -> service.searchToolCalls(searchRequest(10))).isInstanceOf(BusinessException.class);
        }
    }

    @Test void invalidOrDeniedToolQueryCannotLoadRows() {
        assertThatThrownBy(() -> service.searchToolCalls(searchRequest(101))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(toolCallMapper, executionMapper);
        doThrow(new BusinessException(403, "撤权")).when(spaceAccessService).requirePermission(7L, TASK_READ);
        assertThatThrownBy(() -> service.searchToolCalls(searchRequest(10))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(toolCallMapper, executionMapper);
    }

    @Test
    void returnsNullBeforeAgentExecutionIsCreated() {
        when(executionMapper.selectOne(any())).thenReturn(null);

        assertThat(service.getByWorkbenchTask(11L, 7L)).isNull();

        verify(spaceAccessService).requirePermission(7L, TASK_READ);
    }

    @Test
    void rejectsExecutionFromAnotherSpace() {
        AgentExecutionEntity execution = new AgentExecutionEntity();
        execution.setSpaceId(8L);
        when(executionMapper.selectOne(any())).thenReturn(execution);

        assertThatThrownBy(() -> service.getByWorkbenchTask(11L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void returnsOnlySanitizedSnapshotsAndOrderedAuditData() {
        AgentExecutionEntity execution = execution();
        AgentExecutionToolCallEntity toolCall = new AgentExecutionToolCallEntity();
        toolCall.setSequenceNo(1);
        toolCall.setToolName("skill_read_instructions");
        toolCall.setToolSource("SKILL_LOCAL");
        toolCall.setToolSourceKey("skill-local");
        toolCall.setSkillVersionId(22L);
        toolCall.setStatus("SUCCEEDED");
        toolCall.setStartedAt(LocalDateTime.now());
        when(executionMapper.selectOne(any())).thenReturn(execution);
        when(modelCallMapper.selectList(any())).thenReturn(List.of());
        when(toolCallMapper.selectList(any())).thenReturn(List.of(toolCall));

        AgentExecutionAuditVO result = service.getByWorkbenchTask(11L, 7L);

        assertThat(result.agentName()).isEqualTo("审计 Agent");
        assertThat(result.traceId()).isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(result.spanId()).isEqualTo("0123456789abcdef");
        assertThat(result.model().displayName()).isEqualTo("GPT Test");
        assertThat(result.skill().boundSkills()).singleElement()
                .satisfies(skill -> assertThat(skill.skillVersionId()).isEqualTo(22L));
        assertThat(result.toolCalls()).singleElement()
                .satisfies(call -> assertThat(call.skillVersionId()).isEqualTo(22L));
        assertThat(result.model().getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("baseUrl");
        assertThat(result.skill().boundSkills().getFirst().getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("storageKey", "instructionText");
    }

    @Test
    void returnsTokenLedgerIdentityFromFrozenModelSnapshot() {
        AgentExecutionEntity execution = execution();
        LocalDateTime capturedAt = LocalDateTime.of(2026, 9, 14, 20, 0);
        execution.setCreatedAt(capturedAt);
        execution.setModelSnapshot("""
                {"id":5,"modelKey":"gpt-test","inputPricePerMillion":2.000000,
                 "outputPricePerMillion":8.000000}
                """);
        execution.setInputTokens(1000L);
        execution.setOutputTokens(500L);
        when(executionMapper.selectOne(any())).thenReturn(execution);

        AgentExecutionTokenUsageVO result = service.getTokenUsageByWorkbenchTask(11L);

        assertThat(result.executionId()).isEqualTo(3L);
        assertThat(result.modelId()).isEqualTo(5L);
        assertThat(result.modelConfigVersion()).isEqualTo(6L);
        assertThat(result.inputPricePerMillion()).isEqualByComparingTo("2.000000");
        assertThat(result.outputPricePerMillion()).isEqualByComparingTo("8.000000");
        assertThat(result.currency()).isEqualTo("CNY");
        assertThat(result.pricingSchemaVersion()).isEqualTo(1);
        assertThat(result.pricingCapturedAt()).isEqualTo(capturedAt);
    }

    private AgentExecutionEntity execution() {
        AgentExecutionEntity execution = new AgentExecutionEntity();
        execution.setId(3L);
        execution.setWorkbenchTaskId(11L);
        execution.setSpaceId(7L);
        execution.setAgentId(9L);
        execution.setAgentNameSnapshot("审计 Agent");
        execution.setTraceId("0123456789abcdef0123456789abcdef");
        execution.setSpanId("0123456789abcdef");
        execution.setAgentConfigVersion(4L);
        execution.setMaxIterations(12);
        execution.setExecutionTimeoutSeconds(300);
        execution.setStatus("WORKING");
        execution.setModelSnapshot(JsonUtils.toJson(Map.of(
                "id", 5L, "modelKey", "gpt-test", "baseUrl", "https://secret.example")));
        execution.setModelDisplayNameSnapshot("GPT Test");
        execution.setModelConfigVersion(6L);
        execution.setSkillSnapshotJson("""
                [{"skillId":21,"skillVersionId":22,"versionNo":3,"name":"audit-skill",
                  "activationDescription":"审计文档","sha256":"package-hash",
                  "storageKey":"storage/secret","instructionText":"secret instruction"}]
                """);
        execution.setSelectedSkillVersionIdsJson("[22]");
        execution.setToolDefinitionSnapshotJson("[]");
        execution.setExternalMcpSnapshotJson("[]");
        return execution;
    }
}
