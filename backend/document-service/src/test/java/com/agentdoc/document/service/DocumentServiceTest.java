package com.agentdoc.document.service;

import com.agentdoc.common.enums.ChangeOp;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.ChangeItemDTO;
import com.agentdoc.common.feign.dto.MergeRequestDTO;
import com.agentdoc.common.feign.vo.MergeResultVO;
import com.agentdoc.document.enums.DocStatus;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.enums.SpaceRole;
import com.agentdoc.document.mapper.DocumentMapper;
import com.agentdoc.document.pojo.dto.DocumentUpdateDTO;
import com.agentdoc.document.pojo.entity.DocumentEntity;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocumentService} 单元测试：编辑触发版本快照、审批合并防并发覆盖。
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final long USER_ID = 1001L;
    private static final long DOCUMENT_ID = 3001L;

    @Mock
    private DocumentMapper documentMapper;
    @Mock
    private DocumentVersionService versionService;
    @Mock
    private SpacePermissionService permissionService;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentMapper, versionService, permissionService);
        // 模拟已登录：SecurityContext 放入以 Jwt 为 principal 的认证信息
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(String.valueOf(USER_ID))
                .claim("username", "tester")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 构造版本为 2 的正式文档 */
    private DocumentEntity doc(String content) {
        DocumentEntity entity = new DocumentEntity();
        entity.setId(DOCUMENT_ID);
        entity.setSpaceId(2001L);
        entity.setParentId(0L);
        entity.setTitle("测试文档");
        entity.setContent(content);
        entity.setDocType(DocType.FORMAL.getCode());
        entity.setVersion(2L);
        entity.setStatus(DocStatus.NORMAL.getCode());
        entity.setCreatedBy(USER_ID);
        entity.setUpdatedBy(USER_ID);
        return entity;
    }

    @Test
    void shouldCreateVersionWhenContentChanged() {
        when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(doc("旧内容"));
        when(permissionService.requireRole(anyLong(), any(SpaceRole.class))).thenReturn(SpaceRole.EDITOR);
        when(permissionService.requireUserId()).thenReturn(USER_ID);

        documentService.update(DOCUMENT_ID, new DocumentUpdateDTO(null, "新内容"));

        // 主更新 + bumpVersion 各 update 一次，版本递增至 3 并生成快照
        verify(documentMapper, times(2)).updateById(any(DocumentEntity.class));
        verify(versionService).createSnapshot(eq(DOCUMENT_ID), eq(3L), eq("新内容"), any(String.class), eq(USER_ID));
    }

    @Test
    void shouldNotCreateVersionWhenOnlyTitleChanged() {
        when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(doc("旧内容"));
        when(permissionService.requireRole(anyLong(), any(SpaceRole.class))).thenReturn(SpaceRole.EDITOR);
        when(permissionService.requireUserId()).thenReturn(USER_ID);

        documentService.update(DOCUMENT_ID, new DocumentUpdateDTO("新标题", null));

        verify(versionService, never()).createSnapshot(anyLong(), anyLong(), any(String.class),
                any(String.class), anyLong());
    }

    @Test
    void shouldRejectMergeWhenBaseVersionMismatch() {
        when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(doc("旧内容"));
        when(permissionService.requireRole(anyLong(), any(SpaceRole.class))).thenReturn(SpaceRole.EDITOR);
        when(permissionService.requireUserId()).thenReturn(USER_ID);
        // 基线版本 1 ≠ 当前版本 2
        MergeRequestDTO request = new MergeRequestDTO(DOCUMENT_ID, 1L,
                List.of(new ChangeItemDTO(ChangeOp.REPLACE, null, "新版内容")), "审批合并");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentService.mergeForFeign(request));
        assertEquals(ErrorCode.CONFLICT.getCode(), ex.getCode());
        verify(documentMapper, never()).updateById(any(DocumentEntity.class));
    }

    @Test
    void shouldMergeAndCreateVersionWhenBaseVersionMatches() {
        when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(doc("旧内容"));
        when(permissionService.requireRole(anyLong(), any(SpaceRole.class))).thenReturn(SpaceRole.EDITOR);
        when(permissionService.requireUserId()).thenReturn(USER_ID);
        MergeRequestDTO request = new MergeRequestDTO(DOCUMENT_ID, 2L,
                List.of(new ChangeItemDTO(ChangeOp.REPLACE, null, "合并后的内容")), "审批合并变更");

        MergeResultVO result = documentService.mergeForFeign(request);

        assertEquals(DOCUMENT_ID, result.documentId());
        assertEquals(3L, result.newVersion());
        verify(versionService).createSnapshot(eq(DOCUMENT_ID), eq(3L), eq("合并后的内容"),
                eq("审批合并变更"), eq(USER_ID));
    }

    @Test
    void shouldAppendWhenAppendOp() {
        when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(doc("旧内容"));
        when(permissionService.requireRole(anyLong(), any(SpaceRole.class))).thenReturn(SpaceRole.EDITOR);
        when(permissionService.requireUserId()).thenReturn(USER_ID);
        MergeRequestDTO request = new MergeRequestDTO(DOCUMENT_ID, 2L,
                List.of(new ChangeItemDTO(ChangeOp.APPEND, null, "\n追加段落")), "追加");

        documentService.mergeForFeign(request);

        verify(versionService).createSnapshot(eq(DOCUMENT_ID), eq(3L), eq("旧内容\n追加段落"),
                eq("追加"), eq(USER_ID));
    }
}
