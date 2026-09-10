package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.vo.AgentToolSourceCountVO;
import com.agentdoc.common.feign.vo.AgentToolUsageStatsVO;
import com.agentdoc.common.feign.vo.SpaceUsageBudgetVO;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.mapper.TokenUsageDetailMapper;
import com.agentdoc.task.mapper.TokenUsageMapper;
import com.agentdoc.task.pojo.param.TokenUsageDashboardParam;
import com.agentdoc.task.pojo.vo.TokenUsageDailyRow;
import com.agentdoc.task.pojo.vo.TokenUsageDashboardVO;
import com.agentdoc.task.pojo.vo.TokenUsageStatisticsRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenUsageServiceTest {

    @Mock private TokenUsageDetailMapper detailMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private TokenUsageMapper usageMapper;
    @Mock private DocumentFeign documentFeign;
    @Mock private AgentFeign agentFeign;

    private TokenUsageService service;

    @BeforeEach
    void setUp() {
        service = new TokenUsageService(detailMapper, taskMapper, usageMapper, documentFeign, agentFeign);
        when(documentFeign.checkSpacePermission(1L, "usage:read")).thenReturn(Result.ok());
        when(documentFeign.getSpaceTokenBudget(1L))
                .thenReturn(Result.ok(new SpaceUsageBudgetVO(1L, 10_000L)));
        when(detailMapper.sumTokensBySpaceAndDate(anyLong(), any(), any())).thenReturn(400L);
    }

    @Test
    void buildsContinuousTrendAndKeepsToolSourceSummary() {
        TokenUsageStatisticsRow current = new TokenUsageStatisticsRow(
                2L, 120L, 80L, new BigDecimal("0.500000"), 2L,
                0L, 0L, 0L, 1L, 0L);
        TokenUsageStatisticsRow previous = new TokenUsageStatisticsRow(
                1L, 50L, 50L, new BigDecimal("0.200000"), 1L,
                0L, 0L, 0L, 0L, 0L);
        when(detailMapper.summarize(anyLong(), any(), any(), isNull(), isNull(), isNull()))
                .thenReturn(current, previous);
        when(detailMapper.summarizeDaily(anyLong(), any(), any(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(new TokenUsageDailyRow(
                        LocalDate.of(2026, 9, 2), 2L, 120L, 80L,
                        new BigDecimal("0.500000"), 0L, 0L, 0L, 1L, 0L)));
        when(agentFeign.getToolUsageStats(any()))
                .thenReturn(Result.ok(new AgentToolUsageStatsVO(3,
                                List.of(new AgentToolSourceCountVO("WORKBENCH_MCP", 3)))),
                        Result.ok(new AgentToolUsageStatsVO(1, List.of())));

        TokenUsageDashboardVO result = service.dashboard(new TokenUsageDashboardParam(
                1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2),
                null, null, null));

        assertThat(result.summary().tokens()).isEqualTo(200L);
        assertThat(result.summary().toolCalls()).isEqualTo(3L);
        assertThat(result.summary().inputTokensEstimated()).isTrue();
        assertThat(result.trend()).hasSize(2);
        assertThat(result.trend().getFirst().hasData()).isFalse();
        assertThat(result.trend().getLast().tokens()).isEqualTo(200L);
        assertThat(result.monthlyTokenBudget()).isEqualTo(10_000L);
    }
}
