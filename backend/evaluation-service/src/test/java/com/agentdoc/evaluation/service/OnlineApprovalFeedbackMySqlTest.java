package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.vo.ChangeRequestFeedbackSnapshotVO;
import com.agentdoc.common.feign.vo.OnlineApprovalSourceVO;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.utils.OnlineApprovalDifferenceUtils;
import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
import com.agentdoc.evaluation.mapper.EvaluationCaseRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationEvidenceReferenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationFeedbackMapper;
import com.agentdoc.evaluation.mapper.EvaluationResultMapper;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 沿用严格限制库名的隔离 MySQL 配置，验证真实字段/唯一键和不可变审批历史。 */
@EnabledIfEnvironmentVariable(named = "P603_EVALUATION_MYSQL_URL", matches = ".+")
class OnlineApprovalFeedbackMySqlTest {
    private static AnnotationConfigApplicationContext context;
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config extends OnlineEvaluationPersistenceMySqlTest.Config {
        @Override @Bean SqlSessionFactory factory(DriverManagerDataSource source) throws Exception {
            var factory = super.factory(source);
            for (var mapper : List.of(EvaluationFeedbackMapper.class, EvaluationCaseRunMapper.class, EvaluationCaseAttemptMapper.class)) {
                factory.getConfiguration().addMapper(mapper);
            }
            return factory;
        }
        @Bean TaskFeign tasks() { return mock(TaskFeign.class); }
        @Bean SpaceAccessService permissions() { return mock(SpaceAccessService.class); }
        @Bean EvaluationFeedbackService feedback(SqlSessionTemplate session, TaskFeign tasks, SpaceAccessService permissions) {
            return new EvaluationFeedbackService(session.getMapper(EvaluationFeedbackMapper.class), session.getMapper(EvaluationCaseRunMapper.class),
                    session.getMapper(EvaluationCaseAttemptMapper.class), session.getMapper(EvaluationEvidenceReferenceMapper.class),
                    session.getMapper(EvaluationResultMapper.class), permissions, tasks, session.getMapper(OnlineAssignmentMapper.class));
        }
    }
    @BeforeAll static void open() { context = new AnnotationConfigApplicationContext(Config.class); }
    @AfterAll static void close() { if (context != null) { context.close(); } }
    @BeforeEach void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg", "RS256").subject("501").claim("scope", "user").build()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private JdbcTemplate sql() { return new JdbcTemplate(context.getBean(DriverManagerDataSource.class)); }
    private EvaluationFeedbackService service() { return context.getBean(EvaluationFeedbackService.class); }
    private OnlineAssignmentEntity assignment() {
        long id = IdWorker.getId();
        sql().update("INSERT INTO online_assignment(id,experiment_id,space_id,agent_id,task_id,document_id,created_by,client_request_key,request_hash,input_snapshot_schema_version,input_snapshot_hash,bucket,variant,config_schema_version,config_hash,binding_schema_version,binding_hash,reserved_token_budget,execution_id) VALUES(?,?,10,20,?,41,501,?,?,1,?,1,'BASELINE',2,?,2,?,100,?)",
                id, id + 1, id + 2, "approval-" + id, "a".repeat(64), "b".repeat(64), "c".repeat(64), "d".repeat(64), id + 3);
        return context.getBean(SqlSessionTemplate.class).getMapper(OnlineAssignmentMapper.class).selectById(id);
    }
    private ChangeRequestFeedbackSnapshotVO snapshot(OnlineAssignmentEntity assignment, boolean merged) {
        long requestId = assignment.getId() + 4;
        var input = new OnlineOriginalInputVO(assignment.getTaskId().toString(), assignment.getExecutionId().toString(), "10", "20",
                assignment.getExperimentId().toString(), assignment.getId().toString(), assignment.getBindingHash(), "41", "FORMAL", 3L, "b".repeat(64), true);
        var source = new OnlineApprovalSourceVO(input, Long.toString(requestId), merged ? "MERGED" : "APPROVED", "c".repeat(64), "e".repeat(64), 3L, null,
                merged ? 4L : null, merged ? "501" : null, merged ? "2026-10-10T20:00" : null, merged ? null : "NOT_MERGED");
        var difference = merged ? OnlineApprovalDifferenceUtils.signed(source, null, "a".repeat(64), "f".repeat(64), 1, 1, 1, new BigDecimal("0.25"))
                : OnlineApprovalDifferenceUtils.signed(source, "NOT_MERGED", null, null, null, null, null, null);
        var unsigned = new ChangeRequestFeedbackSnapshotVO(requestId, 10L, assignment.getTaskId(), assignment.getExecutionId(), source.status(), "EDITED", "a".repeat(64),
                501L, LocalDateTime.of(2026, 10, 10, 19, 30), 1, null, difference);
        return new ChangeRequestFeedbackSnapshotVO(unsigned.changeRequestId(), unsigned.spaceId(), unsigned.taskId(), unsigned.executionId(), unsigned.status(),
                unsigned.resolutionType(), unsigned.reviewCommentHash(), unsigned.reviewedBy(), unsigned.reviewedAt(), unsigned.revisionNo(),
                OnlineApprovalDifferenceUtils.feedbackHash(unsigned), difference);
    }
    private void upstream(ChangeRequestFeedbackSnapshotVO snapshot) {
        when(context.getBean(TaskFeign.class).getChangeRequestFeedbackSnapshot(snapshot.changeRequestId())).thenReturn(Result.ok(snapshot));
    }
    @Test void repeatedMergeImportUsesRealUniqueIdentityAndDoesNotCreateQualityMetrics() {
        var assignment = assignment(); var snapshot = snapshot(assignment, true); upstream(snapshot);
        var first = service().importChangeRequest(snapshot.changeRequestId());
        assertThat(service().importChangeRequest(snapshot.changeRequestId()).id()).isEqualTo(first.id());
        var row = context.getBean(SqlSessionTemplate.class).getMapper(EvaluationFeedbackMapper.class).selectById(first.id());
        assertThat(row.getOnlineAssignmentId()).isEqualTo(assignment.getId()); assertThat(row.getRunId()).isNull(); assertThat(row.getCaseRunId()).isNull();
        assertThat(row.getScore()).isNull(); assertThat(row.getRubricScore()).isNull(); assertThat(row.getBlindness()).isNull();
        assertThat(row.getFactsJson()).contains("originalContentHash", "mergedContentHash", "0.25");
        assertThat(sql().queryForObject("SELECT COUNT(*) FROM evaluation_feedback WHERE online_assignment_id=?", Long.class, assignment.getId())).isEqualTo(1);
        assertThat(sql().queryForObject("SELECT COUNT(*) FROM evaluation_metric WHERE online_assignment_id=?", Long.class, assignment.getId())).isZero();
        assertThat(sql().queryForObject("SELECT COUNT(*) FROM online_evaluation_attempt WHERE assignment_id=?", Long.class, assignment.getId())).isZero();
    }
    @Test void laterMergeAppendsANewProofAndPreservesOriginalApprovalImport() {
        var assignment = assignment(); var approved = snapshot(assignment, false); upstream(approved);
        var first = service().importChangeRequest(approved.changeRequestId()); var oldFacts = first.factsJson();
        var merged = snapshot(assignment, true); upstream(merged); var second = service().importChangeRequest(merged.changeRequestId());
        assertThat(second.id()).isNotEqualTo(first.id()); assertThat(second.sourceHash()).isNotEqualTo(first.sourceHash());
        assertThat(oldFacts).contains("NOT_MERGED").contains("\"humanModificationRatio\":null");
        assertThat(context.getBean(SqlSessionTemplate.class).getMapper(EvaluationFeedbackMapper.class).selectById(first.id()).getFactsJson()).isEqualTo(oldFacts);
        assertThat(sql().queryForObject("SELECT COUNT(*) FROM evaluation_feedback WHERE online_assignment_id=?", Long.class, assignment.getId())).isEqualTo(2);
    }
    @Test void actualBindingDriftCannotImportAValidButStaleEnvelope() {
        var assignment = assignment(); var snapshot = snapshot(assignment, true); upstream(snapshot);
        sql().update("UPDATE online_assignment SET binding_hash=? WHERE id=?", "f".repeat(64), assignment.getId());
        assertThatThrownBy(() -> service().importChangeRequest(snapshot.changeRequestId())).isInstanceOf(BusinessException.class);
        assertThat(sql().queryForObject("SELECT COUNT(*) FROM evaluation_feedback WHERE online_assignment_id=?", Long.class, assignment.getId())).isZero();
    }
}
