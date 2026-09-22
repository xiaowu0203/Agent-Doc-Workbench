package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.task.enums.ChangeRequestStatus;
import com.agentdoc.task.mapper.ChangeRequestMapper;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.ChangeRequestEntity;
import com.agentdoc.task.pojo.entity.TaskEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeRequestFeedbackProjectionServiceTest {
    @Mock private ChangeRequestMapper changeRequestMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private DocumentFeign documentFeign;

    private ChangeRequestFeedbackProjectionService service;

    @BeforeEach
    void setUp() {
        service = new ChangeRequestFeedbackProjectionService(changeRequestMapper, taskMapper, documentFeign);
    }

    @Test
    void projectsFinalApprovalAsStableRedactedSnapshot() {
        ChangeRequestEntity request = new ChangeRequestEntity();
        request.setId(11L); request.setSpaceId(9L); request.setSourceTaskId(21L);
        request.setStatus(ChangeRequestStatus.MERGED.getCode()); request.setResolutionType("PARTIAL");
        request.setReviewComment("private review"); request.setReviewedBy(31L);
        request.setReviewedAt(LocalDateTime.of(2026, 9, 21, 10, 0)); request.setRevisionNo(2);
        TaskEntity task = new TaskEntity(); task.setId(21L); task.setAgentExecutionId(41L);
        when(changeRequestMapper.selectById(11L)).thenReturn(request);
        when(taskMapper.selectById(21L)).thenReturn(task);
        when(documentFeign.checkSpacePermission(eq(9L), anyString())).thenReturn(Result.ok());

        var first = service.get(11L);
        var second = service.get(11L);

        assertThat(first).isEqualTo(second);
        assertThat(first.status()).isEqualTo("MERGED");
        assertThat(first.executionId()).isEqualTo(41L);
        assertThat(first.reviewCommentHash()).hasSize(64).doesNotContain("private review");
        assertThat(first.sourceHash()).hasSize(64);
    }

    @Test
    void rejectsNonFinalChangeRequest() {
        ChangeRequestEntity request = new ChangeRequestEntity();
        request.setId(11L); request.setStatus(ChangeRequestStatus.PENDING.getCode());
        when(changeRequestMapper.selectById(11L)).thenReturn(request);

        assertThatThrownBy(() -> service.get(11L)).isInstanceOf(BusinessException.class);
    }
}
