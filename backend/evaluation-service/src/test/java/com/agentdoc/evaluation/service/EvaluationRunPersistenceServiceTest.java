package com.agentdoc.evaluation.service;

import com.agentdoc.common.feign.vo.ReplayBatchCreateVO;
import com.agentdoc.common.feign.vo.ReplayBatchItemVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.agentdoc.evaluation.enums.EvaluationAttemptStatus;
import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
import com.agentdoc.evaluation.mapper.EvaluationCaseRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationRunMapper;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationRunEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvaluationRunPersistenceServiceTest {

    @Mock private EvaluationRunMapper runMapper;
    @Mock private EvaluationCaseRunMapper caseRunMapper;
    @Mock private EvaluationCaseAttemptMapper attemptMapper;
    @Mock private WorkerCapabilitySegmentService segmentService;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, EvaluationRunEntity.class);
    }

    @Test
    void createsAllCaseRunsAndAttemptsInBatches() {
        var service = new EvaluationRunPersistenceService(runMapper, caseRunMapper, attemptMapper, segmentService);

        var draft = service.create(9L, 10L, null, 501L, List.of(31L, 32L));

        ArgumentCaptor<List<EvaluationCaseRunEntity>> caseRunCaptor =
                ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<EvaluationCaseAttemptEntity>> attemptCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(caseRunMapper).insertBatch(caseRunCaptor.capture());
        verify(attemptMapper).insertBatch(attemptCaptor.capture());
        assertThat(caseRunCaptor.getValue()).hasSize(2);
        assertThat(attemptCaptor.getValue()).hasSize(2);
        for (int index = 0; index < draft.cases().size(); index++) {
            var item = draft.cases().get(index);
            assertThat(item.caseRun().getTestCaseVersionId()).isEqualTo(31L + index);
            assertThat(item.caseRun().getCurrentAttemptId()).isEqualTo(item.attempt().getId());
            assertThat(item.attempt().getAttemptNo()).isEqualTo(1);
            assertThat(item.attempt().getStatus()).isEqualTo(EvaluationAttemptStatus.CREATED.name());
        }
    }

    @Test
    void attachesDispatchByBatchUpdates() {
        var service = new EvaluationRunPersistenceService(runMapper, caseRunMapper, attemptMapper, segmentService);
        var draft = service.create(9L, 10L, null, 501L, List.of(31L, 32L));
        draft.run().setId(71L);
        var segment = new com.agentdoc.evaluation.pojo.entity.EvaluationWorkerCapabilitySegmentEntity();
        segment.setId(82L);
        Instant expiresAt = Instant.now().plusSeconds(600);
        when(segmentService.append(71L, 9L, 1, "a".repeat(64), "worker-token", expiresAt)).thenReturn(segment);
        List<ReplayBatchItemVO> items = draft.cases().stream()
                .map(item -> new ReplayBatchItemVO(item.attempt().getId() + 100,
                        EvaluationRunPersistenceService.requestKey(item.attempt().getId()),
                        item.attempt().getId() + 200, "PENDING"))
                .toList();

        var attached = service.attachDispatch(draft, new ReplayBatchCreateVO(71L, 9L, items,
                "a".repeat(64), "worker-token", expiresAt));

        ArgumentCaptor<List<EvaluationCaseRunEntity>> caseRunCaptor =
                ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<EvaluationCaseAttemptEntity>> attemptCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(caseRunMapper).updateBatch(caseRunCaptor.capture());
        verify(attemptMapper).updateBatch(attemptCaptor.capture());
        assertThat(caseRunCaptor.getValue()).hasSize(2);
        assertThat(attemptCaptor.getValue()).hasSize(2);
        for (int index = 0; index < attached.cases().size(); index++) {
            var item = attached.cases().get(index);
            assertThat(item.caseRun().getStatus()).isEqualTo(EvaluationAttemptStatus.REPLAY_CREATED.name());
            assertThat(item.attempt().getCapabilitySegmentId()).isEqualTo(82L);
            assertThat(item.attempt().getReplayTaskId()).isEqualTo(items.get(index).replayTaskId());
        }
    }
}
