package com.agentdoc.evaluation.service;

import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.evaluation.enums.OnlineReviewExposureSource;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.mapper.OnlineReviewExposureMapper;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineReviewExposureEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnlineReviewExposureServiceTest {
    private final OnlineExperimentMapper experiments = mock(OnlineExperimentMapper.class);
    private final OnlineReviewExposureMapper records = mock(OnlineReviewExposureMapper.class);
    private final OnlineReviewExposureService service = new OnlineReviewExposureService(experiments, records);
    private void experiment() { var row = new OnlineExperimentEntity(); row.setId(11L); row.setSpaceId(10L); when(experiments.lock(11L)).thenReturn(row); }
    @Test void firstReadFreezesHumanSourceAndRepeatedReadNeverChangesIt() {
        experiment(); service.record(11L, 10L, 501L, OnlineReviewExposureSource.EXPERIMENT_DETAIL);
        var captured = ArgumentCaptor.forClass(OnlineReviewExposureEntity.class); verify(records).insert(captured.capture());
        var row = captured.getValue(); assertThat(row.getActorId()).isEqualTo(501L); assertThat(row.getSourceHash()).isEqualTo(OnlineReviewExposureService.hash(row));
        when(records.selectOne(any())).thenReturn(row); clearInvocations(records);
        service.record(11L, 10L, 501L, OnlineReviewExposureSource.ASSIGNMENT_LIST);
        verify(records, never()).insert(any(OnlineReviewExposureEntity.class)); verify(records, never()).updateById(any(OnlineReviewExposureEntity.class));
        assertThat(row.getSource()).isEqualTo("EXPERIMENT_DETAIL");
    }
    @Test void wrongExperimentSpaceOrMissingHumanCannotRecord() {
        experiment(); assertThatThrownBy(() -> service.record(11L, 99L, 501L, OnlineReviewExposureSource.EXPERIMENT_DETAIL)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.record(11L, 10L, null, OnlineReviewExposureSource.EXPERIMENT_DETAIL)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(records);
    }
    @Test void tamperedExistingFactFailsClosedInsteadOfReplacingIt() {
        experiment(); service.record(11L, 10L, 501L, OnlineReviewExposureSource.EXPERIMENT_DETAIL);
        var captured = ArgumentCaptor.forClass(OnlineReviewExposureEntity.class); verify(records).insert(captured.capture());
        var row = captured.getValue(); row.setSource("ASSIGNMENT_LIST"); when(records.selectOne(any())).thenReturn(row);
        assertThatThrownBy(() -> service.known(11L, 501L)).isInstanceOf(BusinessException.class);
    }
}
