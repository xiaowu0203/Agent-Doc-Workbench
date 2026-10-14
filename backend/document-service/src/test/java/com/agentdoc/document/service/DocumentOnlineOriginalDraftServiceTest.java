package com.agentdoc.document.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.utils.OnlineOriginalChangeUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.document.pojo.entity.DocumentEntity;
import com.agentdoc.document.pojo.entity.DocumentVersionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentOnlineOriginalDraftServiceTest {
    private final TaskFeign tasks = mock(TaskFeign.class);
    private final DocumentService documents = mock(DocumentService.class);
    private final DocumentVersionService versions = mock(DocumentVersionService.class);
    private final SpacePermissionService permissions = mock(SpacePermissionService.class);
    private final DocumentOnlineOriginalDraftService service = new DocumentOnlineOriginalDraftService(tasks,documents,versions,permissions);
    private final OnlineOriginalInputVO input = new OnlineOriginalInputVO("61","71","10","20","11","51","a".repeat(64),"41","DRAFT",3L,"b".repeat(64),true);
    private DocumentEntity doc;
    private DocumentVersionEntity row;
    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg","RS256").subject("501").claim("scope","user").build()));
        when(tasks.getOnlineOriginalInput(61L,10L,71L)).thenReturn(Result.ok(input));
        doc = new DocumentEntity(); doc.setId(41L); doc.setSpaceId(10L); doc.setVersion(10L); doc.setContent("后来人工修改的正文");
        when(documents.requireDoc(41L)).thenReturn(doc);
        row = DocumentVersionEntity.create(41L,4L," 原始😀\r\n正文 ",null,"AGENT_DRAFT","AGENT",20L,null,61L,null,StableSnapshotUtils.sha256Utf8(" 原始😀\r\n正文 "));
        row.setId(91L); row.setCreatedAt(LocalDateTime.parse("2026-10-10T20:00:00")); when(versions.agentDraftSnapshots(41L,61L)).thenReturn(List.of(row));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void humanEditsDoNotChangeOriginalDraftVersionHashOrIdentity() {
        var first = service.read(61L,10L,71L); doc.setContent("再一次人工修改"); doc.setVersion(11L);
        assertThat(service.read(61L,10L,71L)).isEqualTo(first); assertThat(first.state()).isEqualTo("AVAILABLE");
        assertThat(first.items().getFirst().contentHash()).isEqualTo(row.getContentSha256());
        assertThat(OnlineOriginalChangeUtils.valid(first.input(),first.items().getFirst())).isTrue();
    }
    @Test void missingFinalSnapshotCannotBackfillFromCurrentBody() {
        when(versions.agentDraftSnapshots(41L,61L)).thenReturn(List.of()); var result = service.read(61L,10L,71L);
        assertThat(result.state()).isEqualTo("EVIDENCE_UNAVAILABLE"); assertThat(result.items()).isEmpty();
    }
    @Test void modifiedContentOrWrongSourceOrMultipleSnapshotsCannotBecomeQualityEvidence() {
        row.setContent("篡改正文"); assertThat(service.read(61L,10L,71L).state()).isEqualTo("ORIGINAL_EVIDENCE_INVALID");
        row.setContentSha256(StableSnapshotUtils.sha256Utf8(row.getContent())); row.setActorId(21L);
        assertThat(service.read(61L,10L,71L).state()).isEqualTo("ORIGINAL_EVIDENCE_INVALID"); row.setActorId(20L);
        when(versions.agentDraftSnapshots(41L,61L)).thenReturn(List.of(row,row));
        assertThat(service.read(61L,10L,71L).state()).isEqualTo("ORIGINAL_EVIDENCE_INVALID");
    }
    @Test void currentTaskPermissionAndMovedDocumentBlockBeforeReadingOriginalVersion() {
        when(tasks.getOnlineOriginalInput(61L,10L,71L)).thenReturn(Result.fail(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.read(61L,10L,71L)).isInstanceOf(BusinessException.class); verifyNoInteractions(versions);
        when(tasks.getOnlineOriginalInput(61L,10L,71L)).thenReturn(Result.ok(input)); doc.setSpaceId(11L);
        assertThatThrownBy(() -> service.read(61L,10L,71L)).isInstanceOf(BusinessException.class); verifyNoInteractions(versions);
    }
    @Test void agentIdentityCannotUseHumanOriginalEvidenceEndpoint() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("agent").header("alg","RS256").subject("20").claim("scope","agent").build()));
        assertThatThrownBy(() -> service.read(61L,10L,71L)).isInstanceOf(BusinessException.class); verifyNoInteractions(tasks,versions);
    }
}
