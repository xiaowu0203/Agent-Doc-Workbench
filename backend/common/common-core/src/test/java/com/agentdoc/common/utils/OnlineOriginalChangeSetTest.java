package com.agentdoc.common.utils;

import com.agentdoc.common.feign.vo.OnlineOriginalChangesVO;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.feign.vo.OnlineOriginalChangeItemVO;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class OnlineOriginalChangeSetTest {
    private OnlineOriginalChangesVO fixture() throws Exception {
        Path base = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (base != null && !Files.exists(base.resolve("docs/fixtures/online-original-change-set-v2.json"))) { base = base.getParent(); }
        assertThat(base).isNotNull();
        return JsonUtils.parseStrict(Files.readString(base.resolve("docs/fixtures/online-original-change-set-v2.json")), OnlineOriginalChangesVO.class);
    }
    @Test void publishedVectorUsesNumericSortingAndKeepsFailedOriginal() throws Exception {
        var source = fixture();
        assertThat(source.items()).allMatch(item -> OnlineOriginalChangeUtils.valid(source.input(),item));
        assertThat(OnlineOriginalChangeUtils.setIdentityHash(source)).isEqualTo("593f1dff0db614e8e9204ded9bd3195114673b272b5a38ed9ddc7481aefdf1db");
        var reordered = new OnlineOriginalChangesVO(2,"AVAILABLE",source.input(),List.of(source.items().getLast(),source.items().getFirst()));
        assertThat(OnlineOriginalChangeUtils.setIdentityHash(reordered)).isEqualTo(OnlineOriginalChangeUtils.setIdentityHash(source));
        assertThat(OnlineOriginalChangeUtils.setIdentityHash(new OnlineOriginalChangesVO(2,"AVAILABLE",source.input(),List.of(source.items().getLast()))))
                .isNotEqualTo(OnlineOriginalChangeUtils.setIdentityHash(source));
    }
    @Test void payloadOrMemberChangeInvalidatesThePriorCollectionIdentity() throws Exception {
        var source = fixture(); var before = source.items().getFirst();
        var changed = OnlineOriginalChangeUtils.signed(source.input(),before.evidenceId(),before.outputType(),before.baseVersion(),null,
                "f".repeat(64),null,false,before.capturedAt());
        assertThat(OnlineOriginalChangeUtils.setIdentityHash(new OnlineOriginalChangesVO(2,"AVAILABLE",source.input(),List.of(changed,source.items().getLast()))))
                .isNotEqualTo(OnlineOriginalChangeUtils.setIdentityHash(source));
    }
    @Test void duplicateMissingUnknownSchemaAndUnfinishedCollectionsAreRejected() throws Exception {
        var source = fixture();
        for (var invalid : List.of(new OnlineOriginalChangesVO(2,"AVAILABLE",source.input(),List.of(source.items().getFirst(),source.items().getFirst())),
                new OnlineOriginalChangesVO(1,"AVAILABLE",source.input(),source.items()),
                new OnlineOriginalChangesVO(2,"EVIDENCE_UNAVAILABLE",source.input(),source.items()),
                new OnlineOriginalChangesVO(2,"AVAILABLE",source.input(),List.of()))) {
            assertThatThrownBy(() -> OnlineOriginalChangeUtils.setIdentityHash(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
        var input = source.input(); var unfinished = new OnlineOriginalInputVO(input.taskId(),input.executionId(),input.spaceId(),input.agentId(),input.experimentId(),input.assignmentId(),
                input.bindingHash(),input.documentId(),input.documentType(),input.baseVersion(),input.inputHash(),false);
        assertThatThrownBy(() -> OnlineOriginalChangeUtils.setIdentityHash(new OnlineOriginalChangesVO(2,"AVAILABLE",unfinished,source.items()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OnlineOriginalChangeUtils.setIdentityHash(null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void wrongBaselineFailedProposalIsIncludedButClaimedValidVersionDriftIsRejected() throws Exception {
        var source = fixture(); var item = source.items().getFirst();
        var failed = OnlineOriginalChangeUtils.signed(source.input(),item.evidenceId(),item.outputType(),4L,null,item.payloadHash(),null,false,item.capturedAt());
        assertThat(OnlineOriginalChangeUtils.setIdentityHash(new OnlineOriginalChangesVO(2,"AVAILABLE",source.input(),List.of(failed)))).hasSize(64);
        var valid = OnlineOriginalChangeUtils.signed(source.input(),item.evidenceId(),item.outputType(),4L,null,item.payloadHash(),"e".repeat(64),true,item.capturedAt());
        assertThatThrownBy(() -> OnlineOriginalChangeUtils.setIdentityHash(new OnlineOriginalChangesVO(2,"AVAILABLE",source.input(),List.of(valid)))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void forgedMemberSignatureCannotBecomeACollection() throws Exception {
        var source = fixture(); var original = source.items().getFirst();
        var forged = new OnlineOriginalChangeItemVO(original.evidenceId(),original.outputType(),original.baseVersion(),original.outputVersion(),original.payloadHash(),original.contentHash(),original.structureValid(),original.capturedAt(),"0".repeat(64));
        assertThatThrownBy(() -> OnlineOriginalChangeUtils.setIdentityHash(new OnlineOriginalChangesVO(2,"AVAILABLE",source.input(),List.of(forged)))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void draftCollectionRequiresExactlyOneProvenSuccessorAndMatchingPayloadHash() {
        var input = new OnlineOriginalInputVO("61","71","10","20","11","51","a".repeat(64),"41","DRAFT",3L,"b".repeat(64),true);
        var item = OnlineOriginalChangeUtils.signed(input,"101","DRAFT_DOCUMENT",3L,4L,"c".repeat(64),"c".repeat(64),true,"2026-10-12T12:00");
        assertThat(OnlineOriginalChangeUtils.setIdentityHash(new OnlineOriginalChangesVO(2,"AVAILABLE",input,List.of(item)))).hasSize(64);
        var wrong = OnlineOriginalChangeUtils.signed(input,"101","DRAFT_DOCUMENT",3L,5L,"c".repeat(64),"c".repeat(64),true,"2026-10-12T12:00");
        assertThatThrownBy(() -> OnlineOriginalChangeUtils.setIdentityHash(new OnlineOriginalChangesVO(2,"AVAILABLE",input,List.of(wrong)))).isInstanceOf(IllegalArgumentException.class);
        var mismatch = OnlineOriginalChangeUtils.signed(input,"101","DRAFT_DOCUMENT",3L,4L,"c".repeat(64),"d".repeat(64),true,"2026-10-12T12:00");
        assertThatThrownBy(() -> OnlineOriginalChangeUtils.setIdentityHash(new OnlineOriginalChangesVO(2,"AVAILABLE",input,List.of(mismatch)))).isInstanceOf(IllegalArgumentException.class);
    }
}
