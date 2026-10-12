package com.agentdoc.evaluation.service;

import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.convertor.OnlineAssignmentConvertor;
import com.agentdoc.evaluation.convertor.OnlineExperimentConvertor;
import com.agentdoc.evaluation.mapper.*;
import com.agentdoc.evaluation.pojo.entity.*;
import com.agentdoc.evaluation.pojo.param.OnlineExperimentSearchParam;
import com.agentdoc.evaluation.pojo.param.OnlineAssignmentSearchParam;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.stream.LongStream;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 百万 assignment 规模：实际应用服务、MyBatis、行锁和转换，授权 RPC 在边界替身中。 */
@EnabledIfEnvironmentVariable(named = "P603_SCALE", matches = "1")
class OnlineReportScaleMySqlTest {
    @Configuration
    static class Config extends OnlineReportReviewMySqlTest.Config {
        @Override @Bean SqlSessionFactory factory(DriverManagerDataSource source) throws Exception {
            var built = super.factory(source); var pagination = new MybatisPlusInterceptor();
            pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL)); built.getConfiguration().addInterceptor(pagination); return built;
        }
        @Bean OnlineReportSnapshotService snapshots(SqlSessionTemplate session) {
            return new OnlineReportSnapshotService(session.getMapper(OnlineAssignmentMapper.class), session.getMapper(OnlineEvaluationAttemptMapper.class),
                    session.getMapper(EvaluationResultMapper.class), session.getMapper(EvaluationMetricMapper.class), session.getMapper(EvaluationEvidenceReferenceMapper.class),
                    session.getMapper(EvaluationMetricEvidenceMapper.class), session.getMapper(EvaluationFeedbackMapper.class), session.getMapper(OnlineExperimentEventMapper.class));
        }
    }
    private static <T> T construct(Class<T> type, Map<Class<?>, Object> actual) throws Exception {
        var constructor = type.getConstructors()[0]; var arguments = Arrays.stream(constructor.getParameterTypes()).map(argument -> actual.getOrDefault(argument, mock(argument))).toArray();
        return type.cast(constructor.newInstance(arguments));
    }
    private static Map<String, Object> measure(Runnable call) {
        var samples = new ArrayList<Double>(); for (int i = 0; i < 10; i++) { call.run(); }
        for (int i = 0; i < 100; i++) { long start = System.nanoTime(); call.run(); samples.add((System.nanoTime() - start) / 1_000_000.0); }
        samples.sort(Double::compareTo);
        return Map.of("requests", 100, "p50Millis", samples.get(49), "p95Millis", samples.get(94), "maxMillis", samples.get(99), "failures", 0);
    }
    @Test void millionAssignmentsKeepReadP95UnderOneSecondAndRecalculationIsMeasuredSeparately() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(Config.class); var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserIdOrException).thenReturn(502L); auth.when(AuthUtils::getUserId).thenReturn(502L);
            var sql = new JdbcTemplate(context.getBean(DriverManagerDataSource.class)); var session = context.getBean(SqlSessionTemplate.class);
            long base = 8_000_000_000_000L; var docs = LongStream.rangeClosed(1, 1000).mapToObj(Long::toString).toList();
            var payload = Map.of("experimentId", Long.toString(base), "spaceId", "777", "agentId", "20", "documentIds", docs,
                    "bucketProtocol", Map.of("seed", "00000000000000000000000000000000", "weight", 5000),
                    "analysisPlan", Map.of("mode", "DESCRIPTIVE", "humanQualityRequired", false), "ruleBindings", List.of());
            String manifest = OnlineProtocolUtils.canonical("online.manifest", payload);
            for (int i = 0; i < 100; i++) {
                var values = new LinkedHashMap<String, Object>(payload); values.put("experimentId", Long.toString(base + i));
                String frozen = OnlineProtocolUtils.canonical("online.manifest", values);
                sql.update("INSERT INTO online_experiment(id,space_id,agent_id,name,client_request_key,request_hash,manifest_schema_version,manifest_json,manifest_hash,status,authorized_token_budget,max_task_count,created_by,accepted_sequence,assigned_task_count) VALUES(?,777,20,'规模历史',?,?,2,?,?,'STOPPED',10000000,10000,999,10000,10000)", base + i, "scale-" + i, "a".repeat(64), frozen, StableSnapshotUtils.sha256Utf8(frozen));
            }
            sql.execute("CREATE TABLE p603_scale_sequence(n INT PRIMARY KEY)");
            sql.execute("INSERT INTO p603_scale_sequence SELECT a.n + b.n*10 + c.n*100 + d.n*1000 + 1 FROM (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a CROSS JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b CROSS JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c CROSS JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) d");
            long seedStart = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                long offset = base + 1_000_000 + i * 10000L;
                sql.update("INSERT INTO online_assignment(id,experiment_id,space_id,agent_id,task_id,document_id,created_by,client_request_key,request_hash,input_snapshot_schema_version,input_snapshot_hash,bucket,variant,config_schema_version,config_hash,binding_schema_version,binding_hash,binding_json,reserved_token_budget,execution_id,accepted_sequence,created_at) SELECT ?+n,?,777,20,?+n,MOD(n-1,1000)+1,501,CONCAT('scale-',?, '-',n),REPEAT('a',64),1,REPEAT('b',64),MOD(n,10000),IF(MOD(n,2)=0,'BASELINE','CANDIDATE'),2,REPEAT('c',64),2,REPEAT('d',64),JSON_OBJECT('fixture','representative_private_binding','padding',REPEAT('x',1800)),100,?+n,n,'2026-10-12 10:00:00' FROM p603_scale_sequence", offset, base + i, offset + 2_000_000, i, offset + 4_000_000);
            }
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM online_assignment WHERE space_id=777", Long.class)).isEqualTo(1_000_000);
            var experimentMapper = session.getMapper(OnlineExperimentMapper.class); var assignmentMapper = session.getMapper(OnlineAssignmentMapper.class);
            var actual = new HashMap<Class<?>, Object>(); actual.put(OnlineExperimentMapper.class, experimentMapper); actual.put(OnlineAssignmentMapper.class, assignmentMapper);
            actual.put(OnlineExperimentConvertor.class, new OnlineExperimentConvertor()); actual.put(OnlineAssignmentConvertor.class, new OnlineAssignmentConvertor());
            actual.put(OnlineReviewExposureService.class, context.getBean(OnlineReviewExposureService.class)); actual.put(SpaceAccessService.class, mock(SpaceAccessService.class));
            var experiments = construct(OnlineExperimentService.class, actual);
            actual.put(OnlineExperimentReportMapper.class, session.getMapper(OnlineExperimentReportMapper.class));
            var reports = construct(OnlineExperimentReportService.class, actual);
            var experiment = experimentMapper.selectById(base); var snapshots = context.getBean(OnlineReportSnapshotService.class);
            long aggregateStart = System.nanoTime(); var snapshot = snapshots.load(experiment, 10000, LocalDateTime.of(2026, 10, 12, 23, 0));
            var calculation = OnlineReportCalculator.calculate(experiment, OnlineProtocolUtils.object(manifest).path("payload"), 10000, LocalDateTime.of(2026, 10, 12, 23, 0), snapshot, List.of(), List.of(), List.of("UNKNOWN_TASK_SOURCE", "UNKNOWN_EXECUTION_SOURCE"));
            String input = OnlineProtocolUtils.canonical("online.report-input", Map.of("facts", snapshot.frozen()));
            var report = context.getBean(OnlineReportPersistenceService.class).save(base, 502L, "scale-report", "e".repeat(64), experiment.getManifestHash(), 10000, LocalDateTime.of(2026, 10, 12, 23, 0), input, OnlineProtocolUtils.canonical("online.report", calculation));
            double aggregateMillis = (System.nanoTime() - aggregateStart) / 1_000_000.0;
            assertThat(calculation.path("groups").path("BASELINE").path("assignedTaskCount").asInt()).isEqualTo(5000);
            assertThat(calculation.path("groups").path("CANDIDATE").path("primaryMissingDocumentCount").asInt()).isEqualTo(500);
            assertThat(calculation.path("decisionEligibility").path("comparisonAllowed").asBoolean()).isFalse();
            var search = new OnlineExperimentSearchParam(); search.setSpaceId("777"); search.setPageSize(10);
            var page = new OnlineAssignmentSearchParam(); page.setPageNum(100); page.setPageSize(100);
            var result = new LinkedHashMap<String, Object>(); result.put("assignmentRows", 1_000_000); result.put("experiments", 100); result.put("assignmentsPerExperiment", 10000);
            result.put("privateBindingBytes", sql.queryForObject("SELECT OCTET_LENGTH(binding_json) FROM online_assignment WHERE space_id=777 LIMIT 1", Integer.class));
            result.put("seedMillis", (System.nanoTime() - seedStart) / 1_000_000.0); result.put("aggregateAndFreezeMillis", aggregateMillis); result.put("frozenInputBytes", input.getBytes(StandardCharsets.UTF_8).length);
            result.put("experimentList", measure(() -> assertThat(experiments.search(search).total()).isEqualTo(100)));
            result.put("assignmentPage", measure(() -> assertThat(experiments.assignments(Long.toString(base), page).total()).isEqualTo(10000)));
            result.put("reportSummary", measure(() -> assertThat(reports.summary(Long.toString(base), report.getRevision()).state()).isEqualTo("SUMMARY_VERIFIED")));
            result.put("explainParticipation", sql.queryForList("EXPLAIN SELECT experiment_id,COUNT(DISTINCT document_id) FROM online_assignment WHERE binding_schema_version=2 AND experiment_id IN (?,?) GROUP BY experiment_id", base, base + 1));
            result.put("explainAssignments", sql.queryForList("EXPLAIN SELECT * FROM online_assignment WHERE experiment_id=? AND space_id=777 AND binding_schema_version=2 ORDER BY id DESC LIMIT 9900,100", base));
            result.put("boundaries", List.of("single_process_single_concurrency", "actual_service_and_mysql_with_permission_rpc_mock", "representative_private_binding_is_not_execution_proof", "unavailable_execution_and_metric_facts_remain_unknown", "aggregate_timer_excludes_cross_service_network", "no_http_ui_or_model_latency_claim"));
            String output = System.getenv("P603_SCALE_RESULT"); assertThat(output).isNotBlank(); Files.writeString(Path.of(output), JsonUtils.toJson(result));
            for (String api : List.of("experimentList", "assignmentPage", "reportSummary")) {
                assertThat((Double) ((Map<?, ?>) result.get(api)).get("p95Millis")).as(api).isLessThanOrEqualTo(1000);
            }
        }
    }
}
