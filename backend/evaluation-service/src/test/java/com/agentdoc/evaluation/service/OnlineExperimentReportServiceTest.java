package com.agentdoc.evaluation.service;

import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineTaskFeign;
import com.agentdoc.common.feign.OnlineAgentFeign;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentReportMapper;
import com.agentdoc.evaluation.pojo.dto.OnlineReportRecalculateDTO;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_MANAGE;

class OnlineExperimentReportServiceTest {
    private final OnlineExperimentMapper experiments = mock(OnlineExperimentMapper.class);
    private final OnlineReportSnapshotService snapshots = mock(OnlineReportSnapshotService.class);
    private final OnlineReportPersistenceService persistence = mock(OnlineReportPersistenceService.class);
    private final OnlineExecutionAuthority authority = mock(OnlineExecutionAuthority.class);
    private final OnlineTaskFeign tasks = mock(OnlineTaskFeign.class);
    private final OnlineAgentFeign agents = mock(OnlineAgentFeign.class);
    private final SpaceAccessService access = mock(SpaceAccessService.class);
    private final OnlineExperimentReportService service = new OnlineExperimentReportService(experiments, mock(OnlineExperimentReportMapper.class), authority, snapshots, persistence, tasks, agents, access, mock(OnlineReviewExposureService.class));
    private void fixture() { var row = new OnlineExperimentEntity(); row.setId(11L); row.setSpaceId(10L); row.setAcceptedSequence(100L); row.setCreatedAt(LocalDateTime.of(2026, 10, 1, 0, 0)); when(experiments.selectById(11L)).thenReturn(row); }
    @Test void ownerAndManagePermissionFailBeforeAnyFactQueryOrReportWrite() {
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserIdOrException).thenReturn(502L); fixture(); doThrow(new BusinessException(403, "权限撤销")).when(access).requirePermission(10L, EVALUATION_MANAGE);
            assertThatThrownBy(() -> service.recalculate("11", new OnlineReportRecalculateDTO("key", "0", "2026-10-12T10:00"))).isInstanceOf(BusinessException.class);
            verifyNoInteractions(tasks, agents, snapshots, persistence); verify(access).requireOwner(10L);
        }
    }
    @Test void sequenceTimePrecisionAndFutureCutoffsAreRejectedWithoutReadingSources() {
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserIdOrException).thenReturn(502L); fixture();
            for (var request : List.of(new OnlineReportRecalculateDTO("key", "101", "2026-10-12T10:00"), new OnlineReportRecalculateDTO("key", "01", "2026-10-12T10:00"), new OnlineReportRecalculateDTO("key", "1", "2099-01-01T00:00"), new OnlineReportRecalculateDTO("key", "1", "2026-10-12T10:00:00.1234"))) {
                assertThatThrownBy(() -> service.recalculate("11", request)).isInstanceOf(BusinessException.class);
            }
            verifyNoInteractions(tasks, agents, snapshots, persistence);
        }
    }
}
