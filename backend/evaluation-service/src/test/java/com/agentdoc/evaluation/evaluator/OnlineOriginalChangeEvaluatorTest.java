package com.agentdoc.evaluation.evaluator;

import com.agentdoc.common.feign.vo.OnlineOriginalChangesVO;
import com.agentdoc.common.feign.vo.OnlineOriginalChangeItemVO;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static com.agentdoc.common.utils.OnlineOriginalChangeUtils.*;

class OnlineOriginalChangeEvaluatorTest {
    private final OnlineOriginalChangeEvaluator evaluator = new OnlineOriginalChangeEvaluator();
    private final OnlineOriginalInputVO input = new OnlineOriginalInputVO("61","71","10","20","11","51","a".repeat(64),"41","FORMAL",3L,"b".repeat(64),true);
    private String expected(String type, String hash) {
        return JsonUtils.toJson(hash == null ? Map.of("requiredOutputType",type) : Map.of("requiredOutputType",type,"targetContentSha256",hash));
    }
    private OnlineOriginalChangeItemVO item(String id, boolean valid, String hash) {
        return signed(input,id,PROPOSAL,3L,null,"c".repeat(64),hash,valid,"2026-10-10T20:00:00.123");
    }
    private OnlineOriginalChangesVO original(OnlineOriginalChangeItemVO... items) { return new OnlineOriginalChangesVO(2,AVAILABLE,input,List.of(items)); }
    @Test void allOriginalProposalsMustPassAndEveryEvidenceIsReferenced() {
        var output = evaluator.evaluate("{}",expected(PROPOSAL,"d".repeat(64)),original(item("91",true,"d".repeat(64)),item("92",true,"e".repeat(64))));
        assertThat(output.status().name()).isEqualTo("FAILED"); assertThat(output.metrics().getFirst().value().booleanValue()).isTrue();
        assertThat(output.metrics().getLast().value().numericValue()).isEqualByComparingTo("0.5"); assertThat(output.evidence()).hasSize(2);
        assertThat(output.metrics().getFirst().evidenceReferenceKeys()).hasSize(2);
    }
    @Test void validContentFailureRetainsRealZeroAndNoExpectedBodyInResult() {
        var output = evaluator.evaluate("{}",expected(PROPOSAL,"d".repeat(64)),original(item("91",true,"e".repeat(64))));
        assertThat(output.status().name()).isEqualTo("FAILED"); assertThat(output.metrics().getLast().value().numericValue()).isZero();
        assertThat(output.detailsJson()).doesNotContain("d".repeat(64));
    }
    @Test void completeBadStructureIsFalseButUncomputableContentDoesNotBecomeZero() {
        var output = evaluator.evaluate("{}",expected(PROPOSAL,"d".repeat(64)),original(item("91",false,null)));
        assertThat(output.status().name()).isEqualTo("FAILED"); assertThat(output.metrics()).hasSize(1);
        assertThat(output.metrics().getFirst().value().booleanValue()).isFalse(); assertThat(output.evidence()).hasSize(1);
    }
    @Test void absentAndUnavailableEvidenceHaveNoSyntheticZero() {
        for (var original : new OnlineOriginalChangesVO[]{null,new OnlineOriginalChangesVO(2,UNAVAILABLE,input,List.of())}) {
            var output = evaluator.evaluate("{}",expected(PROPOSAL,null),original);
            assertThat(output.status().name()).isEqualTo("SKIPPED"); assertThat(output.metrics()).isEmpty();
        }
    }
    @Test void notApplicableIsMissingRatherThanQualityFailure() {
        var output = evaluator.evaluate("{}",expected(DRAFT,null),original(item("91",true,"d".repeat(64))));
        assertThat(output.status().name()).isEqualTo("SKIPPED"); assertThat(output.summaryCode()).isEqualTo("ORIGINAL_CHANGE_NOT_APPLICABLE");
        assertThat(output.metrics()).isEmpty();
    }
    @Test void tamperedIdentityAndDuplicateCollectionAreErrorsWithoutMetrics() {
        var item = item("91",true,"d".repeat(64));
        var tampered = new OnlineOriginalChangeItemVO(item.evidenceId(),item.outputType(),4L,item.outputVersion(),item.payloadHash(),item.contentHash(),true,item.capturedAt(),item.identityHash());
        var badHash = signed(input,"92",PROPOSAL,3L,null,"c".repeat(64),"非法摘要",false,"2026-10-10T20:00:00");
        for (var original : List.of(original(tampered),original(item,item),original(badHash),new OnlineOriginalChangesVO(2,INVALID,input,List.of()))) {
            var output = evaluator.evaluate("{}",expected(PROPOSAL,null),original);
            assertThat(output.status().name()).isEqualTo("ERROR"); assertThat(output.metrics()).isEmpty();
        }
    }
    @Test void finalizedDraftUsesOnlyItsOriginalVersionAndHash() {
        var draftInput = new OnlineOriginalInputVO("61","71","10","20","11","51","a".repeat(64),"41","DRAFT",3L,"b".repeat(64),true);
        var draft = signed(draftInput,"91",DRAFT,3L,4L,"d".repeat(64),"d".repeat(64),true,"2026-10-10T20:00:00");
        var output = evaluator.evaluate("{}",expected(DRAFT,"d".repeat(64)),new OnlineOriginalChangesVO(2,AVAILABLE,draftInput,List.of(draft)));
        assertThat(output.status().name()).isEqualTo("PASSED"); assertThat(output.metrics().getFirst().value().booleanValue()).isTrue();
    }
    @Test void invalidFrozenConfigIsNotQualityFailure() {
        assertThatThrownBy(() -> evaluator.evaluate("{\"extra\":true}",expected(PROPOSAL,null),original(item("91",true,"d".repeat(64)))))
                .isInstanceOf(BusinessException.class);
    }
}
