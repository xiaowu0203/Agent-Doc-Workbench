package com.agentdoc.task.service;

import com.agentdoc.task.a2a.A2aTokenUsage;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.mapper.TokenUsageDetailMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.entity.TokenUsageDetailEntity;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OnlineTokenBudgetOverflowTest {
    @Test void overflowingLongTotalStillTriggersBudgetProtectionAndKeepsRawLedger() {
        var metadata = new MapperBuilderAssistant(new MybatisConfiguration(), "token-overflow");
        TableInfoHelper.initTableInfo(metadata, TaskEntity.class); TableInfoHelper.initTableInfo(metadata, TokenUsageDetailEntity.class);
        var tasks = mock(TaskMapper.class); var details = mock(TokenUsageDetailMapper.class);
        when(tasks.update(any(), any())).thenReturn(1);
        var task = new TaskEntity(); task.setId(1L); task.setSpaceId(2L); task.setAgentId(3L);
        task.setTokenBudget(Long.MAX_VALUE); task.setStatus(TaskStatus.RUNNING.getCode());
        var usage = new A2aTokenUsage(Long.MAX_VALUE, 0L, Long.MAX_VALUE, false, false, false,
                7L, 8L, 1L, BigDecimal.ONE, BigDecimal.ONE, "USD", 1, LocalDateTime.now());
        assertThat(new TokenUsageService(details, tasks, null, null, null).recordRemote(task, usage)).isFalse();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TERMINATED.getCode()); assertThat(task.getTokensUsed()).isNull();
        verify(details).insert(argThat((TokenUsageDetailEntity row) -> row.getInputTokens().equals(Long.MAX_VALUE) && row.getOutputTokens().equals(Long.MAX_VALUE)));
    }
}
