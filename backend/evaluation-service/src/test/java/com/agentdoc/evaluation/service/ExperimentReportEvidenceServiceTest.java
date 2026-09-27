package com.agentdoc.evaluation.service;

import com.agentdoc.evaluation.mapper.EvaluationEvidenceReferenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationFeedbackMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricEvidenceMapper;
import com.agentdoc.evaluation.pojo.entity.EvaluationEvidenceReferenceEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationFeedbackEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEvidenceEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExperimentReportEvidenceServiceTest {

    @Mock private EvaluationMetricEvidenceMapper metricEvidenceMapper;
    @Mock private EvaluationEvidenceReferenceMapper evidenceMapper;
    @Mock private EvaluationFeedbackMapper feedbackMapper;
    @InjectMocks private ExperimentReportEvidenceService service;

    @Test
    void onlyCurrentTaskFeedbackAndSelectedMetricEvidenceEnterReport() {
        EvaluationMetricEvidenceEntity link = new EvaluationMetricEvidenceEntity();
        link.setMetricId(40L);
        link.setEvidenceReferenceId(50L);
        when(metricEvidenceMapper.selectList(any())).thenReturn(List.of(link));
        EvaluationEvidenceReferenceEntity reference = new EvaluationEvidenceReferenceEntity();
        reference.setId(50L);
        reference.setSpaceId(2L);
        reference.setEvidenceType("AGENT_EXECUTION");
        reference.setBusinessId("60");
        reference.setContentHash("hash");
        when(evidenceMapper.selectBatchIds(any())).thenReturn(List.of(reference));
        when(feedbackMapper.selectList(any())).thenReturn(List.of(feedback(70L, 30L), feedback(71L, 29L)));
        ExperimentReportSnapshotService.Snapshot snapshot = new ExperimentReportSnapshotService.Snapshot(
                List.of(), List.of(new ExperimentReportSnapshotService.CaseSelection(
                "baseline", 10L, 100L, 20L, 31L, 30L, "COMPLETED", null)),
                List.of(10L), List.of(31L), List.of(), List.of(40L));

        ExperimentReportEvidenceService.Evidence selected = service.load(2L, snapshot, List.of(40L));

        assertEquals(List.of(50L), selected.evidenceIds());
        assertEquals(List.of(70L), selected.feedbackIds());
        assertEquals("MANUAL", selected.feedback().getFirst().sourceType());
        assertEquals(40L, selected.metricEvidence().getFirst().metricId());
    }

    private EvaluationFeedbackEntity feedback(Long id, Long taskId) {
        EvaluationFeedbackEntity item = new EvaluationFeedbackEntity();
        item.setId(id);
        item.setSpaceId(2L);
        item.setRunId(10L);
        item.setCaseRunId(20L);
        item.setTaskId(taskId);
        item.setSourceType("MANUAL");
        item.setSourceBusinessId(id.toString());
        item.setLabel("ACCEPTED");
        return item;
    }
}
