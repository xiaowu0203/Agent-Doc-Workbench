package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.common.feign.vo.DocumentVersionExecutionContextVO;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskOriginalEvidenceAccessServiceTest {
    private final TaskMapper tasks = mock(TaskMapper.class);
    private final DocumentFeign documents = mock(DocumentFeign.class);
    private final TaskOriginalEvidenceAccessService service = new TaskOriginalEvidenceAccessService(tasks, documents);
    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg", "RS256")
                .subject("501").claim("scope", "user").build()));
        var row = new TaskEntity(); row.setId(61L); row.setSpaceId(10L); row.setAgentExecutionId(71L); row.setOnlineAssignmentId(51L);
        row.setDocumentId(41L); row.setDocumentVersionSnapshot(3L); row.setDocumentContentSha256("a".repeat(64)); when(tasks.selectById(61L)).thenReturn(row);
        when(documents.checkSpacePermission(eq(10L), anyString())).thenReturn(Result.ok());
        when(documents.getDocumentRefs(List.of(41L))).thenReturn(Result.ok(List.of(new DocumentRefVO(41L,10L,"非敏感标题"))));
        when(documents.getVersionExecutionContext(41L,3L,"a".repeat(64))).thenReturn(Result.ok(new DocumentVersionExecutionContextVO(41L,3L,"a".repeat(64),1L)));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void requiresCurrentTaskAndDocumentReadWithExactExecutionAndFrozenInput() {
        service.require(61L,10L,71L); verify(documents).checkSpacePermission(10L,"task:read"); verify(documents).checkSpacePermission(10L,"document:read");
        verify(documents).getVersionExecutionContext(41L,3L,"a".repeat(64));
    }
    @Test void changedExecutionOrSpaceCannotReadOriginal() {
        assertThatThrownBy(() -> service.require(61L,10L,72L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.require(61L,11L,71L)).isInstanceOf(BusinessException.class); verifyNoInteractions(documents);
    }
    @Test void documentPermissionRevocationStopsBeforeReadingFrozenVersion() {
        when(documents.checkSpacePermission(10L,"document:read")).thenReturn(Result.fail(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.require(61L,10L,71L)).isInstanceOf(BusinessException.class); verify(documents, never()).getDocumentRefs(any());
    }
    @Test void movedDocumentOrChangedFrozenHashCannotAuthorize() {
        when(documents.getDocumentRefs(any())).thenReturn(Result.ok(List.of(new DocumentRefVO(41L,11L,"移出空间"))));
        assertThatThrownBy(() -> service.require(61L,10L,71L)).isInstanceOf(BusinessException.class);
        when(documents.getDocumentRefs(any())).thenReturn(Result.ok(List.of(new DocumentRefVO(41L,10L,"原空间"))));
        when(documents.getVersionExecutionContext(anyLong(),anyLong(),anyString())).thenReturn(Result.ok(new DocumentVersionExecutionContextVO(41L,3L,"b".repeat(64),1L)));
        assertThatThrownBy(() -> service.require(61L,10L,71L)).isInstanceOf(BusinessException.class);
    }
}
