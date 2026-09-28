package com.agentdoc.evaluation.service;

import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.evaluation.mapper.ExperimentMapper;
import com.agentdoc.evaluation.mapper.ExperimentReportMapper;
import com.agentdoc.evaluation.mapper.ExperimentReportRequestMapper;
import com.agentdoc.evaluation.pojo.entity.ExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentReportEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentReportRequestEntity;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExperimentReportPersistenceServiceTest {

    @Mock private ExperimentMapper experimentMapper;
    @Mock private ExperimentReportMapper reportMapper;
    @Mock private ExperimentReportRequestMapper requestMapper;
    @InjectMocks private ExperimentReportPersistenceService service;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, ExperimentEntity.class);
    }

    @Test
    void automaticFirstReportGetsRevisionOne() {
        when(experimentMapper.selectOne(any())).thenReturn(experiment());

        ExperimentReportEntity saved = service.save(1L, prepared("input-a"), null, null, 9L);

        assertEquals(1, saved.getRevision());
        assertEquals("input-a", saved.getCalculationInputHash());
        verify(reportMapper).insert(saved);
        verify(requestMapper, never()).insert(any(ExperimentReportRequestEntity.class));
    }

    @Test
    void differentRequestKeysReuseSameInputRevision() {
        when(experimentMapper.selectOne(any())).thenReturn(experiment());
        ExperimentReportEntity prior = new ExperimentReportEntity();
        prior.setId(80L);
        prior.setRevision(1);
        when(reportMapper.selectOne(any())).thenReturn(prior, prior);

        ExperimentReportEntity saved = service.save(1L, prepared("input-a"), "request-b", "hash-b", 9L);

        assertEquals(80L, saved.getId());
        verify(reportMapper, never()).insert(any(ExperimentReportEntity.class));
        verify(requestMapper).insert(any(ExperimentReportRequestEntity.class));
    }

    @Test
    void changedSelectedInputCreatesNextRevisionWithoutUpdatingOldOne() {
        when(experimentMapper.selectOne(any())).thenReturn(experiment());
        ExperimentReportEntity prior = new ExperimentReportEntity();
        prior.setId(80L);
        prior.setRevision(1);
        when(reportMapper.selectOne(any())).thenReturn(prior, (ExperimentReportEntity) null);

        service.save(1L, prepared("input-b"), "request-b", "hash-b", 9L);

        ArgumentCaptor<ExperimentReportEntity> captured = ArgumentCaptor.forClass(ExperimentReportEntity.class);
        verify(reportMapper).insert(captured.capture());
        assertEquals(2, captured.getValue().getRevision());
        assertEquals("input-b", captured.getValue().getCalculationInputHash());
        verify(reportMapper, never()).update(any(), any());
    }

    @Test
    void decisionRequiresExistingRevisionAndNeverOverwritesPriorDecision() {
        ExperimentEntity decided = experiment();
        decided.setDecision("ACCEPTED");
        decided.setDecisionReason("evidence");
        decided.setDecisionReportRevision(1);
        decided.setDecidedBy(9L);
        when(experimentMapper.selectOne(any())).thenReturn(decided);
        ExperimentReportEntity report = new ExperimentReportEntity();
        report.setRevision(1);
        when(reportMapper.selectOne(any())).thenReturn(report);

        assertEquals(decided, service.decide(1L, 1, "ACCEPTED", "evidence", 9L));
        assertThrows(BusinessException.class,
                () -> service.decide(1L, 1, "REJECTED", "changed", 9L));
        verify(experimentMapper, never()).update(any(), any());
    }

    @Test
    void firstDecisionRecordsTheSelectedReportRevision() {
        ExperimentEntity decided = experiment();
        decided.setDecision("INSUFFICIENT_EVIDENCE");
        decided.setDecisionReason("样本不足");
        decided.setDecisionReportRevision(1);
        decided.setDecidedBy(9L);
        when(experimentMapper.selectOne(any())).thenReturn(experiment());
        ExperimentReportEntity report = new ExperimentReportEntity();
        report.setRevision(1);
        when(reportMapper.selectOne(any())).thenReturn(report);
        when(experimentMapper.update(any(), any())).thenReturn(1);
        when(experimentMapper.selectById(1L)).thenReturn(decided);

        assertEquals(decided, service.decide(1L, 1, "INSUFFICIENT_EVIDENCE", "样本不足", 9L));
        verify(experimentMapper).update(any(), any());
    }

    @Test
    void decisionWriteConflictIsNotSilentlyAccepted() {
        when(experimentMapper.selectOne(any())).thenReturn(experiment());
        when(reportMapper.selectOne(any())).thenReturn(new ExperimentReportEntity());

        assertThrows(BusinessException.class,
                () -> service.decide(1L, 1, "INSUFFICIENT_EVIDENCE", "样本不足", 9L));
    }

    private ExperimentEntity experiment() {
        ExperimentEntity entity = new ExperimentEntity();
        entity.setId(1L);
        entity.setSpaceId(2L);
        entity.setStatus("COMPLETED");
        entity.setManifestHash("manifest");
        return entity;
    }

    private ExperimentReportPersistenceService.Prepared prepared(String inputHash) {
        return new ExperimentReportPersistenceService.Prepared("manifest", 1, inputHash,
                "{}", 1, "{}", "content");
    }
}
