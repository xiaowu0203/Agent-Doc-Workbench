package com.agentdoc.task.service;

import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.task.a2a.A2aTokenUsage;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.mapper.TokenUsageDetailMapper;
import com.agentdoc.task.mapper.TokenUsageMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.entity.TokenUsageDetailEntity;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenUsageLedgerTest {

    @Mock private TokenUsageDetailMapper detailMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private TokenUsageMapper usageMapper;
    @Mock private DocumentFeign documentFeign;
    @Mock private AgentFeign agentFeign;

    private TokenUsageService service;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("com.agentdoc.task.mapper.TaskMapper");
        TableInfoHelper.initTableInfo(assistant, TaskEntity.class);
        service = new TokenUsageService(detailMapper, taskMapper, usageMapper, documentFeign, agentFeign);
    }

    @Test
    void recordsOneSelfContainedLedgerRowUsingFrozenPrice() {
        TaskEntity task = task();
        LocalDateTime capturedAt = LocalDateTime.of(2026, 9, 14, 20, 0);
        A2aTokenUsage usage = new A2aTokenUsage(
                1_000L, 200L, 500L, false, false, true,
                91L, 12L, 4L, new BigDecimal("2.000000"), new BigDecimal("8.000000"),
                "CNY", 1, capturedAt);

        assertThat(service.recordRemote(task, usage)).isTrue();

        ArgumentCaptor<TokenUsageDetailEntity> captor = ArgumentCaptor.forClass(TokenUsageDetailEntity.class);
        verify(detailMapper).insert(captor.capture());
        TokenUsageDetailEntity detail = captor.getValue();
        assertThat(detail.getExecutionId()).isEqualTo(91L);
        assertThat(detail.getModelConfigVersion()).isEqualTo(4L);
        assertThat(detail.getInputPricePerMillion()).isEqualByComparingTo("2.000000");
        assertThat(detail.getOutputPricePerMillion()).isEqualByComparingTo("8.000000");
        assertThat(detail.getCurrency()).isEqualTo("CNY");
        assertThat(detail.getPricingCapturedAt()).isEqualTo(capturedAt);
        assertThat(detail.getEstimatedCost()).isEqualByComparingTo("0.006000");
        assertThat(detail.getTraceId()).isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(task.getTokensUsed()).isEqualTo(1_500L);
        assertThat(task.getTokensEstimated()).isTrue();
    }

    @Test
    void duplicateExecutionCallbackIsAnIdempotentRead() {
        TokenUsageDetailEntity existing = new TokenUsageDetailEntity();
        existing.setExecutionId(91L);
        when(detailMapper.selectOne(any())).thenReturn(existing);

        assertThat(service.recordRemote(task(), usage())).isTrue();

        verify(detailMapper, never()).insert(any(TokenUsageDetailEntity.class));
        verify(taskMapper, never()).update(any(), any());
    }

    @Test
    void recordsBusinessUsageWithNullTraceWhenTaskHasNoTelemetryTrace() {
        TaskEntity task = task();
        task.setTraceId(null);

        assertThat(service.recordRemote(task, usage())).isTrue();

        ArgumentCaptor<TokenUsageDetailEntity> captor = ArgumentCaptor.forClass(TokenUsageDetailEntity.class);
        verify(detailMapper).insert(captor.capture());
        assertThat(captor.getValue().getTraceId()).isNull();
    }

    private TaskEntity task() {
        TaskEntity task = new TaskEntity();
        task.setId(11L);
        task.setSpaceId(1L);
        task.setAgentId(2L);
        task.setTraceId("0123456789abcdef0123456789abcdef");
        task.setTokenBudget(10_000L);
        return task;
    }

    private A2aTokenUsage usage() {
        return new A2aTokenUsage(
                10L, null, 5L, false, false, false,
                91L, 12L, 4L, BigDecimal.ONE, BigDecimal.TEN,
                "CNY", 1, LocalDateTime.of(2026, 9, 14, 20, 0));
    }
}
