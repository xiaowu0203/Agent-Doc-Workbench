package com.agentdoc.evaluation.service;

import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.feign.vo.OnlineReviewGoalVO;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.enums.EvaluationFeedbackLabel;
import com.agentdoc.evaluation.enums.OnlineReviewExposureSource;
import com.agentdoc.evaluation.mapper.*;
import com.agentdoc.evaluation.pojo.dto.EvaluationFeedbackCreateDTO;
import com.agentdoc.evaluation.pojo.dto.OnlineExperimentDecisionDTO;
import com.agentdoc.evaluation.pojo.dto.OnlineQualityFeedbackDTO;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentReportEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.beans.factory.ObjectProvider;
import com.agentdoc.evaluation.config.OnlineReconciliationProperties;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

/** 两个独立 Spring 事务上下文验证版本、结论和资格的线性化边界。 */
@EnabledIfEnvironmentVariable(named = "P603_EVALUATION_MYSQL_URL", matches = ".+")
class OnlineReportReviewMySqlTest {
    private static AnnotationConfigApplicationContext first, second;
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config extends OnlineQualityFeedbackPersistenceMySqlTest.Config {
        @Override @Bean SqlSessionFactory factory(DriverManagerDataSource source) throws Exception {
            var built = super.factory(source);
            for (var mapper : List.of(OnlineExperimentReportMapper.class, OnlineExperimentActionRequestMapper.class,
                    OnlineExperimentEventMapper.class, OnlineExecutionSlotMapper.class, OnlineReportInputChunkMapper.class)) { built.getConfiguration().addMapper(mapper); }
            return built;
        }
        @Bean OnlineReportPersistenceService reportPersistence(SqlSessionTemplate session) {
            return new OnlineReportPersistenceService(session.getMapper(OnlineExperimentMapper.class), session.getMapper(OnlineExperimentReportMapper.class), session.getMapper(OnlineExperimentActionRequestMapper.class), session.getMapper(OnlineReportInputChunkMapper.class));
        }
        @Bean OnlineExecutionAuthority authority(SqlSessionTemplate session) {
            return new OnlineExecutionAuthority(session.getMapper(OnlineExperimentMapper.class), session.getMapper(OnlineAssignmentMapper.class), session.getMapper(OnlineExecutionSlotMapper.class), session.getMapper(OnlineExperimentEventMapper.class));
        }
        @Bean OnlineReconciliationProperties properties() { return new OnlineReconciliationProperties(); }
        @Bean OnlineSafetyFeedbackService safety(SqlSessionTemplate session, OnlineExecutionAuthority authority, OnlineReconciliationProperties properties, ObjectProvider<OnlineSafetyFeedbackService> self) {
            return new OnlineSafetyFeedbackService(session.getMapper(OnlineExperimentMapper.class), session.getMapper(OnlineAssignmentMapper.class), session.getMapper(EvaluationFeedbackMapper.class), session.getMapper(OnlineExperimentEventMapper.class), authority, properties, self);
        }
    }
    @BeforeAll static void open() { first = new AnnotationConfigApplicationContext(Config.class); second = new AnnotationConfigApplicationContext(Config.class); }
    @AfterAll static void close() { if (first != null) { first.close(); } if (second != null) { second.close(); } }
    private AnnotationConfigApplicationContext context(int index) { return index % 2 == 0 ? first : second; }
    private JdbcTemplate sql() { return new JdbcTemplate(first.getBean(DriverManagerDataSource.class)); }
    private <T> T mapper(Class<T> type) { return first.getBean(SqlSessionTemplate.class).getMapper(type); }
    private long experiment(boolean tracked, String status) {
        long id = IdWorker.getId();
        var payload = new LinkedHashMap<String, Object>(Map.of("experimentId", Long.toString(id), "spaceId", "10", "agentId", "20"));
        if (tracked) { payload.put("reviewTrackingSchemaVersion", 1); }
        String manifest = OnlineProtocolUtils.canonical("online.manifest", payload);
        sql().update("INSERT INTO online_experiment(id,space_id,agent_id,name,client_request_key,request_hash,manifest_schema_version,manifest_json,manifest_hash,status,authorized_token_budget,max_task_count,created_by,accepted_sequence) VALUES(?,10,20,'报告资格验证',?,?,2,?,?,?,10000,100,999,100)",
                id, "report-" + id, "a".repeat(64), manifest, StableSnapshotUtils.sha256Utf8(manifest), status); return id;
    }
    private OnlineAssignmentEntity assignment(long experiment) {
        long id = IdWorker.getId();
        sql().update("INSERT INTO online_assignment(id,experiment_id,space_id,agent_id,task_id,document_id,created_by,client_request_key,request_hash,input_snapshot_schema_version,input_snapshot_hash,bucket,variant,config_schema_version,config_hash,binding_schema_version,binding_hash,reserved_token_budget,execution_id) VALUES(?,?,10,20,?,41,501,?,?,1,?,1,'BASELINE',2,?,2,?,100,?)",
                id, experiment, id + 1, "receipt-" + id, "a".repeat(64), "b".repeat(64), "c".repeat(64), "d".repeat(64), id + 2);
        return mapper(OnlineAssignmentMapper.class).selectById(id);
    }
    private OnlineOriginalInputVO input(OnlineAssignmentEntity row) { return new OnlineOriginalInputVO(row.getTaskId().toString(), row.getExecutionId().toString(), "10", "20", row.getExperimentId().toString(), row.getId().toString(), row.getBindingHash(), "41", "FORMAL", 3L, "b".repeat(64), true); }
    private OnlineQualityFeedbackDTO quality(OnlineAssignmentEntity row, String supersedes) { return new OnlineQualityFeedbackDTO(row.getId().toString(), "ORIGINAL_TEXT", row.getExecutionId().toString(), "e".repeat(64), 0, "BLINDED", supersedes, List.of("VERIFIED_ORIGINAL"), false, null); }
    private String capture(OnlineAssignmentEntity row, long actor, int instance) { return context(instance).getBean(OnlineBlindReviewReceiptService.class).capture(row, actor, new OnlineReviewGoalVO(row.getTaskId().toString(), row.getInputSnapshotHash(), "冻结用户目标"), "ORIGINAL_TEXT", row.getExecutionId().toString(), "e".repeat(64)); }
    private OnlineExperimentReportEntity save(long experiment, String key, String input, boolean eligible, List<String> safety, int instance) {
        String manifest = mapper(OnlineExperimentMapper.class).selectById(experiment).getManifestHash();
        String body = OnlineProtocolUtils.canonical("online.report", Map.of("schemaVersion", 2, "reportType", "ONLINE_V1", "manifestHash", manifest,
                "decisionEligibility", Map.of("comparisonAllowed", eligible, "confirmedSafetyFeedbackIds", safety)));
        return context(instance).getBean(OnlineReportPersistenceService.class).save(experiment, 502L, key, OnlineProtocolUtils.hash("online.report-request", Map.of("key", key)), manifest, 100, LocalDateTime.now(), OnlineProtocolUtils.canonical("online.report-input", Map.of("fact", input)), body);
    }
    @Test void concurrentKeysReuseOneRevisionAndNewFactsAppendWithoutOverwritingHistory() throws Exception {
        long experiment = experiment(true, "STOPPED");
        try (var pool = Executors.newFixedThreadPool(8)) {
            var jobs = IntStream.range(0, 8).mapToObj(i -> (Callable<Long>) () -> save(experiment, "parallel-" + i, "frozen facts", true, List.of(), i).getId()).toList();
        var ids = new HashSet<Long>(); for (var future : pool.invokeAll(jobs)) { ids.add(future.get()); } assertThat(ids).hasSize(1);
        }
        var before = sql().queryForMap("SELECT * FROM online_experiment_report WHERE experiment_id=?", experiment);
        var next = save(experiment, "next", "new frozen facts", false, List.of(), 1); assertThat(next.getRevision()).isEqualTo(2);
        assertThat(sql().queryForMap("SELECT * FROM online_experiment_report WHERE experiment_id=? AND revision=1", experiment)).isEqualTo(before);
        assertThat(save(experiment, "parallel-0", "ignored on same request retry", false, List.of(), 0).getRevision()).isEqualTo(1);
        assertThat(sql().queryForObject("SELECT COUNT(*) FROM online_experiment_report WHERE experiment_id=?", Long.class, experiment)).isEqualTo(2);
        assertThat(sql().queryForObject("SELECT COUNT(*) FROM online_experiment_action_request WHERE experiment_id=?", Long.class, experiment)).isEqualTo(9);
    }
    @Test void decisionRequiresStoppedEligibleReportAndFrozenHashAndDoesNotPromoteAgent() {
        long experiment = experiment(true, "ACTIVE"); var report = save(experiment, "first", "facts", true, List.of(), 0);
        var decision = new OnlineExperimentDecisionDTO("decision", 1, report.getReportHash(), "ACCEPTED", "完整覆盖", null);
        var service = first.getBean(OnlineReportPersistenceService.class);
        assertThatThrownBy(() -> service.decide(experiment, 502L, decision, "a".repeat(64))).isInstanceOf(BusinessException.class).hasMessageContaining("OBSERVATION_NOT_FINISHED");
        sql().update("UPDATE online_experiment SET status='STOPPED' WHERE id=?", experiment);
        assertThatThrownBy(() -> service.decide(experiment, 502L, new OnlineExperimentDecisionDTO("forged", 1, "b".repeat(64), "ACCEPTED", "伪造摘要", null), "c".repeat(64))).isInstanceOf(BusinessException.class);
        service.decide(experiment, 502L, decision, "a".repeat(64)); second.getBean(OnlineReportPersistenceService.class).decide(experiment, 502L, decision, "a".repeat(64));
        var row = mapper(OnlineExperimentMapper.class).selectById(experiment); assertThat(row.getDecisionReportHash()).isEqualTo(report.getReportHash()); assertThat(row.getAgentId()).isEqualTo(20L);
        var newer = save(experiment, "newer", "facts2", true, List.of(), 0);
        assertThatThrownBy(() -> service.decide(experiment, 502L, new OnlineExperimentDecisionDTO("change", 2, newer.getReportHash(), "REJECTED", "变更结论", null), "d".repeat(64))).isInstanceOf(BusinessException.class).hasMessageContaining("DECISION_ALREADY_EXISTS");
    }
    @Test void insufficientReportOnlyAllowsInsufficientAndSafetyRejectNeedsFrozenConfirmation() {
        long experiment = experiment(true, "STOPPED"); var report = save(experiment, "insufficient", "facts", false, List.of(), 0); var service = first.getBean(OnlineReportPersistenceService.class);
        for (String decision : List.of("ACCEPTED", "REJECTED")) { assertThatThrownBy(() -> service.decide(experiment, 502L, new OnlineExperimentDecisionDTO(decision, 1, report.getReportHash(), decision, "覆盖不足", null), "a".repeat(64))).isInstanceOf(BusinessException.class).hasMessageContaining("INSUFFICIENT_EVIDENCE"); }
        service.decide(experiment, 502L, new OnlineExperimentDecisionDTO("insufficient-result", 1, report.getReportHash(), "INSUFFICIENT_EVIDENCE", "覆盖不足", null), "b".repeat(64));
        long safetyExperiment = experiment(true, "PAUSED"); var safety = save(safetyExperiment, "safety", "confirmed facts", false, List.of("91"), 0);
        assertThatThrownBy(() -> service.decide(safetyExperiment, 502L, new OnlineExperimentDecisionDTO("fake", 1, safety.getReportHash(), "REJECTED", "低分不等于安全事件", "92"), "c".repeat(64))).isInstanceOf(BusinessException.class).hasMessageContaining("SAFETY_EVIDENCE_REQUIRED");
        service.decide(safetyExperiment, 502L, new OnlineExperimentDecisionDTO("confirmed", 1, safety.getReportHash(), "REJECTED", "已确认严重安全事实", "91"), "d".repeat(64));
    }
    @Test void damagedInputOrUnknownSchemaCannotDriveDecisionButSummaryHasSeparateVerificationState() {
        long experiment = experiment(true, "STOPPED"); var report = save(experiment, "first", "facts", true, List.of(), 0);
        assertThat(OnlineReportPersistenceService.summary(report).state()).isEqualTo("SUMMARY_VERIFIED");
        sql().update("UPDATE online_experiment_report SET input_json='tampered' WHERE id=?", report.getId());
        var damaged = mapper(OnlineExperimentReportMapper.class).selectById(report.getId()); assertThat(OnlineReportPersistenceService.view(damaged).state()).isEqualTo("HASH_MISMATCH");
        assertThatThrownBy(() -> first.getBean(OnlineReportPersistenceService.class).decide(experiment, 502L, new OnlineExperimentDecisionDTO("reject", 1, report.getReportHash(), "ACCEPTED", "不可信输入", null), "a".repeat(64))).isInstanceOf(BusinessException.class);
        damaged.setSchemaVersion(99); assertThat(OnlineReportPersistenceService.view(damaged).state()).isEqualTo("UNSUPPORTED_SCHEMA");
    }
    @Test void receiptRequiresTrackedManifestIndependentReviewerAndImmutableOriginalProof() {
        var historical = assignment(experiment(false, "STOPPED")); assertThat(capture(historical, 502, 0)).isEqualTo("UNKNOWN");
        var row = assignment(experiment(true, "STOPPED")); assertThat(capture(row, 501, 0)).isEqualTo("NON_BLINDED"); assertThat(capture(row, 999, 0)).isEqualTo("NON_BLINDED");
        assertThat(capture(row, 502, 0)).isEqualTo("BLINDED"); var receipt = second.getBean(OnlineBlindReviewReceiptService.class).require(row, quality(row, null), 502L);
        assertThat(receipt.getSourceHash()).isEqualTo(OnlineBlindReviewReceiptService.hash(receipt));
        assertThatThrownBy(() -> first.getBean(OnlineBlindReviewReceiptService.class).require(row, quality(row, null), 503L)).isInstanceOf(BusinessException.class);
        sql().update("UPDATE online_blind_review_receipt SET original_evidence_hash=? WHERE id=?", "f".repeat(64), receipt.getId());
        assertThatThrownBy(() -> first.getBean(OnlineBlindReviewReceiptService.class).require(row, quality(row, null), 502L)).isInstanceOf(BusinessException.class);
    }
    @Test void concurrentHiddenReadsHaveOneProofAndLaterExposureDowngradesCorrectionWithoutRewritingFirst() throws Exception {
        var row = assignment(experiment(true, "STOPPED"));
        try (var pool = Executors.newFixedThreadPool(8)) { var jobs = IntStream.range(0, 8).mapToObj(i -> (Callable<String>) () -> capture(row, 502, i)).toList(); for (var future : pool.invokeAll(jobs)) { assertThat(future.get()).isEqualTo("BLINDED"); } }
        assertThat(sql().queryForObject("SELECT COUNT(*) FROM online_blind_review_receipt WHERE assignment_id=?", Long.class, row.getId())).isEqualTo(1);
        var request = new EvaluationFeedbackCreateDTO(10L, null, null, null, EvaluationFeedbackLabel.REJECTED, null, null, quality(row, null));
        var firstFeedback = first.getBean(OnlineQualityFeedbackPersistenceService.class).append(input(row), "f".repeat(64), request, 502L);
        var fact = mapper(EvaluationFeedbackMapper.class).selectById(firstFeedback.id()); assertThat(fact.getBlindness()).isEqualTo("BLINDED"); assertThat(OnlineQualityFeedbackFacts.valid(fact)).isTrue();
        var before = sql().queryForMap("SELECT * FROM evaluation_feedback WHERE id=?", firstFeedback.id());
        second.getBean(OnlineReviewExposureService.class).record(row.getExperimentId(), 10L, 502L, OnlineReviewExposureSource.REPORT);
        var correction = new EvaluationFeedbackCreateDTO(10L, null, null, null, EvaluationFeedbackLabel.REJECTED, null, "读取报告后更正", quality(row, firstFeedback.id().toString()));
        var revised = second.getBean(OnlineQualityFeedbackPersistenceService.class).append(input(row), "f".repeat(64), correction, 502L);
        assertThat(revised.onlineQuality().blindness()).isEqualTo("NON_BLINDED"); assertThat(OnlineQualityFeedbackFacts.valid(mapper(EvaluationFeedbackMapper.class).selectById(revised.id()))).isTrue();
        assertThat(sql().queryForMap("SELECT * FROM evaluation_feedback WHERE id=?", firstFeedback.id())).isEqualTo(before);
    }
    @Test void knownExposureAndHiddenReadRaceNeverProducesEligibleBlindedFeedbackAfterExposure() throws Exception {
        var row = assignment(experiment(true, "STOPPED"));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var hidden = pool.submit(() -> capture(row, 502, 0));
            var known = pool.submit(() -> second.getBean(OnlineReviewExposureService.class).record(row.getExperimentId(), 10L, 502L, OnlineReviewExposureSource.RELATED_RESOURCE_READ));
            assertThat(hidden.get()).isIn("BLINDED", "NON_BLINDED"); known.get();
        }
        assertThat(capture(row, 502, 1)).isEqualTo("NON_BLINDED");
        var quality = new OnlineQualityFeedbackDTO(row.getId().toString(), "ORIGINAL_TEXT", row.getExecutionId().toString(), "e".repeat(64), 0, "UNKNOWN", null, List.of(), false, null);
        var request = new EvaluationFeedbackCreateDTO(10L, null, null, null, EvaluationFeedbackLabel.REJECTED, null, null, quality);
        assertThat(first.getBean(OnlineQualityFeedbackPersistenceService.class).append(input(row), "f".repeat(64), request, 502L).onlineQuality().blindness()).isEqualTo("NON_BLINDED");
    }
    @Test void confirmedSafetyFactPausesOnceWhileOrdinaryZeroAndTamperedFactsCannotTriggerProtection() {
        var row = assignment(experiment(true, "ACTIVE"));
        var low = new OnlineQualityFeedbackDTO(row.getId().toString(), "ORIGINAL_TEXT", row.getExecutionId().toString(), "e".repeat(64), 0, "NON_BLINDED", null, List.of(), false, null);
        var persist = first.getBean(OnlineQualityFeedbackPersistenceService.class);
        var feedback = persist.append(input(row), "f".repeat(64), new EvaluationFeedbackCreateDTO(10L, null, null, null, EvaluationFeedbackLabel.REJECTED, null, null, low), 502L);
        var protection = first.getBean(OnlineSafetyFeedbackService.class);
        assertThatThrownBy(() -> protection.consume(feedback.id())).isInstanceOf(BusinessException.class);
        sql().update("UPDATE evaluation_feedback SET serious_safety_event=1,safety_reason='伪造事件' WHERE id=?", feedback.id());
        assertThatThrownBy(() -> protection.consume(feedback.id())).isInstanceOf(BusinessException.class);
        assertThat(mapper(OnlineExperimentMapper.class).selectById(row.getExperimentId()).getStatus()).isEqualTo("ACTIVE");
        var safetyRow = assignment(experiment(true, "ACTIVE"));
        var confirmation = new OnlineQualityFeedbackDTO(safetyRow.getId().toString(), "ORIGINAL_TEXT", safetyRow.getExecutionId().toString(), "e".repeat(64), 0, "NON_BLINDED", null, List.of("SAFETY_CONFIRMED"), true, "原始产物泄露受保护信息，OWNER确认");
        var confirmed = persist.append(input(safetyRow), "f".repeat(64), new EvaluationFeedbackCreateDTO(10L, null, null, null, EvaluationFeedbackLabel.REJECTED, null, null, confirmation), 999L);
        protection.consume(confirmed.id()); second.getBean(OnlineSafetyFeedbackService.class).consume(confirmed.id());
        assertThat(mapper(OnlineExperimentMapper.class).selectById(safetyRow.getExperimentId()).getStatus()).isEqualTo("PAUSED");
        assertThat(sql().queryForObject("SELECT COUNT(*) FROM online_experiment_event WHERE experiment_id=? AND event_type='CONFIRMED_SAFETY_EVENT'", Long.class, safetyRow.getExperimentId())).isEqualTo(1);
    }
    @Test void largeInputChunksKeepExactHashAndMissingOrTamperedChunkCannotDriveDecision() {
        long experiment = experiment(true, "STOPPED"); var report = save(experiment, "large", "😀真实冻结输入".repeat(150000), true, List.of(), 0);
        assertThat(report.getInputJson()).contains("online.report-input-chunks"); assertThat(OnlineReportPersistenceService.view(report).state()).isEqualTo("SUPPORTED");
        var loaded = second.getBean(OnlineReportPersistenceService.class).prior(experiment, 502L, "large", OnlineProtocolUtils.hash("online.report-request", Map.of("key", "large")));
        assertThat(loaded.getResolvedInputJson()).isEqualTo(report.getResolvedInputJson());
        sql().update("UPDATE online_report_input_chunk SET content='tampered' WHERE report_id=? AND chunk_no=0", report.getId());
        var damaged = first.getBean(OnlineReportPersistenceService.class).completeInput(mapper(OnlineExperimentReportMapper.class).selectById(report.getId()));
        assertThat(OnlineReportPersistenceService.view(damaged).state()).isEqualTo("HASH_MISMATCH");
        assertThatThrownBy(() -> first.getBean(OnlineReportPersistenceService.class).decide(experiment, 502L, new OnlineExperimentDecisionDTO("reject-tamper", 1, report.getReportHash(), "ACCEPTED", "输入损坏", null), "a".repeat(64))).isInstanceOf(BusinessException.class);
        sql().update("DELETE FROM online_report_input_chunk WHERE report_id=? AND chunk_no=1", report.getId());
        assertThat(OnlineReportPersistenceService.view(first.getBean(OnlineReportPersistenceService.class).completeInput(mapper(OnlineExperimentReportMapper.class).selectById(report.getId()))).state()).isEqualTo("HASH_MISMATCH");
    }
}
