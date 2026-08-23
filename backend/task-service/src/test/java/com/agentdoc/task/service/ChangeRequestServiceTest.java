package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ChangeOp;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.ChangeItemDTO;
import com.agentdoc.common.feign.dto.MergeRequestDTO;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.common.feign.vo.MergeResultVO;
import com.agentdoc.task.enums.ChangeRequestStatus;
import com.agentdoc.task.enums.ChangeRequestType;
import com.agentdoc.task.mapper.ChangeRequestMapper;
import com.agentdoc.task.pojo.dto.ChangeRequestReviewDTO;
import com.agentdoc.task.pojo.dto.ChangeRequestSubmitDTO;
import com.agentdoc.task.pojo.entity.ChangeRequestEntity;
import com.agentdoc.task.pojo.vo.ChangeRequestVO;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ChangeRequestService} 单元测试：提交、状态机流转、审批合并与防并发冲突。
 */
@ExtendWith(MockitoExtension.class)
class ChangeRequestServiceTest {

    private static final long USER_ID = 1001L;
    private static final long DOCUMENT_ID = 3001L;
    private static final long REQUEST_ID = 4001L;

    @Mock
    private ChangeRequestMapper changeRequestMapper;
    @Mock
    private DocumentFeign documentFeign;

    private ChangeRequestService service;

    @BeforeEach
    void setUp() {
        service = new ChangeRequestService(changeRequestMapper, documentFeign);
        // 模拟已登录：SecurityContext 放入以 Jwt 为 principal 的认证信息
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(String.valueOf(USER_ID))
                .claim("username", "tester")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        // lenient：部分测试（状态机拒绝路径）不走到 toVO，无需该 stub
        lenient().when(documentFeign.getDocumentRefs(anyList()))
                .thenReturn(Result.ok(List.of(new DocumentRefVO(DOCUMENT_ID, 2001L, "测试文档"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 构造指定状态的变更请求实体 */
    private ChangeRequestEntity request(ChangeRequestStatus status) {
        ChangeRequestEntity entity = new ChangeRequestEntity();
        entity.setId(REQUEST_ID);
        entity.setDocumentId(DOCUMENT_ID);
        entity.setRequestType(ChangeRequestType.FORMAL.getCode());
        entity.setChanges("[{\"op\":\"replace\",\"newText\":\"新版内容\"}]");
        entity.setBaseVersion(2L);
        entity.setStatus(status.getCode());
        entity.setProposedBy(USER_ID);
        return entity;
    }

    private ChangeRequestSubmitDTO submitDTO() {
        return new ChangeRequestSubmitDTO(DOCUMENT_ID, ChangeRequestType.FORMAL,
                List.of(new ChangeItemDTO(ChangeOp.REPLACE, null, "新版内容")), 2L);
    }

    @Test
    void shouldSubmitWithPendingStatus() {
        ChangeRequestVO vo = service.submit(submitDTO());
        assertEquals(ChangeRequestStatus.PENDING, vo.status());
        assertEquals(ChangeRequestType.FORMAL, vo.requestType());
        assertEquals("测试文档", vo.documentTitle());
        assertEquals(1, vo.changes().size());
        verify(changeRequestMapper).insert(any(ChangeRequestEntity.class));
    }

    @Test
    void shouldApproveFromPending() {
        when(changeRequestMapper.selectById(REQUEST_ID)).thenReturn(request(ChangeRequestStatus.PENDING));
        ChangeRequestVO vo = service.approve(REQUEST_ID, new ChangeRequestReviewDTO("同意"));
        assertEquals(ChangeRequestStatus.APPROVED, vo.status());
        assertEquals("同意", vo.reviewComment());
    }

    @Test
    void shouldRejectTransitionFromWrongState() {
        when(changeRequestMapper.selectById(REQUEST_ID)).thenReturn(request(ChangeRequestStatus.MERGED));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.approve(REQUEST_ID, new ChangeRequestReviewDTO("通过")));
        assertEquals(ErrorCode.CONFLICT.getCode(), ex.getCode());
        verify(changeRequestMapper, never()).updateById(any(ChangeRequestEntity.class));
    }

    @Test
    void shouldMergeWhenApproved() {
        when(changeRequestMapper.selectById(REQUEST_ID)).thenReturn(request(ChangeRequestStatus.APPROVED));
        when(documentFeign.mergeDocument(any(MergeRequestDTO.class)))
                .thenReturn(Result.ok(new MergeResultVO(DOCUMENT_ID, "测试文档", 3L)));

        ChangeRequestVO vo = service.merge(REQUEST_ID);

        assertEquals(ChangeRequestStatus.MERGED, vo.status());
    }

    @Test
    void shouldRejectMergeFromPending() {
        when(changeRequestMapper.selectById(REQUEST_ID)).thenReturn(request(ChangeRequestStatus.PENDING));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.merge(REQUEST_ID));
        assertEquals(ErrorCode.CONFLICT.getCode(), ex.getCode());
        verify(documentFeign, never()).mergeDocument(any(MergeRequestDTO.class));
    }

    @Test
    void shouldFailMergeWhenBaselineConflict() {
        when(changeRequestMapper.selectById(REQUEST_ID)).thenReturn(request(ChangeRequestStatus.APPROVED));
        // document 返回业务冲突（契约 Result.fail(CONFLICT)）
        when(documentFeign.mergeDocument(any(MergeRequestDTO.class)))
                .thenReturn(Result.fail(ErrorCode.CONFLICT));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.merge(REQUEST_ID));
        assertEquals(ErrorCode.CONFLICT.getCode(), ex.getCode());
        // 合并失败保持 APPROVED，不更新状态
        verify(changeRequestMapper, never()).updateById(any(ChangeRequestEntity.class));
    }
}
