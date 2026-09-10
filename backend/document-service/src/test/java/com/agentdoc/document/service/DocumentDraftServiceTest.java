package com.agentdoc.document.service;

import com.agentdoc.common.utils.RedisUtils;
import com.agentdoc.document.pojo.dto.DocumentDraftSaveDTO;
import com.agentdoc.document.pojo.entity.DocumentEntity;
import com.agentdoc.document.pojo.vo.DocumentDraftVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocumentDraftService} 单元测试：用户隔离键、一天 TTL 以及草稿读写删除。
 */
@ExtendWith(MockitoExtension.class)
class DocumentDraftServiceTest {

    private static final long USER_ID = 1001L;
    private static final long SPACE_ID = 2001L;
    private static final long DOCUMENT_ID = 3001L;
    private static final String DRAFT_KEY =
            "agent-doc-workbench:document:draft:1001:2001:3001";

    @Mock
    private DocumentService documentService;
    @Mock
    private SpacePermissionService permissionService;
    @Mock
    private RedisUtils redisUtils;

    @Test
    void shouldSaveDraftWithUserIsolatedKeyAndOneDayTtl() {
        DocumentEntity document = document();
        when(documentService.requireDoc(DOCUMENT_ID)).thenReturn(document);
        when(permissionService.requireUserId()).thenReturn(USER_ID);

        DocumentDraftService service = new DocumentDraftService(documentService, permissionService, redisUtils);
        DocumentDraftSaveDTO request = new DocumentDraftSaveDTO(3L, "临时标题", "# 临时内容");

        service.save(DOCUMENT_ID, request);

        verify(redisUtils).set(eq(DRAFT_KEY), isA(DocumentDraftVO.class), eq(Duration.ofDays(1)));
    }

    @Test
    void shouldReadAndDeleteCurrentUsersDraft() {
        DocumentEntity document = document();
        DocumentDraftVO draft = new DocumentDraftVO(DOCUMENT_ID, 3L, "临时标题", "# 临时内容");
        when(documentService.requireDoc(DOCUMENT_ID)).thenReturn(document);
        when(permissionService.requireUserId()).thenReturn(USER_ID);
        when(redisUtils.get(DRAFT_KEY)).thenReturn(draft);

        DocumentDraftService service = new DocumentDraftService(documentService, permissionService, redisUtils);

        assertSame(draft, service.get(DOCUMENT_ID));
        service.delete(DOCUMENT_ID);

        verify(redisUtils).delete(DRAFT_KEY);
    }

    private DocumentEntity document() {
        DocumentEntity document = new DocumentEntity();
        document.setId(DOCUMENT_ID);
        document.setSpaceId(SPACE_ID);
        document.setVersion(3L);
        return document;
    }
}
