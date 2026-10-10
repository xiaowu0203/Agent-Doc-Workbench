package com.agentdoc.agent.service;

import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.mapper.AgentOnlineOriginalTextMapper;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.pojo.entity.AgentOnlineOriginalTextEntity;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentOnlineOriginalTextServiceTest {
    private final AgentOnlineOriginalTextMapper originals = mock(AgentOnlineOriginalTextMapper.class);
    private final AgentExecutionMapper executions = mock(AgentExecutionMapper.class);
    private final TaskFeign tasks = mock(TaskFeign.class);
    private final AgentOnlineOriginalTextService service = new AgentOnlineOriginalTextService(originals, executions, tasks);
    private AgentExecutionEntity execution;
    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "original-text"), AgentExecutionEntity.class);
        var jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject("501").claim("scope", "user").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        execution = new AgentExecutionEntity(); execution.setId(71L); execution.setWorkbenchTaskId(61L); execution.setSpaceId(10L); execution.setAgentId(20L);
        execution.setOnlineExperimentId(11L); execution.setOnlineAssignmentId(51L); execution.setOnlineBindingSchemaVersion(2);
        execution.setOnlineBindingHash("a".repeat(64)); execution.setStatus("COMPLETED"); execution.setFinishedAt(LocalDateTime.of(2026,10,10,20,0,0,123456789));
        execution.setResultSummary("不能从摘要补证据");
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    private AgentOnlineOriginalTextEntity capture(String text) {
        service.capture(execution, text); var value = ArgumentCaptor.forClass(AgentOnlineOriginalTextEntity.class);
        verify(originals).insert(value.capture()); return value.getValue();
    }
    @Test void capturesExactRuntimeTextAndReusesOriginalOnSameContentRetry() {
        var row = capture(" 原始😀\r\n文本 ");
        assertThat(row.getOriginalText()).isEqualTo(" 原始😀\r\n文本 "); assertThat(row.getContentHash()).isEqualTo(StableSnapshotUtils.sha256Utf8(row.getOriginalText()));
        assertThat(row.getCapturedAt().getNano()).isEqualTo(123000000);
        doThrow(new DuplicateKeyException("duplicate")).when(originals).insert(any(AgentOnlineOriginalTextEntity.class));
        when(originals.selectById(71L)).thenReturn(row); execution.setFinishedAt(execution.getFinishedAt().plusSeconds(1));
        service.capture(execution, row.getOriginalText()); verify(originals, never()).updateById(any(AgentOnlineOriginalTextEntity.class));
        assertThatThrownBy(() -> service.capture(execution, "更换内容")).isInstanceOf(BusinessException.class);
    }
    @Test void missingHistoricalCaptureReturnsUnavailableRatherThanSummary() {
        when(executions.selectOne(any())).thenReturn(execution); when(tasks.checkOriginalEvidencePermission(61L,10L,71L)).thenReturn(Result.ok());
        var result = service.read(61L,10L); assertThat(result.state()).isEqualTo("EVIDENCE_UNAVAILABLE"); assertThat(result.originalText()).isNull();
    }
    @Test void revokedPermissionDoesNotReadBodyAndAgentCapabilityCannotRead() {
        when(executions.selectOne(any())).thenReturn(execution); when(tasks.checkOriginalEvidencePermission(61L,10L,71L)).thenReturn(Result.fail(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.read(61L,10L)).isInstanceOf(BusinessException.class); verify(originals, never()).selectById(any());
        var jwt = Jwt.withTokenValue("task").header("alg", "RS256").subject("61").claim("scope", "agent").claim("actorType", "AGENT").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt)); clearInvocations(executions);
        assertThatThrownBy(() -> service.read(61L,10L)).isInstanceOf(BusinessException.class); verifyNoInteractions(executions);
    }
    @Test void originalReadChecksCurrentBindingAndDetectsStoredBodyCorruption() {
        var row = capture("原始文本"); when(originals.selectById(71L)).thenReturn(row); when(executions.selectOne(any())).thenReturn(execution);
        when(tasks.checkOriginalEvidencePermission(61L,10L,71L)).thenReturn(Result.ok());
        assertThat(service.read(61L,10L).originalText()).isEqualTo("原始文本");
        row.setOriginalText("被改写"); assertThatThrownBy(() -> service.read(61L,10L)).isInstanceOf(BusinessException.class);
    }
}
