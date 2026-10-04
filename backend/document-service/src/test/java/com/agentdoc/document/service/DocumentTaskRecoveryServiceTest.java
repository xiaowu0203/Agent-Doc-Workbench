package com.agentdoc.document.service;

import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.document.mapper.DocumentMapper;
import com.agentdoc.document.pojo.entity.DocumentEntity;
import com.agentdoc.document.pojo.entity.DocumentVersionEntity;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentTaskRecoveryServiceTest {
    private final DocumentMapper mapper = mock(DocumentMapper.class);
    private final DocumentVersionService versions = mock(DocumentVersionService.class);
    private final DocumentService service = new DocumentService(mapper, mock(DocumentDirectoryService.class), versions,
            mock(SpacePermissionService.class), mock(AuthFeign.class));
    private DocumentEntity document;

    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "recovery"), DocumentEntity.class);
    }

    @BeforeEach
    void setup() {
        document = new DocumentEntity();
        document.setId(4L);
        document.setSpaceId(3L);
        document.setDocType(DocType.DRAFT.getCode());
        document.setVersion(1L);
        document.setContent("baseline");
        document.setAgentStagedTaskId(1L);
        document.setAgentStagedBaseVersion(1L);
        document.setAgentStagedRevision(2L);
        document.setAgentStagedContent("candidate");
        when(mapper.selectById(4L)).thenReturn(document);
    }

    @Test
    void completesOnlyExistingStagingUsingOriginalBaselineAndCreatesSourceTaskSnapshot() {
        when(mapper.update(any(), any())).thenReturn(1);
        recover(true);
        assertThat(document.getContent()).isEqualTo("candidate");
        assertThat(document.getVersion()).isEqualTo(2L);
        verify(versions).createAgentDraftSnapshot(4L, 2L, "candidate", "Agent 更新草稿", 2L, 1L);
    }

    @Test
    void discardNeverChangesMainContentOrCreatesAnyVersion() {
        when(mapper.update(any(), any())).thenReturn(1);
        recover(false);
        assertThat(document.getContent()).isEqualTo("baseline");
        assertThat(document.getVersion()).isEqualTo(1L);
        verify(versions, never()).createAgentDraftSnapshot(any(), any(), any(), any(), any(), any());
    }

    @Test
    void repeatsAfterSuccessfulSnapshotOrNoStagingAreNoOpsEvenWhenAnotherTaskHasNewStaging() {
        when(versions.findAgentDraftSnapshot(4L, 1L)).thenReturn(new DocumentVersionEntity());
        document.setAgentStagedTaskId(99L);
        recover(true);
        verify(mapper, never()).update(any(), any());
        when(versions.findAgentDraftSnapshot(4L, 1L)).thenReturn(null);
        document.setAgentStagedTaskId(null);
        recover(false);
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void humanEditAndOtherTaskStagingStayUntouched() {
        document.setVersion(2L);
        assertThatThrownBy(() -> recover(true)).isInstanceOf(BusinessException.class).hasMessage("DRAFT_VERSION_CONFLICT");
        document.setVersion(1L);
        document.setAgentStagedTaskId(99L);
        assertThatThrownBy(() -> recover(false)).hasMessage("DRAFT_VERSION_CONFLICT");
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void rejectsOtherSpaceFormalDocumentAndChangedBaselineHash() {
        document.setSpaceId(99L);
        assertThatThrownBy(() -> recover(true)).hasMessage("RECOVERY_IDENTITY_MISMATCH");
        document.setSpaceId(3L);
        document.setDocType(DocType.FORMAL.getCode());
        assertThatThrownBy(() -> recover(true)).isInstanceOf(BusinessException.class);
        document.setDocType(DocType.DRAFT.getCode());
        document.setContent("changed without version");
        assertThatThrownBy(() -> recover(true)).hasMessage("RECOVERY_IDENTITY_MISMATCH");
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void conditionalUpdateLosingTheRaceFailsBeforeVersionCreation() {
        when(mapper.update(any(), any())).thenReturn(0);
        assertThatThrownBy(() -> recover(true)).hasMessage("DRAFT_VERSION_CONFLICT");
        assertThatThrownBy(() -> recover(false)).hasMessage("DRAFT_VERSION_CONFLICT");
        verify(versions, never()).createAgentDraftSnapshot(any(), any(), any(), any(), any(), any());
    }

    private void recover(boolean completed) {
        service.finalizeRecoveredTaskDraft(4L, 3L, 1L, 2L, 1L, StableSnapshotUtils.sha256Utf8("baseline"), completed);
    }
}
