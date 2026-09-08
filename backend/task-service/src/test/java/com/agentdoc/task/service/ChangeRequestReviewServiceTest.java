package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.ApprovalMergeRequestDTO;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.common.feign.vo.MergeResultVO;
import com.agentdoc.task.enums.ActorType;
import com.agentdoc.task.enums.ChangeRequestResolutionType;
import com.agentdoc.task.enums.ChangeRequestStatus;
import com.agentdoc.task.enums.ChangeRequestType;
import com.agentdoc.task.mapper.ChangeRequestCommentMapper;
import com.agentdoc.task.mapper.ChangeRequestMapper;
import com.agentdoc.task.pojo.dto.ChangeRequestApproveDTO;
import com.agentdoc.task.pojo.entity.ChangeRequestEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeRequestReviewServiceTest {

    private static final long USER_ID = 1001L;
    private static final long SPACE_ID = 2001L;
    private static final long DOCUMENT_ID = 3001L;
    private static final long REQUEST_ID = 4001L;

    @Mock
    private ChangeRequestService changeRequestService;
    @Mock
    private ChangeRequestMapper changeRequestMapper;
    @Mock
    private ChangeRequestCommentMapper commentMapper;
    @Mock
    private DocumentFeign documentFeign;
    @Mock
    private AuthFeign authFeign;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private TaskService taskService;

    private ChangeRequestReviewService service;

    @BeforeEach
    void setUp() {
        service = new ChangeRequestReviewService(changeRequestService, changeRequestMapper, commentMapper,
                documentFeign, authFeign, auditLogService, taskService);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(String.valueOf(USER_ID))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(documentFeign.checkSpacePermission(anyLong(), any())).thenReturn(Result.ok());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRequireSelectedHunkForPartialApproval() {
        when(changeRequestService.requireRequest(REQUEST_ID)).thenReturn(request(ChangeRequestStatus.PENDING));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.approve(REQUEST_ID,
                new ChangeRequestApproveDTO(ChangeRequestResolutionType.PARTIAL, null, List.of(), "部分正文")));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), exception.getCode());
        verify(changeRequestMapper, never()).update(any(), any());
    }

    @Test
    void shouldMergeApprovedRequestThroughIdempotentDocumentContract() {
        ChangeRequestEntity approved = request(ChangeRequestStatus.APPROVED);
        ChangeRequestEntity merged = request(ChangeRequestStatus.MERGED);
        merged.setMergedVersion(3L);
        when(changeRequestService.requireRequest(REQUEST_ID)).thenReturn(approved, merged);
        when(documentFeign.mergeApprovedDocument(any(ApprovalMergeRequestDTO.class)))
                .thenReturn(Result.ok(new MergeResultVO(DOCUMENT_ID, "测试文档", 3L)));
        when(changeRequestMapper.update(any(), any())).thenReturn(1);
        when(documentFeign.getDocumentRefs(anyList()))
                .thenReturn(Result.ok(List.of(new DocumentRefVO(DOCUMENT_ID, SPACE_ID, "测试文档"))));

        var result = service.merge(REQUEST_ID);

        assertEquals(ChangeRequestStatus.MERGED, result.status());
        verify(documentFeign).mergeApprovedDocument(any(ApprovalMergeRequestDTO.class));
    }

    private ChangeRequestEntity request(ChangeRequestStatus status) {
        ChangeRequestEntity entity = new ChangeRequestEntity();
        entity.setId(REQUEST_ID);
        entity.setSpaceId(SPACE_ID);
        entity.setDocumentId(DOCUMENT_ID);
        entity.setRequestType(ChangeRequestType.FORMAL.getCode());
        entity.setChanges("[{\"op\":\"REPLACE\",\"newText\":\"新版内容\"}]");
        entity.setBaseVersion(2L);
        entity.setStatus(status.getCode());
        entity.setProposedBy(5001L);
        entity.setProposedActorType(ActorType.AGENT.getCode());
        entity.setRevisionNo(1);
        return entity;
    }
}
