package com.agentdoc.evaluation.service;
import com.agentdoc.evaluation.mapper.OnlineBlindReviewReceiptMapper;

import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.feign.vo.OnlineOriginalChangesVO;
import com.agentdoc.common.utils.OnlineOriginalChangeUtils;
import com.agentdoc.evaluation.enums.EvaluationEvidenceType;
import com.agentdoc.evaluation.enums.EvaluationFeedbackLabel;
import com.agentdoc.evaluation.mapper.EvaluationFeedbackMapper;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.mapper.OnlineReviewExposureMapper;
import com.agentdoc.evaluation.enums.OnlineReviewExposureSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.TimeoutException;
import com.agentdoc.evaluation.pojo.dto.EvaluationFeedbackCreateDTO;
import com.agentdoc.evaluation.pojo.dto.OnlineQualityFeedbackDTO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

/** 双 Spring 实例使用真实 MySQL 行锁，证明首评唯一、更正链和历史不覆盖。 */
@EnabledIfEnvironmentVariable(named = "P603_EVALUATION_MYSQL_URL", matches = ".+")
class OnlineQualityFeedbackPersistenceMySqlTest {
    private static AnnotationConfigApplicationContext first, second;
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config extends OnlineApprovalFeedbackMySqlTest.Config {
        @Override @Bean SqlSessionFactory factory(DriverManagerDataSource source) throws Exception {
            var factory = super.factory(source); factory.getConfiguration().addMapper(OnlineExperimentMapper.class);
            factory.getConfiguration().addMapper(OnlineReviewExposureMapper.class); return factory;
        }
        @Bean OnlineReviewExposureService exposures(SqlSessionTemplate session) {
            return new OnlineReviewExposureService(session.getMapper(OnlineExperimentMapper.class), session.getMapper(OnlineReviewExposureMapper.class));
        }
        @Bean OnlineBlindReviewReceiptService receipts(SqlSessionTemplate session, OnlineReviewExposureService exposures) {
            session.getConfiguration().addMapper(OnlineBlindReviewReceiptMapper.class);
            return new OnlineBlindReviewReceiptService(session.getMapper(OnlineExperimentMapper.class), session.getMapper(OnlineAssignmentMapper.class), session.getMapper(OnlineBlindReviewReceiptMapper.class), exposures);
        }
        @Bean OnlineQualityFeedbackPersistenceService quality(SqlSessionTemplate session, OnlineReviewExposureService exposures, OnlineBlindReviewReceiptService receipts) {
            return new OnlineQualityFeedbackPersistenceService(session.getMapper(OnlineAssignmentMapper.class), session.getMapper(EvaluationFeedbackMapper.class),
                    session.getMapper(OnlineExperimentMapper.class), exposures, receipts);
        }
    }
    @BeforeAll static void open() { first = new AnnotationConfigApplicationContext(Config.class); second = new AnnotationConfigApplicationContext(Config.class); }
    @AfterAll static void close() { if (first != null) { first.close(); } if (second != null) { second.close(); } }
    private JdbcTemplate sql() { return new JdbcTemplate(first.getBean(DriverManagerDataSource.class)); }
    private OnlineQualityFeedbackPersistenceService service(int instance) { return (instance % 2 == 0 ? first : second).getBean(OnlineQualityFeedbackPersistenceService.class); }
    private OnlineOriginalInputVO input() {
        long id = IdWorker.getId();
        sql().update("INSERT INTO online_experiment(id,space_id,agent_id,name,client_request_key,request_hash,manifest_schema_version,manifest_json,manifest_hash,status,authorized_token_budget,max_task_count,created_by) VALUES(?,10,20,'质量验证',?,?,2,'{}',?,'CREATED',10000,100,999)",
                id + 1, "quality-exp-" + id, "a".repeat(64), "b".repeat(64));
        sql().update("INSERT INTO online_assignment(id,experiment_id,space_id,agent_id,task_id,document_id,created_by,client_request_key,request_hash,input_snapshot_schema_version,input_snapshot_hash,bucket,variant,config_schema_version,config_hash,binding_schema_version,binding_hash,reserved_token_budget,execution_id) VALUES(?,?,10,20,?,41,501,?,?,1,?,1,'BASELINE',2,?,2,?,100,?)",
                id, id + 1, id + 2, "quality-" + id, "a".repeat(64), "b".repeat(64), "c".repeat(64), "d".repeat(64), id + 3);
        return new OnlineOriginalInputVO(Long.toString(id + 2), Long.toString(id + 3), "10", "20", Long.toString(id + 1), Long.toString(id), "d".repeat(64), "41", "FORMAL", 3L, "b".repeat(64), true);
    }
    private EvaluationFeedbackCreateDTO request(OnlineOriginalInputVO input, int rubric, String blind, String supersedes, String comment) {
        return new EvaluationFeedbackCreateDTO(10L, null, null, null, EvaluationFeedbackLabel.REJECTED, null, comment,
                new OnlineQualityFeedbackDTO(input.assignmentId(), "ORIGINAL_TEXT", input.executionId(), "e".repeat(64), rubric,
                        blind, supersedes, List.of(), false, null));
    }
    private List<Long> race(OnlineOriginalInputVO input, EvaluationFeedbackCreateDTO request) throws Exception {
        try (var pool = Executors.newFixedThreadPool(8)) {
            var ready = new CountDownLatch(8); var start = new CountDownLatch(1);
            var jobs = IntStream.range(0, 8).mapToObj(i -> (Callable<Long>) () -> {
                ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("并发验证未同步开始"); }
                String identity = EvaluationEvidenceType.ORIGINAL_CHANGE_SET.name().equals(request.onlineQuality().originalEvidenceType())
                        ? request.onlineQuality().originalEvidenceHash() : "f".repeat(64);
                try { return service(i).append(input, identity, request, 501L + i).id(); }
                catch (BusinessException rejected) { return null; }
            }).toList();
            var futures = jobs.stream().map(pool::submit).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
        var accepted = new ArrayList<Long>();
            for (var future : futures) { Long id = future.get(); if (id != null) { accepted.add(id); } }
            return accepted;
        }
    }
    @Test void concurrentFirstFeedbackHasOneWinnerAndZeroIsStoredAsActualScore() throws Exception {
        var input = input(); var winners = race(input, request(input, 0, "NON_BLINDED", null, null));
        assertThat(winners).hasSize(1);
        var row = sql().queryForMap("SELECT * FROM evaluation_feedback WHERE id=?", winners.getFirst());
        assertThat(row.get("rubric_score")).isEqualTo(0); assertThat(row.get("score").toString()).isEqualTo("0.000000");
        assertThat(row.get("run_id")).isNull(); assertThat(row.get("case_run_id")).isNull();
        assertThat(row.get("blindness")).isEqualTo("NON_BLINDED"); assertThat(row.get("serious_safety_event").toString()).isIn("false", "0");
        assertThat(sql().queryForObject("SELECT COUNT(*) FROM evaluation_metric WHERE online_assignment_id=?", Long.class, input.assignmentId())).isZero();
    }
    @Test void concurrentCorrectionsAppendOneSuccessorAndRetainTheFirstHashAndKnownBias() throws Exception {
        var input = input(); var root = service(0).append(input, "f".repeat(64), request(input, 0, "NON_BLINDED", null, null), 501L);
        var before = sql().queryForMap("SELECT * FROM evaluation_feedback WHERE id=?", root.id());
        var winners = race(input, request(input, 4, "UNKNOWN", root.id().toString(), "原始产物已核对，更正评分"));
        assertThat(winners).hasSize(1);
        assertThat(sql().queryForMap("SELECT * FROM evaluation_feedback WHERE id=?", root.id())).isEqualTo(before);
        var corrected = sql().queryForMap("SELECT * FROM evaluation_feedback WHERE id=?", winners.getFirst());
        assertThat(corrected.get("blindness")).isEqualTo("NON_BLINDED"); assertThat(corrected.get("score").toString()).isEqualTo("1.000000");
        assertThat(corrected.get("facts_json").toString()).contains("\"declaredBlindness\":\"UNKNOWN\"");
        assertThat(corrected.get("source_hash")).isNotEqualTo(root.sourceHash());
        var next = service(0).append(input, "f".repeat(64), request(input, 2, "UNKNOWN", winners.getFirst().toString(), "继续核对原产物"), 502L);
        assertThat(next.onlineQuality().blindness()).isEqualTo("NON_BLINDED"); assertThat(next.score()).isEqualByComparingTo("0.5");
        assertThat(sql().queryForObject("SELECT COUNT(*) FROM evaluation_feedback WHERE online_assignment_id=?", Long.class, input.assignmentId())).isEqualTo(3);
    }
    @Test void noReasonStaleTailCrossAssignmentOrBindingDriftCannotAppend() {
        var input = input(); var root = service(0).append(input, "f".repeat(64), request(input, 0, "UNKNOWN", null, null), 501L);
        assertThatThrownBy(() -> service(1).append(input, "f".repeat(64), request(input, 4, "UNKNOWN", root.id().toString(), " "), 502L)).isInstanceOf(BusinessException.class);
        var corrected = service(1).append(input, "f".repeat(64), request(input, 4, "UNKNOWN", root.id().toString(), "更正"), 502L);
        assertThatThrownBy(() -> service(0).append(input, "f".repeat(64), request(input, 1, "UNKNOWN", root.id().toString(), "旧链尾"), 503L)).isInstanceOf(BusinessException.class);
        var other = input(); assertThatThrownBy(() -> service(0).append(other, "f".repeat(64), request(other, 1, "UNKNOWN", corrected.id().toString(), "跨分配"), 503L)).isInstanceOf(BusinessException.class);
        sql().update("UPDATE online_assignment SET binding_hash=? WHERE id=?", "a".repeat(64), input.assignmentId());
        assertThatThrownBy(() -> service(0).append(input, "f".repeat(64), request(input, 1, "UNKNOWN", corrected.id().toString(), "绑定变化"), 503L)).isInstanceOf(BusinessException.class);
        assertThat(sql().queryForObject("SELECT COUNT(*) FROM evaluation_feedback WHERE online_assignment_id=?", Long.class, input.assignmentId())).isEqualTo(2);
        assertThat(sql().queryForObject("SELECT COUNT(*) FROM evaluation_feedback WHERE online_assignment_id=?", Long.class, other.assignmentId())).isZero();
    }
    @Test void changedOriginalIdentityAndCorruptedCyclicHistoryAreRejected() {
        var input = input(); var root = service(0).append(input, "f".repeat(64), request(input, 0, "UNKNOWN", null, null), 501L);
        var correction = request(input, 4, "UNKNOWN", root.id().toString(), "更正");
        assertThatThrownBy(() -> service(0).append(input, "a".repeat(64), correction, 502L)).isInstanceOf(BusinessException.class);
        sql().update("UPDATE evaluation_feedback SET supersedes_feedback_id=id WHERE id=?", root.id());
        assertThatThrownBy(() -> service(1).append(input, "f".repeat(64), correction, 502L)).isInstanceOf(BusinessException.class);
        assertThat(sql().queryForObject("SELECT COUNT(*) FROM evaluation_feedback WHERE online_assignment_id=?", Long.class, input.assignmentId())).isEqualTo(1);
    }
    @Test void reasonCodeAloneIsAValidCorrectionReasonAndUnknownCanBecomeNonBlinded() {
        var input = input(); var root = service(0).append(input, "f".repeat(64), request(input, 0, "UNKNOWN", null, null), 501L);
        var quality = new OnlineQualityFeedbackDTO(input.assignmentId(), "ORIGINAL_TEXT", input.executionId(), "e".repeat(64), 1,
                "NON_BLINDED", root.id().toString(), List.of("RUBRIC_CORRECTION"), false, null);
        var correction = new EvaluationFeedbackCreateDTO(10L, null, null, null, EvaluationFeedbackLabel.NEEDS_CHANGES, null, null, quality);
        var added = service(1).append(input, "f".repeat(64), correction, 502L);
        assertThat(added.score()).isEqualByComparingTo("0.25"); assertThat(added.comment()).isNull();
        assertThat(added.onlineQuality().reasonCodes()).containsExactly("RUBRIC_CORRECTION");
        assertThat(added.onlineQuality().blindness()).isEqualTo("NON_BLINDED");
        assertThat(sql().queryForObject("SELECT blindness FROM evaluation_feedback WHERE id=?", String.class, root.id())).isEqualTo("UNKNOWN");
    }
    @Test void knownExposureOverridesUnknownWithoutRewritingEarlierFeedback() {
        var input = input(); long experiment = Long.parseLong(input.experimentId());
        var root = service(0).append(input, "f".repeat(64), request(input, 0, "UNKNOWN", null, null), 501L);
        var exposure = first.getBean(OnlineReviewExposureService.class);
        exposure.record(experiment, 10L, 501L, OnlineReviewExposureSource.EXPERIMENT_DETAIL);
        var original = sql().queryForMap("SELECT * FROM online_review_exposure WHERE experiment_id=? AND actor_id=501", experiment);
        second.getBean(OnlineReviewExposureService.class).record(experiment, 10L, 501L, OnlineReviewExposureSource.ASSIGNMENT_LIST);
        assertThat(sql().queryForMap("SELECT * FROM online_review_exposure WHERE experiment_id=? AND actor_id=501", experiment)).isEqualTo(original);
        var next = service(1).append(input, "f".repeat(64), request(input, 2, "UNKNOWN", root.id().toString(), "更正"), 501L);
        assertThat(next.onlineQuality().blindness()).isEqualTo("NON_BLINDED"); assertThat(next.factsJson()).contains("exposureId", "exposureHash");
        assertThat(sql().queryForObject("SELECT source_hash FROM evaluation_feedback WHERE id=?", String.class, root.id())).isEqualTo(root.sourceHash());
        assertThat(sql().queryForObject("SELECT blindness FROM evaluation_feedback WHERE id=?", String.class, root.id())).isEqualTo("UNKNOWN");
        var otherInput = input(); var other = service(1).append(otherInput, "f".repeat(64), request(otherInput, 0, "UNKNOWN", null, null), 501L);
        assertThat(other.onlineQuality().blindness()).isEqualTo("UNKNOWN");
    }
    @Test void simultaneousExposureReadsKeepOneImmutableFirstRecordAcrossInstances() throws Exception {
        var input = input(); long experiment = Long.parseLong(input.experimentId());
        try (var pool = Executors.newFixedThreadPool(8)) {
            var ready = new CountDownLatch(8); var start = new CountDownLatch(1);
            var futures = IntStream.range(0, 8).mapToObj(i -> pool.submit(() -> {
                ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException(); }
                (i % 2 == 0 ? first : second).getBean(OnlineReviewExposureService.class).record(experiment, 10L, 501L, OnlineReviewExposureSource.ASSIGNMENT_LIST);
                return true;
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown(); for (var future : futures) { assertThat(future.get()).isTrue(); }
        }
        assertThat(sql().queryForObject("SELECT COUNT(*) FROM online_review_exposure WHERE experiment_id=? AND actor_id=501", Long.class, experiment)).isEqualTo(1);
        var known = second.getBean(OnlineReviewExposureService.class).known(experiment, 501L);
        assertThat(known.getSourceHash()).isEqualTo(OnlineReviewExposureService.hash(known));
        assertThat(second.getBean(OnlineReviewExposureService.class).known(experiment, 502L)).isNull();
    }
    @Test void feedbackWaitsForAnUncommittedExposureAndUsesItAfterCommit() throws Exception {
        var input = input(); long experiment = Long.parseLong(input.experimentId());
        var written = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var exposing = pool.submit(() -> new TransactionTemplate(first.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
                first.getBean(OnlineReviewExposureService.class).record(experiment, 10L, 501L, OnlineReviewExposureSource.EXPERIMENT_DETAIL);
                written.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException(); } }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            }));
            assertThat(written.await(10, TimeUnit.SECONDS)).isTrue();
            var scoring = pool.submit(() -> service(1).append(input, "f".repeat(64), request(input, 0, "UNKNOWN", null, null), 501L));
            try { assertThatThrownBy(() -> scoring.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class); }
            finally { release.countDown(); }
            exposing.get(); assertThat(scoring.get().onlineQuality().blindness()).isEqualTo("NON_BLINDED");
        } finally { release.countDown(); }
    }
    private String setHash(OnlineOriginalInputVO input) {
        var good = OnlineOriginalChangeUtils.signed(input,"81","CHANGE_REQUEST",3L,null,"c".repeat(64),"d".repeat(64),true,"2026-10-12T12:00");
        var bad = OnlineOriginalChangeUtils.signed(input,"82","CHANGE_REQUEST",3L,null,"e".repeat(64),null,false,"2026-10-12T12:00");
        return OnlineOriginalChangeUtils.setIdentityHash(new OnlineOriginalChangesVO(2,"AVAILABLE",input,List.of(good,bad)));
    }
    private EvaluationFeedbackCreateDTO setRequest(OnlineOriginalInputVO input, String hash, int rubric, String supersedes, String comment) {
        return new EvaluationFeedbackCreateDTO(10L,null,null,null,EvaluationFeedbackLabel.NEEDS_CHANGES,null,comment,
                new OnlineQualityFeedbackDTO(input.assignmentId(),"ORIGINAL_CHANGE_SET",input.assignmentId(),hash,rubric,
                        "UNKNOWN",supersedes,List.of(),false,null));
    }
    @Test void wholeSetFirstRatingAndCorrectionHaveOneWinnerAndKeepOneTaskSample() throws Exception {
        var input = input(); var hash = setHash(input); var roots = race(input,setRequest(input,hash,0,null,null)); assertThat(roots).hasSize(1);
        var before = sql().queryForMap("SELECT * FROM evaluation_feedback WHERE id=?",roots.getFirst());
        assertThat(before.get("score").toString()).isEqualTo("0.000000"); assertThat(before.get("original_evidence_id").toString()).isEqualTo(input.assignmentId());
        assertThat(before.get("original_evidence_hash")).isEqualTo(hash);
        var next = race(input,setRequest(input,hash,4,roots.getFirst().toString(),"核对完整集合后更正")); assertThat(next).hasSize(1);
        assertThat(sql().queryForMap("SELECT * FROM evaluation_feedback WHERE id=?",roots.getFirst())).isEqualTo(before);
        var view = service(0).append(input,hash,setRequest(input,hash,2,next.getFirst().toString(),"保留完整集合再核对"),501L);
        assertThat(view.onlineQuality().originalEvidenceType()).isEqualTo("ORIGINAL_CHANGE_SET");
        assertThat(view.onlineQuality().originalEvidenceId()).isEqualTo(input.assignmentId()); assertThat(view.onlineQuality().originalEvidenceHash()).isEqualTo(hash);
        assertThat(view.factsJson().length()).isLessThanOrEqualTo(2000); assertThat(view.score()).isEqualByComparingTo("0.5");
        assertThat(sql().queryForObject("SELECT COUNT(DISTINCT online_assignment_id) FROM evaluation_feedback WHERE online_assignment_id=?",Long.class,input.assignmentId())).isEqualTo(1);
        assertThat(sql().queryForObject("SELECT COUNT(*) FROM evaluation_metric WHERE online_assignment_id=?",Long.class,input.assignmentId())).isZero();
    }
    @Test void setCorrectionsCannotChangeMembershipOrReplaceAnExistingSingleOriginalRating() {
        var input = input(); var hash = setHash(input); var root = service(0).append(input,hash,setRequest(input,hash,0,null,null),501L);
        var before = sql().queryForMap("SELECT * FROM evaluation_feedback WHERE id=?",root.id());
        var changed = "a".repeat(64);
        assertThatThrownBy(() -> service(1).append(input,changed,setRequest(input,changed,4,root.id().toString(),"改集合"),502L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service(1).append(input,changed,setRequest(input,hash,4,root.id().toString(),"伪造身份"),502L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service(1).append(input,hash,setRequest(input,hash,4,null,"另造首评"),502L)).isInstanceOf(BusinessException.class);
        assertThat(sql().queryForMap("SELECT * FROM evaluation_feedback WHERE id=?",root.id())).isEqualTo(before);
        var oldInput = input(); var old = service(0).append(oldInput,"f".repeat(64),request(oldInput,0,"NON_BLINDED",null,null),501L);
        var oldBefore = sql().queryForMap("SELECT * FROM evaluation_feedback WHERE id=?",old.id()); var newHash = setHash(oldInput);
        assertThatThrownBy(() -> service(1).append(oldInput,newHash,setRequest(oldInput,newHash,4,old.id().toString(),"换命名空间"),502L)).isInstanceOf(BusinessException.class);
        assertThat(sql().queryForMap("SELECT * FROM evaluation_feedback WHERE id=?",old.id())).isEqualTo(oldBefore);
    }
}
