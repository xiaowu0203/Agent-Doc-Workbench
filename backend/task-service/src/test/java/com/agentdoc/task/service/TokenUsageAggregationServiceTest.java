package com.agentdoc.task.service;

import com.agentdoc.task.mapper.TokenDailySnapshotMapper;
import com.agentdoc.task.mapper.TokenUsageDetailMapper;
import com.agentdoc.task.mapper.TokenUsageMapper;
import com.agentdoc.task.pojo.entity.TokenUsageEntity;
import com.agentdoc.task.pojo.entity.TokenDailySnapshotEntity;
import com.agentdoc.task.pojo.vo.TokenUsageAggregateRow;
import com.agentdoc.task.pojo.vo.TokenUsageSnapshotRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenUsageAggregationServiceTest {

    @Mock private TokenUsageDetailMapper detailMapper;
    @Mock private TokenUsageMapper usageMapper;
    @Mock private TokenDailySnapshotMapper snapshotMapper;

    @Test
    void aggregatesRowsInOneBatchInsert() {
        var service = new TokenUsageAggregationService(detailMapper, usageMapper, snapshotMapper);
        LocalDate date = LocalDate.of(2026, 9, 22);
        LocalDate end = date.plusDays(1);
        List<TokenUsageAggregateRow> spaceRows = List.of(
                new TokenUsageAggregateRow(9L, 9L, 100L),
                new TokenUsageAggregateRow(10L, 10L, 30L));
        when(detailMapper.aggregateSpace(date, end)).thenReturn(spaceRows);
        when(detailMapper.aggregateDocument(date, end)).thenReturn(List.of());
        when(detailMapper.aggregateTask(date, end)).thenReturn(List.of());
        when(detailMapper.aggregateAgent(date, end)).thenReturn(List.of());

        service.aggregate(date);

        ArgumentCaptor<List<TokenUsageEntity>> rows = ArgumentCaptor.forClass(List.class);
        verify(usageMapper).insertBatch(rows.capture());
        assertThat(rows.getValue()).hasSize(2);
        assertThat(rows.getValue().getFirst().getDimension()).isEqualTo(1);
        verify(usageMapper, never()).insert(any(TokenUsageEntity.class));
    }

    @Test
    void snapshotsTodayWithOneSpaceSummaryQuery() {
        var service = new TokenUsageAggregationService(detailMapper, usageMapper, snapshotMapper);
        LocalDate date = LocalDate.of(2026, 9, 23);
        LocalDate end = date.plusDays(1);
        when(detailMapper.summarizeBySpaceByDate(date, end)).thenReturn(List.of(
                new TokenUsageSnapshotRow(9L, 40L, 60L, new BigDecimal("0.20"), false),
                new TokenUsageSnapshotRow(10L, 5L, 5L, new BigDecimal("0.10"), true)));

        service.snapshotToday();

        ArgumentCaptor<List<TokenDailySnapshotEntity>> snapshots =
                ArgumentCaptor.forClass(List.class);
        verify(snapshotMapper).insertBatch(snapshots.capture());
        var first = snapshots.getValue().getFirst();
        var second = snapshots.getValue().getLast();
        assertThat(first.getSpaceId()).isEqualTo(9L);
        assertThat(first.getTotalInput()).isEqualTo(40L);
        assertThat(first.getTotalOutput()).isEqualTo(60L);
        assertThat(first.getTotalEstimatedCost()).isEqualByComparingTo("0.20");
        assertThat(second.getSpaceId()).isEqualTo(10L);
        assertThat(second.getTotalInput()).isEqualTo(5L);
        assertThat(second.getTotalOutput()).isEqualTo(5L);
        assertThat(second.getTotalEstimatedCost()).isNull();
        verify(detailMapper, never()).listSpacesByDate(any(), any());
        verify(detailMapper, never()).sumInputBySpaceAndDate(any(), any(), any());
        verify(detailMapper, never()).sumOutputBySpaceAndDate(any(), any(), any());
        verify(detailMapper, never()).sumCostBySpaceAndDate(any(), any(), any());
        verify(detailMapper, never()).hasNullCostBySpaceAndDate(any(), any(), any());
        verify(snapshotMapper, never()).insert(any(TokenDailySnapshotEntity.class));
    }
}
