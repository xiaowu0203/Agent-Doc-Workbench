package com.agentdoc.evaluation.service;

import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.feign.OnlineTaskFeign;
import com.agentdoc.common.feign.OnlineAgentFeign;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentReportMapper;
import com.agentdoc.evaluation.pojo.dto.OnlineReportRecalculateDTO;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentReportEntity;
import com.agentdoc.evaluation.enums.OnlineReviewExposureSource;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_MANAGE;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.SPACE_READ;

class OnlineExperimentReportServiceTest {
    @Test void suggestedRangeUsesAuthoritativeLongSequenceAndServerClockWithoutGeneratingReport() {
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserIdOrException).thenReturn(502L); fixture();
            experiments.selectById(11L).setAcceptedSequence(9007199254740993L);
            var before = LocalDateTime.now().minusSeconds(1);
            var result = service.rangeSuggestion("11");
            assertThat(result.assignmentCutoffSequence()).isEqualTo("9007199254740993");
            assertThat(result.observationCutoffAt()).isBetween(before, LocalDateTime.now());
            assertThat(result.observationCutoffAt().getNano() % 1_000_000).isZero();
            assertThat(result.timeZone()).isEqualTo(ZoneId.systemDefault().getId());
            verify(access).requireOwner(10L); verify(access).requirePermission(10L, EVALUATION_MANAGE);
            verify(exposures).record(11L, 10L, 502L, OnlineReviewExposureSource.REPORT);
            verifyNoInteractions(reports, tasks, agents, snapshots, persistence);
        }
    }
    private final OnlineExperimentMapper experiments = mock(OnlineExperimentMapper.class);
    private final OnlineReportSnapshotService snapshots = mock(OnlineReportSnapshotService.class);
    private final OnlineReportPersistenceService persistence = mock(OnlineReportPersistenceService.class);
    private final OnlineExecutionAuthority authority = mock(OnlineExecutionAuthority.class);
    private final OnlineTaskFeign tasks = mock(OnlineTaskFeign.class);
    private final OnlineAgentFeign agents = mock(OnlineAgentFeign.class);
    private final SpaceAccessService access = mock(SpaceAccessService.class);
    private final OnlineExperimentReportMapper reports = mock(OnlineExperimentReportMapper.class);
    private final OnlineReviewExposureService exposures = mock(OnlineReviewExposureService.class);
    private final OnlineExperimentReportService service = new OnlineExperimentReportService(experiments, reports, authority, snapshots, persistence, tasks, agents, access, exposures);
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

    @Test void revisionIndexIsBoundedMetadataOnlyAndMarksOnlyExactDecisionReference() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), OnlineExperimentReportEntity.class);
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserIdOrException).thenReturn(502L); fixture();
            var experiment = experiments.selectById(11L);
            experiment.setDecision("ACCEPTED"); experiment.setDecisionReportRevision(12); experiment.setDecisionReportHash("hash");
            var match = new OnlineExperimentReportEntity(); match.setRevision(12); match.setReportHash("hash");
            var mismatch = new OnlineExperimentReportEntity(); mismatch.setRevision(12); mismatch.setReportHash("other");
            var old = new OnlineExperimentReportEntity(); old.setRevision(11); old.setReportHash("hash");
            when(reports.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
                Page<OnlineExperimentReportEntity> page = invocation.getArgument(0);
                LambdaQueryWrapper<OnlineExperimentReportEntity> query = invocation.getArgument(1);
                assertThat(page.getCurrent()).isEqualTo(2); assertThat(page.getSize()).isEqualTo(10);
                assertThat(query.getSqlSelect()).contains("revision", "report_hash", "created_at", "observed_at")
                        .doesNotContain("input_json", "report_json");
                assertThat(query.getSqlSegment()).contains("experiment_id", "space_id", "ORDER BY revision DESC");
                assertThat(query.getParamNameValuePairs().values()).containsExactlyInAnyOrder(11L, 10L);
                return page.setTotal(13).setRecords(List.of(match, mismatch, old));
            });
            var param = new PageParam(); param.setPageNum(2);
            var result = service.revisions("11", param);
            assertThat(result.total()).isEqualTo(13); assertThat(result.pageNum()).isEqualTo(2);
            assertThat(result.records()).extracting(row -> row.decisionReference()).containsExactly(true, false, false);
            var order = inOrder(access, exposures, reports);
            order.verify(access).requirePermission(10L, SPACE_READ); order.verify(access).requirePermission(10L, EVALUATION_READ);
            order.verify(exposures).record(11L, 10L, 502L, OnlineReviewExposureSource.REPORT);
            order.verify(reports).selectPage(any(Page.class), any());
            verifyNoInteractions(tasks, agents, snapshots, persistence);
        }
    }

    @Test void deniedReadOrExposureFailureNeverReturnsIndex() {
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserIdOrException).thenReturn(502L); fixture();
            doThrow(new BusinessException(403, "权限撤销")).when(access).requirePermission(10L, EVALUATION_READ);
            assertThatThrownBy(() -> service.revisions("11", new PageParam())).isInstanceOf(BusinessException.class);
            verifyNoInteractions(reports, exposures);
            reset(access);
            doThrow(new IllegalStateException("知情记录失败")).when(exposures).record(11L, 10L, 502L, OnlineReviewExposureSource.REPORT);
            assertThatThrownBy(() -> service.revisions("11", new PageParam())).isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(reports);
        }
    }

    @Test void oversizedIndexIsRejectedBeforeReadingExperiment() {
        var param = new PageParam(); param.setPageSize(101);
        assertThatThrownBy(() -> service.revisions("11", param)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(experiments, reports, exposures);
    }
}
