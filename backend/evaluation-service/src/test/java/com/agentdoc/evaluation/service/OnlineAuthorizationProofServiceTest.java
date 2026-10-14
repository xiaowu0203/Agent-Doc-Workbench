package com.agentdoc.evaluation.service;

import static com.agentdoc.common.constant.OnlineCapabilityConstant.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.mapper.*;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.List;
import java.util.Map;

class OnlineAuthorizationProofServiceTest {
    private final OnlineExperimentMapper experiments = mock(OnlineExperimentMapper.class);
    private final OnlineAssignmentMapper assignments = mock(OnlineAssignmentMapper.class);
    private final OnlineCapabilityVerifier verifier = mock(OnlineCapabilityVerifier.class);
    private final OnlineExecutionAuthority authority = new OnlineExecutionAuthority(experiments, assignments,
            mock(OnlineExecutionSlotMapper.class), mock(OnlineExperimentEventMapper.class));
    private final OnlineExperimentEntity experiment = new OnlineExperimentEntity();
    private OnlineAuthorizationProofService service;

    @BeforeEach
    void setup() {
        var beans = new StaticListableBeanFactory(); beans.addBean("verifier", verifier);
        service = new OnlineAuthorizationProofService(experiments, assignments, authority,
                mock(SpaceAccessService.class), beans.getBeanProvider(OnlineCapabilityVerifier.class));
        experiment.setId(11L); experiment.setSpaceId(22L); experiment.setAgentId(201L);
        experiment.setManifestSchemaVersion(2); experiment.setStatus("ACTIVE"); experiment.setControlAuthorizedBy(501L);
        var payload = Map.of("experimentId", "11", "spaceId", "22", "agentId", "201");
        experiment.setManifestJson(OnlineProtocolUtils.canonical("online.manifest", payload));
        experiment.setManifestHash(OnlineProtocolUtils.hash("online.manifest", payload));
        when(experiments.selectById(11L)).thenReturn(experiment);
        when(verifier.verify(anyString(), any(), eq("11"), anyList())).thenReturn(proof("22", "501", experiment.getManifestHash()));
    }

    @Test
    void onlyCanonicalMatchingExistingBindingsAreReturnedInNumericOrder() {
        var first = assignment("2"); var second = assignment("10");
        when(assignments.selectList(any(Wrapper.class))).thenReturn(List.of(second, first));
        assertThat(service.bindings("11", "control", List.of("10", "2"))).extracting(OnlineTaskBindingDTO::taskId)
                .containsExactly("2", "10");
        assertThat(service.control("11", "control").authorizedBy()).isEqualTo("501");
        first.setInputSnapshotHash("0".repeat(64));
        assertThatThrownBy(() -> service.bindings("11", "control", List.of("2", "10"))).isInstanceOf(BusinessException.class);
    }

    @Test
    void missingForeignOrDuplicateSetIsNeverSilentlyNarrowed() {
        when(assignments.selectList(any(Wrapper.class))).thenReturn(List.of(assignment("2")));
        assertThatThrownBy(() -> service.bindings("11", "control", List.of("2", "10"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.bindings("11", "control", List.of("2", "2"))).isInstanceOf(IllegalArgumentException.class);
        var foreign = assignment("2"); foreign.setExperimentId(12L);
        when(assignments.selectList(any(Wrapper.class))).thenReturn(List.of(foreign));
        assertThatThrownBy(() -> service.bindings("11", "control", List.of("2"))).isInstanceOf(BusinessException.class);
    }

    @Test
    void wrongOwnerSpaceOrManifestCannotObtainAuthorityProof() {
        for (Jwt jwt : List.of(proof("33", "501", experiment.getManifestHash()), proof("22", "502", experiment.getManifestHash()),
                proof("22", "501", "b".repeat(64)))) {
            when(verifier.verify(anyString(), any(), eq("11"), anyList())).thenReturn(jwt);
            assertThatThrownBy(() -> service.control("11", "control")).isInstanceOf(BusinessException.class);
        }
    }

    private Jwt proof(String space, String owner, String manifest) {
        return Jwt.withTokenValue("proof").header("alg", "RS256").claim("spaceId", space)
                .claim(AUTHORIZED_BY, owner).claim(MANIFEST_HASH, manifest).build();
    }
    private OnlineAssignmentEntity assignment(String taskId) {
        var binding = new OnlineTaskBindingDTO("7" + taskId, "11", experiment.getManifestHash(), "1", "BASELINE", 9999,
                "601", taskId, "22", "201", "301", "1", "b".repeat(64), 1, "c".repeat(64), "401", 2,
                "d".repeat(64), "e".repeat(64), "f".repeat(64), "500", 60, "LIVE", "ORIGINAL", 2);
        var row = new OnlineAssignmentEntity(); row.setId(OnlineProtocolUtils.id(binding.assignmentId()));
        row.setExperimentId(11L); row.setSpaceId(22L); row.setAgentId(201L); row.setTaskId(OnlineProtocolUtils.id(taskId));
        row.setDocumentId(301L); row.setCreatedBy(601L); row.setAcceptedSequence(1L); row.setVariant("BASELINE"); row.setBucket(9999);
        row.setInputSnapshotSchemaVersion(1); row.setInputSnapshotHash(binding.inputHash()); row.setTemplateId(401L);
        row.setConfigSchemaVersion(2); row.setConfigHash(binding.templateHash()); row.setDependencyHash(binding.dependencyHash());
        row.setReservedTokenBudget(500L); row.setBindingSchemaVersion(2);
        row.setBindingJson(OnlineProtocolUtils.canonical("online.binding", binding)); row.setBindingHash(OnlineProtocolUtils.hash("online.binding", binding));
        return row;
    }
}
