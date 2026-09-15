package com.agentdoc.task.mcp;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.vo.DocumentFragmentVO;
import com.agentdoc.common.feign.vo.DocumentVersionExecutionContextVO;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.service.AuditLogService;
import com.agentdoc.task.service.ChangeRequestService;
import com.agentdoc.task.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkbenchMcpApplicationServiceTest {

    private static final long TASK_ID = 11L;
    private static final long DOCUMENT_ID = 22L;
    private static final long VERSION = 7L;
    private static final String SHA256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Mock
    private McpTaskScopeService scopeService;
    @Mock
    private TaskService taskService;
    @Mock
    private ChangeRequestService changeRequestService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private DocumentFeign documentFeign;

    private WorkbenchMcpApplicationService service;

    @BeforeEach
    void setUp() {
        service = new WorkbenchMcpApplicationService(scopeService, taskService, changeRequestService,
                auditLogService, documentFeign);
    }

    @Test
    void readsOnlyTheVersionFrozenByTask() {
        TaskEntity task = frozenTask();
        DocumentFragmentVO expected = new DocumentFragmentVO(DOCUMENT_ID, "fragment", 0L, 8, 20L);
        when(scopeService.require(JwtConstant.ACTION_READ_FRAGMENT))
                .thenReturn(new McpTaskScope(TASK_ID, 33L, 44L, DOCUMENT_ID));
        when(taskService.require(TASK_ID)).thenReturn(task);
        when(documentFeign.readVersionFragment(DOCUMENT_ID, VERSION, SHA256, 0L, 8))
                .thenReturn(Result.ok(expected));

        DocumentFragmentVO result = service.readDocumentFragment(0L, 8);

        assertEquals(expected, result);
        verify(taskService).requireReadableRange(TASK_ID, 0L, 8);
        verify(documentFeign).readVersionFragment(DOCUMENT_ID, VERSION, SHA256, 0L, 8);
        verify(documentFeign, never()).readFragment(DOCUMENT_ID, 0L, 8);
    }

    @Test
    void loadsTaskContextFromFrozenVersionIdentity() {
        TaskEntity task = frozenTask();
        DocumentVersionExecutionContextVO document = new DocumentVersionExecutionContextVO(
                DOCUMENT_ID, VERSION, SHA256, 20L);
        when(scopeService.require(JwtConstant.ACTION_READ_FRAGMENT))
                .thenReturn(new McpTaskScope(TASK_ID, 33L, 44L, DOCUMENT_ID));
        when(taskService.require(TASK_ID)).thenReturn(task);
        when(documentFeign.getVersionExecutionContext(DOCUMENT_ID, VERSION, SHA256))
                .thenReturn(Result.ok(document));

        service.getTaskContext();

        verify(taskService).getTaskDocumentContext(TASK_ID, document);
        verify(documentFeign, never()).getExecutionContext(DOCUMENT_ID);
    }

    private TaskEntity frozenTask() {
        TaskEntity task = new TaskEntity();
        task.setId(TASK_ID);
        task.setDocumentId(DOCUMENT_ID);
        task.setDocumentVersionSnapshot(VERSION);
        task.setDocumentContentSha256(SHA256);
        return task;
    }
}
