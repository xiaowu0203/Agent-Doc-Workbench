package com.agentdoc.evaluation.service;

import com.agentdoc.common.feign.vo.AgentOnlineOriginalTextVO;
import com.agentdoc.common.utils.OnlineOriginalTextUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.handler.CommonMetaObjectHandler;
import com.agentdoc.evaluation.evaluator.DeterministicEvaluationOutcome;
import com.agentdoc.evaluation.evaluator.OnlineOriginalTextEvaluator;
import com.agentdoc.evaluation.enums.EvaluationResultStatus;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.mapper.OnlineEvaluationAttemptMapper;
import com.agentdoc.evaluation.mapper.EvaluationResultMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricMapper;
import com.agentdoc.evaluation.mapper.EvaluationEvidenceReferenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricEvidenceMapper;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.agentdoc.evaluation.metric.StandardMetricValue;
import com.agentdoc.evaluation.metric.StandardMetricOutput;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashSet;
import java.util.stream.IntStream;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import static org.assertj.core.api.Assertions.*;
import static com.agentdoc.evaluation.enums.EvaluationMetricDirection.HIGHER_IS_BETTER;
import static com.agentdoc.evaluation.enums.EvaluationMetricSource.EVALUATOR;

@EnabledIfEnvironmentVariable(named = "P603_EVALUATION_MYSQL_URL", matches = ".+")
class OnlineEvaluationPersistenceMySqlTest {
    private static AnnotationConfigApplicationContext first, second;
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean DriverManagerDataSource source() {
            String url = System.getenv("P603_EVALUATION_MYSQL_URL");
            if (!url.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/agentdoc_p603_evaluation_[a-f0-9]{12}\\?.*")) { throw new IllegalArgumentException("仅允许本批拥有的隔离数据库"); }
            return new DriverManagerDataSource(url,System.getenv("P603_MYSQL_USER"),System.getenv("P603_MYSQL_PASSWORD"));
        }
        @Bean PlatformTransactionManager transactionManager(DriverManagerDataSource source) { return new DataSourceTransactionManager(source); }
        @Bean SqlSessionFactory factory(DriverManagerDataSource source) throws Exception {
            var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
            var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(config);
            factory.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(new CommonMetaObjectHandler()));
            var built = factory.getObject();
            for (var mapper : List.of(OnlineAssignmentMapper.class,OnlineEvaluationAttemptMapper.class,EvaluationResultMapper.class,
                    EvaluationMetricMapper.class,EvaluationEvidenceReferenceMapper.class,EvaluationMetricEvidenceMapper.class)) { built.getConfiguration().addMapper(mapper); }
            return built;
        }
        @Bean SqlSessionTemplate session(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean OnlineEvaluationPersistenceService service(SqlSessionTemplate session) {
            return new OnlineEvaluationPersistenceService(session.getMapper(OnlineAssignmentMapper.class),session.getMapper(OnlineEvaluationAttemptMapper.class),
                    session.getMapper(EvaluationResultMapper.class),session.getMapper(EvaluationEvidenceReferenceMapper.class),session.getMapper(EvaluationMetricMapper.class),
                    session.getMapper(EvaluationMetricEvidenceMapper.class));
        }
    }
    @BeforeAll static void open() { first = new AnnotationConfigApplicationContext(Config.class); second = new AnnotationConfigApplicationContext(Config.class); }
    @AfterAll static void close() { if(first != null) { first.close(); } if(second != null) { second.close(); } }
    private OnlineEvaluationPersistenceService service(int instance) { return (instance % 2 == 0 ? first : second).getBean(OnlineEvaluationPersistenceService.class); }
    private JdbcTemplate sql() { return new JdbcTemplate(first.getBean(DriverManagerDataSource.class)); }
    private OnlineAssignmentEntity assignment() {
        long id = IdWorker.getId(); var jdbc = sql();
        jdbc.update("INSERT INTO online_assignment(id,experiment_id,space_id,agent_id,task_id,document_id,created_by,client_request_key,request_hash,input_snapshot_schema_version,input_snapshot_hash,bucket,variant,config_schema_version,config_hash,binding_schema_version,binding_hash,reserved_token_budget,execution_id) VALUES(?,?,10,20,?,41,501,?, ?,1,?,1,'BASELINE',2,?,2,?,100,?)",
                id,id+1,id+2,"source-"+id,"a".repeat(64),"b".repeat(64),"c".repeat(64),"d".repeat(64),id+3);
        var session = first.getBean(SqlSessionTemplate.class); return session.getMapper(OnlineAssignmentMapper.class).selectById(id);
    }
    private DeterministicEvaluationOutcome outcome(OnlineAssignmentEntity assignment) {
        var value = new AgentOnlineOriginalTextVO(2,"AVAILABLE",assignment.getExecutionId().toString(),assignment.getTaskId().toString(),assignment.getExecutionId().toString(),
                "10","20",assignment.getExperimentId().toString(),assignment.getId().toString(),assignment.getBindingHash(),StableSnapshotUtils.sha256Utf8("原始文本"),null,"2026-10-10T20:00:00.123","原始文本");
        var original = new AgentOnlineOriginalTextVO(value.schemaVersion(),value.state(),value.evidenceId(),value.taskId(),value.executionId(),value.spaceId(),value.agentId(),value.experimentId(),
                value.assignmentId(),value.bindingHash(),value.contentHash(),OnlineOriginalTextUtils.identityHash(value),value.capturedAt(),value.originalText());
        return new OnlineOriginalTextEvaluator().evaluate("{}","{\"assertions\":[{\"field\":\"originalText\",\"operator\":\"EXACT\",\"value\":\"不匹配\"}]}",original);
    }
    private long count(String table, long assignmentId) { return sql().queryForObject("SELECT COUNT(*) FROM "+table+" WHERE online_assignment_id=?",Long.class,assignmentId); }
    @Test void concurrentRetriesAcrossIndependentTransactionsPersistOneZeroResultAndCompleteEvidenceLinks() throws Exception {
        var assignment = assignment(); var output = outcome(assignment);
        try(var pool = Executors.newFixedThreadPool(8)) {
            var jobs = IntStream.range(0,8).mapToObj(index -> (Callable<String>) () -> service(index).append(assignment,81L,"quality","same-key",
                    "e".repeat(64),"f".repeat(64),501L,LocalDateTime.now(),output).id()).toList();
            var ids = pool.invokeAll(jobs); var unique = new HashSet<String>(); for(var id : ids) { unique.add(id.get()); } assertThat(unique).hasSize(1);
        }
        assertThat(count("evaluation_result",assignment.getId())).isEqualTo(1); assertThat(count("evaluation_metric",assignment.getId())).isEqualTo(1);
        assertThat(count("evaluation_evidence_reference",assignment.getId())).isEqualTo(1);
        assertThat(sql().queryForObject("SELECT numeric_value FROM evaluation_metric WHERE online_assignment_id=?",BigDecimal.class,assignment.getId())).isZero();
        assertThat(sql().queryForObject("SELECT COUNT(*) FROM evaluation_metric_evidence l JOIN evaluation_metric m ON m.id=l.metric_id WHERE m.online_assignment_id=?",Long.class,assignment.getId())).isEqualTo(1);
        assertThatThrownBy(() -> service(0).append(assignment,81L,"quality","same-key","a".repeat(64),"f".repeat(64),501L,LocalDateTime.now(),output)).isInstanceOf(BusinessException.class);
    }
    @Test void invalidMetricRollsBackAttemptResultAndEvidenceAtomically() {
        var assignment = assignment(); var valid = outcome(assignment);
        var bad = new StandardMetricOutput(StandardMetricValue.number("evaluation.online-original-text.pass-ratio",new BigDecimal("2"),"ratio",HIGHER_IS_BETTER,EVALUATOR),List.of("original-text"));
        var output = new DeterministicEvaluationOutcome(EvaluationResultStatus.FAILED,"BAD_TEST_METRIC","{}",List.of(bad),valid.evidence());
        assertThatThrownBy(() -> service(0).append(assignment,81L,"quality","rollback","e".repeat(64),"f".repeat(64),501L,LocalDateTime.now(),output)).isInstanceOf(BusinessException.class);
        assertThat(service(0).prior(assignment.getId(),81L,"rollback")).isNull(); assertThat(count("evaluation_result",assignment.getId())).isZero();
        assertThat(count("evaluation_evidence_reference",assignment.getId())).isZero();
    }
    @Test void missingEvidencePersistsNoMetricAndDifferentKeyAppendsNextAttempt() {
        var assignment = assignment(); var skipped = new OnlineOriginalTextEvaluator().evaluate("{}","{\"assertions\":[{\"field\":\"originalText\",\"operator\":\"EXACT\",\"value\":\"x\"}]}",null);
        var firstResult = service(0).append(assignment,81L,"quality","missing","e".repeat(64),"f".repeat(64),501L,LocalDateTime.now(),skipped);
        assertThat(firstResult.status()).isEqualTo("SKIPPED"); assertThat(count("evaluation_metric",assignment.getId())).isZero();
        var next = service(1).append(assignment,81L,"quality","later","1".repeat(64),"f".repeat(64),501L,LocalDateTime.now(),outcome(assignment));
        assertThat(next.attemptNo()).isEqualTo(2); assertThat(service(0).reuse(service(0).prior(assignment.getId(),81L,"missing"),"e".repeat(64)).status()).isEqualTo("SKIPPED");
    }
    @Test void changedExecutionIdentityCannotAppend() {
        var assignment = assignment(); sql().update("UPDATE online_assignment SET execution_id=execution_id+1 WHERE id=?",assignment.getId());
        assertThatThrownBy(() -> service(0).append(assignment,81L,"quality","forged","e".repeat(64),"f".repeat(64),501L,LocalDateTime.now(),outcome(assignment))).isInstanceOf(BusinessException.class);
        assertThat(count("evaluation_result",assignment.getId())).isZero();
    }
}
