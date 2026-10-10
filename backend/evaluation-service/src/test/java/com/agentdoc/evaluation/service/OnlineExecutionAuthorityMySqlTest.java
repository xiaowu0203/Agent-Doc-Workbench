package com.agentdoc.evaluation.service;

import com.agentdoc.common.feign.dto.OnlineAssignmentRequestDTO;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.common.feign.OnlineTaskFeign;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import com.agentdoc.common.feign.vo.OnlineSlotPermitVO;
import com.agentdoc.common.feign.vo.OnlineTaskFactVO;
import com.agentdoc.common.handler.CommonMetaObjectHandler;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.concurrent.LinkedBlockingQueue;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.mapper.OnlineExecutionSlotMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentEventMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentActionRequestMapper;
import com.agentdoc.evaluation.mapper.OnlinePreflightProofMapper;
import com.agentdoc.evaluation.mapper.OnlineTaskRouteMapper;
import com.agentdoc.evaluation.pojo.entity.OnlinePreflightProofEntity;
import com.agentdoc.evaluation.enums.OnlineExperimentAction;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineTaskRouteEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.LongStream;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 两个独立服务容器/事务管理器共享专用MySQL；不连接业务库，不调用模型。 */
@EnabledIfEnvironmentVariable(named = "P602_KERNEL_MYSQL_URL", matches = ".+")
class OnlineExecutionAuthorityMySqlTest {
    private static AnnotationConfigApplicationContext first;
    private static AnnotationConfigApplicationContext second;
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean DriverManagerDataSource source() {
            String url = System.getenv("P602_KERNEL_MYSQL_URL");
            if (!url.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/agentdoc_p602_kernel_[a-f0-9]{12}\\?.*")) {
                throw new IllegalArgumentException("仅允许本批拥有的隔离数据库");
            }
            return new DriverManagerDataSource(url, System.getenv("P602_MYSQL_USER"), System.getenv("P602_MYSQL_PASSWORD"));
        }
        @Bean PlatformTransactionManager transactionManager(DriverManagerDataSource source) { return new DataSourceTransactionManager(source); }
        @Bean SqlSessionFactory factory(DriverManagerDataSource source) throws Exception {
            var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
            var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(configuration);
            factory.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(new CommonMetaObjectHandler()));
            var built = factory.getObject();
            built.getConfiguration().addMapper(OnlineExperimentMapper.class);
            built.getConfiguration().addMapper(OnlineAssignmentMapper.class);
            built.getConfiguration().addMapper(OnlineExecutionSlotMapper.class);
            built.getConfiguration().addMapper(OnlineExperimentEventMapper.class);
            built.getConfiguration().addMapper(OnlineExperimentActionRequestMapper.class);
            built.getConfiguration().addMapper(OnlinePreflightProofMapper.class);
            built.getConfiguration().addMapper(OnlineTaskRouteMapper.class);
            return built;
        }
        @Bean SqlSessionTemplate session(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean OnlineExperimentMapper experiments(SqlSessionTemplate session) { return session.getMapper(OnlineExperimentMapper.class); }
        @Bean OnlineAssignmentMapper assignments(SqlSessionTemplate session) { return session.getMapper(OnlineAssignmentMapper.class); }
        @Bean OnlineExecutionSlotMapper slots(SqlSessionTemplate session) { return session.getMapper(OnlineExecutionSlotMapper.class); }
        @Bean OnlineExperimentEventMapper events(SqlSessionTemplate session) { return session.getMapper(OnlineExperimentEventMapper.class); }
        @Bean OnlineExperimentActionRequestMapper actions(SqlSessionTemplate session) { return session.getMapper(OnlineExperimentActionRequestMapper.class); }
        @Bean OnlinePreflightProofMapper proofs(SqlSessionTemplate session) { return session.getMapper(OnlinePreflightProofMapper.class); }
        @Bean OnlineTaskRouteMapper routes(SqlSessionTemplate session) { return session.getMapper(OnlineTaskRouteMapper.class); }
        @Bean OnlineLifecyclePersistenceService lifecycle(OnlineExperimentMapper experiments, OnlineExperimentActionRequestMapper actions,
                OnlinePreflightProofMapper proofs, OnlineExperimentEventMapper events, OnlineExecutionAuthority authority) {
            return new OnlineLifecyclePersistenceService(experiments, actions, proofs, events, authority);
        }
        @Bean OnlineRoutingPersistenceService routing(OnlineTaskRouteMapper routes, OnlineExperimentMapper experiments, OnlineExecutionAuthority authority) {
            return new OnlineRoutingPersistenceService(routes, experiments, authority);
        }
        @Bean OnlineExecutionAuthority authority(OnlineExperimentMapper experiments, OnlineAssignmentMapper assignments,
                OnlineExecutionSlotMapper slots, OnlineExperimentEventMapper events) {
            return new OnlineExecutionAuthority(experiments, assignments, slots, events);
        }
    }
    @BeforeAll static void open() {
        first = new AnnotationConfigApplicationContext(Config.class); second = new AnnotationConfigApplicationContext(Config.class);
    }
    @AfterAll static void close() { if (first != null) { first.close(); } if (second != null) { second.close(); } }
    private OnlineExecutionAuthority authority(int instance) { return (instance % 2 == 0 ? first : second).getBean(OnlineExecutionAuthority.class); }
    private OnlineAssignmentMapper assignments() { return first.getBean(OnlineAssignmentMapper.class); }
    private OnlineExperimentMapper experiments() { return first.getBean(OnlineExperimentMapper.class); }
    private OnlineExperimentEntity experiment(long budget, int maxTasks) {
        long id = IdWorker.getId();
        Map<String, Object> manifest = Map.of("experimentId", Long.toString(id), "spaceId", Long.toString(id), "agentId", "201",
                "documentIds", LongStream.rangeClosed(301, 1300).mapToObj(Long::toString).toList(),
                "bucketProtocol", Map.of("seed", "0123456789abcdef0123456789abcdef", "weight", 5000),
                "budgetPlan", Map.of("perTask", "100", "timeout", 600),
                "baseline", Map.of("id", "601", "schemaVersion", 2, "hash", "a".repeat(64), "nonPromptHash", "b".repeat(64)),
                "candidate", Map.of("id", "602", "schemaVersion", 2, "hash", "c".repeat(64), "nonPromptHash", "b".repeat(64)),
                "dependencyHash", "d".repeat(64), "windowPlan", Map.of("assignmentWindowSeconds", 600, "completionObservationSeconds", 600));
        var entity = new OnlineExperimentEntity(); entity.setId(id); entity.setSpaceId(id); entity.setAgentId(201L); entity.setName("安全内核隔离测试");
        entity.setClientRequestKey("experiment-" + id); entity.setRequestHash("a".repeat(64)); entity.setManifestSchemaVersion(2);
        entity.setManifestJson(OnlineProtocolUtils.canonical("online.manifest", manifest)); entity.setManifestHash(OnlineProtocolUtils.hash("online.manifest", manifest));
        entity.setStatus("ACTIVE"); entity.setActiveSlot(1); entity.setStateVersion(0L); entity.setAcceptedSequence(0L);
        entity.setAuthorizedTokenBudget(budget); entity.setMaxTaskCount(maxTasks); entity.setAssignedTaskCount(0);
        entity.setReservedTokenBudget(0L); entity.setConsumedTokens(BigInteger.ZERO); entity.setCreatedBy(501L); entity.setControlAuthorizedBy(501L);
        entity.setControlExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        entity.setAssignmentDeadline(LocalDateTime.now().plusHours(1)); entity.setBaselineSlotCount(0); entity.setCandidateSlotCount(0); entity.setUnknownTaskCount(0);
        experiments().insert(entity); return entity;
    }
    private OnlineAssignmentRequestDTO request(OnlineExperimentEntity experiment, String document, long budget) {
        String taskId = Long.toString(IdWorker.getId());
        return new OnlineAssignmentRequestDTO(taskId, experiment.getSpaceId().toString(), "201", document, "501", "task-" + taskId,
                "a".repeat(64), "b".repeat(64), "1", "c".repeat(64), Long.toString(budget), 1);
    }
    private OnlineAssignmentEntity assignment(String taskId) {
        return assignments().selectOne(new LambdaQueryWrapper<OnlineAssignmentEntity>().eq(OnlineAssignmentEntity::getTaskId, Long.valueOf(taskId)));
    }
    private OnlineSlotPermitVO claim(OnlineExperimentEntity experiment, OnlineAssignmentRequestDTO request) {
        authority(0).allocate(experiment.getId(), request);
        return authority(0).claim(experiment.getId(), Long.valueOf(request.taskId()), assignment(request.taskId()).getBindingHash());
    }
    private OnlineTaskFactVO fact(OnlineAssignmentRequestDTO request, String status, String tokens, String executionId,
            boolean neverDispatched, String finishedAt) {
        return new OnlineTaskFactVO(request.taskId(), assignment(request.taskId()).getBindingHash(), "TERMINATED", executionId,
                status, tokens, neverDispatched, true, finishedAt);
    }
    private <T> List<T> parallel(List<Callable<T>> jobs) throws Exception {
        try (var executor = Executors.newFixedThreadPool(16)) {
            var futures = executor.invokeAll(jobs, 60, TimeUnit.SECONDS); var results = new ArrayList<T>();
            for (var future : futures) { results.add(future.get(10, TimeUnit.SECONDS)); } return results;
        }
    }

    @Test void concurrentBudgetReservationPausesWithoutRollingBackTheGate() throws Exception {
        var experiment = experiment(100, 100);
        var jobs = new ArrayList<Callable<OnlineExecutionAuthority.Allocation>>();
        for (int index = 0; index < 16; index++) {
            int instance = index; var request = request(experiment, "301", 60);
            jobs.add(() -> authority(instance).allocate(experiment.getId(), request));
        }
        var results = parallel(jobs);
        assertThat(results.stream().filter(value -> value.binding() != null)).hasSize(1);
        var stored = experiments().selectById(experiment.getId());
        assertThat(stored.getReservedTokenBudget()).isEqualTo(60); assertThat(stored.getAssignedTaskCount()).isEqualTo(1);
        assertThat(stored.getStatus()).isEqualTo("PAUSED"); assertThat(stored.getReasonCode()).isEqualTo("ONLINE_BUDGET_EXHAUSTED");
    }

    @Test void stoppingAnEmptyExperimentReleasesSpaceAndStaleStateVersionCannotCloseTheGate() {
        var experiment = experiment(100, 100);
        assertThatThrownBy(() -> authority(0).closeGate(experiment.getId(), 1L, 501L, false, "MANUAL_STOP"))
                .hasMessageContaining("ONLINE_STATE_CONFLICT");
        assertThat(experiments().selectById(experiment.getId()).getStatus()).isEqualTo("ACTIVE");
        authority(1).closeGate(experiment.getId(), 0L, 501L, false, "MANUAL_STOP");
        var stored = experiments().selectById(experiment.getId()); assertThat(stored.getStatus()).isEqualTo("STOPPED");
        assertThat(stored.getActiveSlot()).isNull();
    }

    @Test void normalStopLetsAcceptedWorkBeginAndDeadlineClosurePersists() {
        var experiment = experiment(1000, 100); var request = request(experiment, "301", 20); var permit = claim(experiment, request);
        authority(1).closeGate(experiment.getId(), 0L, 501L, false, "MANUAL_STOP");
        assertThat(authority(0).begin(experiment.getId(), Long.valueOf(request.taskId()), permit.generation(), permit.permitHash()).started()).isTrue();
        var overdue = experiment(1000, 100); overdue.setAssignmentDeadline(LocalDateTime.now().minusMinutes(1)); experiments().updateById(overdue);
        assertThat(authority(0).allocate(overdue.getId(), request(overdue, "301", 20)).reason()).isEqualTo("ONLINE_ASSIGNMENT_DEADLINE");
        assertThat(experiments().selectById(overdue.getId()).getStatus()).isEqualTo("STOPPED");
    }

    @Test void sameFrozenRequestConvergesAcrossInstancesAndRetriesAfterStop() throws Exception {
        var experiment = experiment(1000, 100); var request = request(experiment, "301", 20);
        var jobs = new ArrayList<Callable<OnlineExecutionAuthority.Allocation>>();
        for (int index = 0; index < 16; index++) { int instance = index; jobs.add(() -> authority(instance).allocate(experiment.getId(), request)); }
        var results = parallel(jobs);
        assertThat(results.stream().map(value -> value.binding().assignmentId()).distinct()).hasSize(1);
        authority(1).closeGate(experiment.getId(), 0L, 501L, false, "MANUAL_STOP");
        assertThat(authority(0).allocate(experiment.getId(), request).binding()).isEqualTo(results.getFirst().binding());
        var changed = new OnlineAssignmentRequestDTO(request.taskId(), request.spaceId(), request.agentId(), request.documentId(), request.actorId(),
                request.requestKey(), request.requestHash(), request.inputHash(), "2", request.documentContentHash(), request.tokenBudget(), request.inputSchemaVersion());
        assertThatThrownBy(() -> authority(0).allocate(experiment.getId(), changed)).hasMessageContaining("IDEMPOTENCY_CONFLICT");
        assertThat(experiments().selectById(experiment.getId()).getAssignedTaskCount()).isEqualTo(1);
    }

    @Test void oneSlotPerGroupAndRedeliveryReusesGeneration() throws Exception {
        var experiment = experiment(10000, 100); var requests = new ArrayList<OnlineAssignmentRequestDTO>();
        // 每组至少存在一个文档；分桶本身依实验身份变化，不假定固定文档落在哪一组。
        String baseline = null; String candidate = null;
        for (String document : LongStream.rangeClosed(301, 400).mapToObj(Long::toString).toList()) {
            String variant = OnlineProtocolUtils.variant(OnlineProtocolUtils.bucket(experiment.getId().toString(), document,
                    "0123456789abcdef0123456789abcdef"), 5000);
            if (variant.equals("BASELINE")) { baseline = document; } else { candidate = document; }
        }
        assertThat(baseline).isNotNull(); assertThat(candidate).isNotNull();
        String document = baseline;
        for (int index = 0; index < 8; index++) {
            var request = request(experiment, document, 20); requests.add(request); authority(0).allocate(experiment.getId(), request);
        }
        var jobs = new ArrayList<Callable<OnlineSlotPermitVO>>();
        for (int index = 0; index < requests.size(); index++) {
            int instance = index; var request = requests.get(index);
            jobs.add(() -> { try { return authority(instance).claim(experiment.getId(), Long.valueOf(request.taskId()), assignment(request.taskId()).getBindingHash()); }
                catch (BusinessException busy) { assertThat(busy.getMessage()).contains("ONLINE_SLOT_BUSY"); return null; } });
        }
        var permits = parallel(jobs).stream().filter(Objects::nonNull).toList(); assertThat(permits).hasSize(1);
        var permit = permits.getFirst();
        var repeat = authority(1).claim(experiment.getId(), Long.valueOf(permit.binding().taskId()), permit.bindingHash());
        assertThat(repeat).isEqualTo(permit);
        assertThat(authority(0).begin(experiment.getId(), Long.valueOf(permit.binding().taskId()), permit.generation(), permit.permitHash()).started()).isTrue();
        var other = request(experiment, candidate, 20);
        var otherPermit = claim(experiment, other); authority(1).begin(experiment.getId(), Long.valueOf(other.taskId()), otherPermit.generation(), otherPermit.permitHash());
        var stored = experiments().selectById(experiment.getId()); assertThat(stored.getBaselineSlotCount()).isEqualTo(1); assertThat(stored.getCandidateSlotCount()).isEqualTo(1);
    }

    @Test void cancellationAndUnknownCostHaveDifferentReleaseBoundaries() {
        var experiment = experiment(1000, 100); var request = request(experiment, "301", 100); var permit = claim(experiment, request);
        long taskId = Long.parseLong(request.taskId()); authority(0).begin(experiment.getId(), taskId, permit.generation(), permit.permitHash());
        authority(1).closeGate(experiment.getId(), 0L, 501L, true, "MANUAL_EMERGENCY");
        authority(0).reconcile(experiment.getId(), taskId, fact(request, null, null, "701", false, null), fact(request, "WORKING", null, "701", false, null));
        assertThat(assignment(request.taskId()).getSlotStatus()).isEqualTo("ACQUIRED");
        assertThat(assignment(request.taskId()).getSettlementStatus()).isEqualTo("RESERVED");
        String finished = LocalDateTime.now().toString();
        authority(1).reconcile(experiment.getId(), taskId, fact(request, null, null, "701", false, null), fact(request, "CANCELED", null, "701", false, finished));
        assertThat(assignment(request.taskId()).getSlotStatus()).isEqualTo("RELEASED");
        assertThat(assignment(request.taskId()).getSettlementStatus()).isEqualTo("UNKNOWN");
        assertThat(experiments().selectById(experiment.getId()).getReservedTokenBudget()).isEqualTo(100);
        assertThat(experiments().selectById(experiment.getId()).getActiveSlot()).isEqualTo(1);
        for (int index = 0; index < 2; index++) {
            authority(index).reconcile(experiment.getId(), taskId, fact(request, null, "7", "701", false, null), fact(request, "CANCELED", null, "701", false, finished));
        }
        var stored = experiments().selectById(experiment.getId()); assertThat(stored.getConsumedTokens()).isEqualTo(BigInteger.valueOf(7));
        assertThat(stored.getReservedTokenBudget()).isZero(); assertThat(stored.getStatus()).isEqualTo("STOPPED"); assertThat(stored.getActiveSlot()).isNull();
        assertThatThrownBy(() -> authority(0).begin(experiment.getId(), taskId, permit.generation(), permit.permitHash())).hasMessageContaining("ONLINE_SLOT_INVALID");
    }

    @Test void emergencyStopsUnstartedPermitAndOnlyAuthoritativeNonDispatchCanSettleZero() {
        var experiment = experiment(1000, 100); var request = request(experiment, "301", 100); var permit = claim(experiment, request);
        long taskId = Long.parseLong(request.taskId()); authority(1).closeGate(experiment.getId(), 0L, 501L, true, "MANUAL_EMERGENCY");
        assertThatThrownBy(() -> authority(0).begin(experiment.getId(), taskId, permit.generation(), permit.permitHash())).hasMessageContaining("ONLINE_GATE_CLOSED");
        authority(0).reconcile(experiment.getId(), taskId, null, fact(request, "ABSENT", null, null, false, null));
        assertThat(experiments().selectById(experiment.getId()).getReservedTokenBudget()).isEqualTo(100);
        authority(1).reconcile(experiment.getId(), taskId, fact(request, null, null, null, true, null), fact(request, "ABSENT", null, null, false, null));
        assertThat(assignment(request.taskId()).getConsumedTokens()).isEqualTo(BigInteger.ZERO);
        assertThat(experiments().selectById(experiment.getId()).getStatus()).isEqualTo("STOPPED");
    }

    @Test void costAboveLongIsPreservedAndTriggersEmergencyStop() {
        var experiment = experiment(Long.MAX_VALUE, 100); var one = request(experiment, "301", 100); var two = request(experiment, "301", 100);
        authority(0).allocate(experiment.getId(), one); authority(0).allocate(experiment.getId(), two);
        BigInteger cost = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        for (var request : List.of(one, two)) {
            String execution = request == one ? "701" : "702"; String finished = LocalDateTime.now().toString();
            authority(0).reconcile(experiment.getId(), Long.valueOf(request.taskId()), fact(request, null, cost.toString(), execution, false, null),
                    fact(request, "COMPLETED", null, execution, false, finished));
        }
        var stored = experiments().selectById(experiment.getId()); assertThat(stored.getConsumedTokens()).isEqualTo(cost.multiply(BigInteger.TWO));
        assertThat(stored.getReasonCode()).isEqualTo("ONLINE_TOKEN_OVERRUN"); assertThat(stored.getEmergencyStopRequestedAt()).isNotNull();
    }

    @Test void unknownBeyondFiveMinutesPausesButNeverReleasesTheSlot() {
        var experiment = experiment(1000, 100); var request = request(experiment, "301", 100); claim(experiment, request);
        authority(0).reconcile(experiment.getId(), Long.valueOf(request.taskId()), null, null);
        var assignment = assignment(request.taskId()); assignment.setUnresolvedSince(LocalDateTime.now().minusSeconds(301)); assignments().updateById(assignment);
        authority(1).reconcile(experiment.getId(), Long.valueOf(request.taskId()), null, null);
        assertThat(experiments().selectById(experiment.getId()).getStatus()).isEqualTo("PAUSED");
        assertThat(assignment(request.taskId()).getSlotStatus()).isEqualTo("ACQUIRED");
        assertThat(experiments().selectById(experiment.getId()).getReservedTokenBudget()).isEqualTo(100);
    }

    @Test void healthRequiresTwentyActualTerminalsAndSixFailuresAndExcludesCancellation() {
        for (int[] scenario : List.of(new int[]{19, 6}, new int[]{20, 5}, new int[]{20, 6})) {
            var experiment = experiment(100000, 100); String document = "301";
            for (int index = 0; index < scenario[0]; index++) {
                var request = request(experiment, document, 20); authority(0).allocate(experiment.getId(), request);
                String execution = Integer.toString(1000 + index); String finished = LocalDateTime.now().minusMinutes(2).plusSeconds(index).toString();
                authority(1).reconcile(experiment.getId(), Long.valueOf(request.taskId()), fact(request, null, "1", execution, false, null),
                        fact(request, index < scenario[1] ? "FAILED" : "COMPLETED", null, execution, false, finished));
            }
            assertThat(experiments().selectById(experiment.getId()).getStatus()).isEqualTo(scenario[0] == 20 && scenario[1] == 6 ? "PAUSED" : "ACTIVE");
            if (scenario[0] == 19) {
                var canceled = request(experiment, document, 20); authority(0).allocate(experiment.getId(), canceled);
                authority(0).reconcile(experiment.getId(), Long.valueOf(canceled.taskId()), fact(canceled, null, "1", "8000", false, null),
                        fact(canceled, "CANCELED", null, "8000", false, LocalDateTime.now().toString()));
                assertThat(experiments().selectById(experiment.getId()).getStatus()).isEqualTo("ACTIVE");
            }
        }
    }

    @Test void stopRaceNeverAcceptsNewAssignmentAfterCommittedGate() throws Exception {
        var experiment = experiment(100000, 100); var jobs = new ArrayList<Callable<String>>();
        for (int index = 0; index < 32; index++) { int instance = index; var request = request(experiment, "301", 1);
            jobs.add(() -> authority(instance).allocate(experiment.getId(), request).reason()); }
        jobs.add(() -> { authority(1).closeGate(experiment.getId(), 0L, 501L, false, "MANUAL_STOP"); return "STOP"; });
        parallel(jobs);
        var before = experiments().selectById(experiment.getId());
        for (int index = 0; index < 5; index++) {
            assertThat(authority(index).allocate(experiment.getId(), request(experiment, "301", 1)).reason()).isEqualTo("ONLINE_GATE_CLOSED");
        }
        assertThat(experiments().selectById(experiment.getId()).getAssignedTaskCount()).isEqualTo(before.getAssignedTaskCount());
    }

    @Test void forgedFactsRollbackAndReleasedSlotIncrementsGenerationForANewTask() {
        var experiment = experiment(1000, 100); var one = request(experiment, "301", 20); var firstPermit = claim(experiment, one);
        var forged = new OnlineTaskFactVO(one.taskId(), "f".repeat(64), "TERMINATED", "701", "COMPLETED", "1", false, false, LocalDateTime.now().toString());
        assertThatThrownBy(() -> authority(0).reconcile(experiment.getId(), Long.valueOf(one.taskId()), forged, forged)).hasMessageContaining("ONLINE_SLOT_INVALID");
        assertThat(assignment(one.taskId()).getSlotStatus()).isEqualTo("ACQUIRED");
        assertThatThrownBy(() -> authority(0).reconcile(experiment.getId(), Long.valueOf(one.taskId()),
                fact(one, null, "1", "799", false, null), fact(one, "COMPLETED", null, "701", false, LocalDateTime.now().toString())))
                .hasMessageContaining("ONLINE_SLOT_INVALID");
        String finished = LocalDateTime.now().toString(); authority(1).reconcile(experiment.getId(), Long.valueOf(one.taskId()),
                fact(one, null, "1", "701", false, null), fact(one, "COMPLETED", null, "701", false, finished));
        var two = request(experiment, "301", 20); var secondPermit = claim(experiment, two);
        assertThat(secondPermit.generation()).isEqualTo(firstPermit.generation() + 1);
        assertThatThrownBy(() -> authority(0).begin(experiment.getId(), Long.valueOf(one.taskId()), firstPermit.generation(), firstPermit.permitHash()))
                .hasMessageContaining("ONLINE_SLOT_INVALID");
    }

    @Test void reportsHotRowAllocationThroughputAndLatencyForTwoInstances() throws Exception {
        var experiment = experiment(1000000, 1000); var jobs = new ArrayList<Callable<Long>>(); int count = 200;
        for (int index = 0; index < count; index++) { int instance = index; var request = request(experiment, "301", 1);
            jobs.add(() -> { long started = System.nanoTime(); assertThat(authority(instance).allocate(experiment.getId(), request).binding()).isNotNull();
                return System.nanoTime() - started; }); }
        long lockTimeBefore = lockTime();
        long started = System.nanoTime(); var latencies = parallel(jobs).stream().sorted().toList(); long elapsed = System.nanoTime() - started;
        assertThat(experiments().selectById(experiment.getId()).getAssignedTaskCount()).isEqualTo(count);
        System.out.printf("P602_KERNEL_HOT_ROW count=%d instances=2 workers=16 throughput=%.2f/s p95=%.2fms elapsed=%.2fs globalLockTimeDelta=%dms%n",
                count, count * 1e9 / elapsed, latencies.get((int) Math.ceil(count * .95) - 1) / 1e6, elapsed / 1e9, lockTime() - lockTimeBefore);
    }

    @Test void humanActionRetryKeepsOriginalResponseAfterLaterStopAndRejectsKeyConflicts() {
        var experiment = experiment(1000, 100); claim(experiment, request(experiment, "301", 20));
        var state = first.getBean(OnlineLifecyclePersistenceService.class);
        var paused = state.apply(experiment.getId(), 501L, OnlineExperimentAction.PAUSE, "pause-key", "a".repeat(64), experiment.getManifestHash(), 0L, "{\"reason\":\"复核\"}", null, null, null);
        assertThat(paused.status()).isEqualTo("PAUSED");
        var stopped = state.apply(experiment.getId(), 501L, OnlineExperimentAction.STOP, "stop-key", "b".repeat(64), experiment.getManifestHash(), 1L, "{\"reason\":\"结束\"}", null, null, null);
        assertThat(stopped.status()).isEqualTo("STOPPING");
        assertThat(state.apply(experiment.getId(), 501L, OnlineExperimentAction.PAUSE, "pause-key", "a".repeat(64), experiment.getManifestHash(), 0L, "{\"reason\":\"复核\"}", null, null, null)).isEqualTo(paused);
        assertThatThrownBy(() -> state.apply(experiment.getId(), 501L, OnlineExperimentAction.PAUSE, "pause-key", "c".repeat(64), experiment.getManifestHash(), 0L, "{}", null, null, null)).hasMessageContaining("IDEMPOTENCY_CONFLICT");
        assertThat(experiments().selectById(experiment.getId()).getStatus()).isEqualTo("STOPPING");
    }

    @Test void startRequiresStoredActorStateManifestDependencyAndUnexpiredProof() {
        var experiment = experiment(1000, 100); experiment.setStatus("CREATED"); experiment.setActiveSlot(null); experiment.setAssignmentDeadline(null);
        experiments().updateById(experiment);
        experiments().update(null, new LambdaUpdateWrapper<OnlineExperimentEntity>().eq(OnlineExperimentEntity::getId, experiment.getId())
                .set(OnlineExperimentEntity::getActiveSlot, null).set(OnlineExperimentEntity::getAssignmentDeadline, null));
        var proof = new OnlinePreflightProofEntity(); proof.setId(IdWorker.getId()); proof.setExperimentId(experiment.getId()); proof.setActorId(501L);
        proof.setManifestHash(experiment.getManifestHash()); proof.setStateVersion(0L); proof.setDependencyHash("d".repeat(64));
        proof.setProofHash(OnlineProtocolUtils.hash("online.preflight", Map.of("experimentId", experiment.getId().toString())));
        proof.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(60)); first.getBean(OnlinePreflightProofMapper.class).insert(proof);
        var state = first.getBean(OnlineLifecyclePersistenceService.class);
        var grant = new OnlineLifecyclePersistenceService.Grant("owned-test", "synthetic-cipher", LocalDateTime.now(ZoneOffset.UTC).plusSeconds(300));
        assertThatThrownBy(() -> state.apply(experiment.getId(), 502L, OnlineExperimentAction.START, "start", "a".repeat(64), experiment.getManifestHash(), 0L, "{}", proof.getProofHash(), "d".repeat(64), grant)).hasMessageContaining("ONLINE_STATE_CONFLICT");
        assertThatThrownBy(() -> state.apply(experiment.getId(), 501L, OnlineExperimentAction.START, "start", "a".repeat(64), experiment.getManifestHash(), 0L, "{}", "0".repeat(64), "d".repeat(64), grant)).hasMessageContaining("ONLINE_STATE_CONFLICT");
        var started = state.apply(experiment.getId(), 501L, OnlineExperimentAction.START, "start", "a".repeat(64), experiment.getManifestHash(), 0L, "{}", proof.getProofHash(), "d".repeat(64), grant);
        assertThat(started.status()).isEqualTo("ACTIVE"); assertThat(started.startedAt()).isNotNull();
        assertThat(state.apply(experiment.getId(), 501L, OnlineExperimentAction.START, "start", "a".repeat(64), experiment.getManifestHash(), 0L, "{}", proof.getProofHash(), "d".repeat(64), grant)).isEqualTo(started);
        assertThat(experiments().selectById(experiment.getId()).getControlCiphertext()).isEqualTo("synthetic-cipher");
    }

    @Test void stoppedEmptyDraftReleasesItsSlotAndSnapshotContainsNoAuthorizationCiphertext() {
        var experiment = experiment(1000, 100); experiment.setStatus("CREATED"); experiments().updateById(experiment);
        var state = first.getBean(OnlineLifecyclePersistenceService.class);
        var result = state.apply(experiment.getId(), 501L, OnlineExperimentAction.STOP, "stop", "a".repeat(64), experiment.getManifestHash(), 0L, "{\"reason\":\"无需启动\"}", null, null, null);
        assertThat(result.status()).isEqualTo("STOPPED"); assertThat(result.activeSlot()).isNull();
        assertThat(experiments().selectById(experiment.getId()).getActiveSlot()).isNull();
    }

    @Test void runtimeSrmCountsFirstDocumentsOnlyAndDoesNotRepeatWithinFiveMinutes() {
        var experiment = experiment(1000000, 1000); int count = 0;
        for (String doc : LongStream.rangeClosed(301, 1300).mapToObj(Long::toString).toList()) {
            if (!"BASELINE".equals(OnlineProtocolUtils.variant(OnlineProtocolUtils.bucket(experiment.getId().toString(), doc, "0123456789abcdef0123456789abcdef"), 5000))) { continue; }
            authority(0).allocate(experiment.getId(), request(experiment, doc, 1));
            authority(1).allocate(experiment.getId(), request(experiment, doc, 1));
            if (++count == 100) { break; }
        }
        authority(0).runtimeSrm(experiment.getId()); var checked = experiments().selectById(experiment.getId());
        assertThat(checked.getStatus()).isEqualTo("ACTIVE");
        assertThat(assignments().runtimeUnits(experiment.getId())).hasSize(100); assertThat(checked.getAssignedTaskCount()).isEqualTo(200);
        authority(1).runtimeSrm(experiment.getId()); assertThat(experiments().selectById(experiment.getId()).getLastRuntimeSrmCheckAt()).isEqualTo(checked.getLastRuntimeSrmCheckAt());
        checked.setLastRuntimeSrmCheckAt(LocalDateTime.now().minusSeconds(301)); experiments().updateById(checked);
        authority(1).runtimeSrm(experiment.getId()); var rechecked = experiments().selectById(experiment.getId());
        assertThat(rechecked.getStatus()).isEqualTo("PAUSED"); assertThat(rechecked.getReasonCode()).isEqualTo("SRM_DETECTED");
    }

    @Test void userCancellationBeforeDispatchSettlesOnlyWithBothAuthorityProofsAndNoBegin() {
        var experiment = experiment(1000, 100); var request = request(experiment, "301", 100); claim(experiment, request);
        var task = fact(request, null, null, null, true, null); var absent = fact(request, "ABSENT", null, null, false, null);
        authority(0).reconcile(experiment.getId(), Long.parseLong(request.taskId()), task, null);
        assertThat(assignment(request.taskId()).getSlotStatus()).isEqualTo("ACQUIRED");
        authority(1).reconcile(experiment.getId(), Long.parseLong(request.taskId()), task, absent);
        assertThat(assignment(request.taskId()).getSettlementStatus()).isEqualTo("SETTLED"); assertThat(assignment(request.taskId()).getConsumedTokens()).isEqualTo(BigInteger.ZERO);
    }

    @Test void waitExchangeRequiresCurrentControlEvenAfterValidSlotWasAcquired() {
        var experiment = experiment(1000, 100); var request = request(experiment, "301", 20); var permit = claim(experiment, request);
        var verifier = mock(OnlineCapabilityVerifier.class); var control = mock(OnlineControlAccessService.class);
        var source = Jwt.withTokenValue("wait").header("alg", "RS256")
                .claim(OnlineCapabilityConstant.EXPERIMENT_ID, experiment.getId().toString())
                .claim(OnlineCapabilityConstant.ASSIGNMENT_ID, permit.binding().assignmentId())
                .claim(JwtConstant.CLAIM_SPACE_ID, experiment.getSpaceId().toString())
                .claim(OnlineCapabilityConstant.MANIFEST_HASH, experiment.getManifestHash())
                .claim(OnlineCapabilityConstant.BINDING_HASH, permit.bindingHash()).build();
        when(verifier.verifyWait("wait", request.taskId())).thenReturn(source);
        var beans = new DefaultListableBeanFactory(); beans.registerSingleton("verifier", verifier);
        var access = new OnlineAssignmentAccessService(assignments(), first.getBean(OnlineExecutionSlotMapper.class), experiments(), authority(0),
                mock(OnlineTaskFeign.class), beans.getBeanProvider(OnlineCapabilityVerifier.class), control);
        assertThat(access.permit(request.taskId(), "wait").generation()).isEqualTo(permit.generation());
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "CANCEL_AUTHORIZATION_UNAVAILABLE")).when(control).require(experiment.getId());
        assertThatThrownBy(() -> access.permit(request.taskId(), "wait")).hasMessageContaining("CANCEL_AUTHORIZATION_UNAVAILABLE");
        assertThat(assignment(request.taskId()).getSlotStatus()).isEqualTo("ACQUIRED");
    }

    @Test void expiredControlCannotClaimOrBeginAndResumeKeepsUnresolvedBudgetBlocked() {
        var experiment = experiment(1000, 100); var request = request(experiment, "301", 20); var permit = claim(experiment, request);
        var row = experiments().selectById(experiment.getId()); row.setControlExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1)); experiments().updateById(row);
        assertThatThrownBy(() -> authority(1).begin(experiment.getId(), Long.valueOf(request.taskId()), permit.generation(), permit.permitHash())).hasMessageContaining("CANCEL_AUTHORIZATION_UNAVAILABLE");
        assertThatThrownBy(() -> authority(1).claim(experiment.getId(), Long.valueOf(request.taskId()), permit.bindingHash())).hasMessageContaining("CANCEL_AUTHORIZATION_UNAVAILABLE");
        authority(0).reconcile(experiment.getId(), Long.valueOf(request.taskId()), null, null);
        authority(0).safetyPause(experiment.getId(), "ONLINE_FACT_UNKNOWN"); row = experiments().selectById(experiment.getId());
        var current = row; var state = first.getBean(OnlineLifecyclePersistenceService.class);
        var grant = new OnlineLifecyclePersistenceService.Grant("test-key", "test-cipher", LocalDateTime.now(ZoneOffset.UTC).plusSeconds(300));
        assertThatThrownBy(() -> state.apply(current.getId(), 501L, OnlineExperimentAction.RESUME, "resume", "a".repeat(64), current.getManifestHash(),
                current.getStateVersion(), "{\"reason\":\"复核\"}", null, "d".repeat(64), grant)).hasMessageContaining("ONLINE_FACT_UNKNOWN");
        assertThat(experiments().selectById(experiment.getId()).getStatus()).isEqualTo("PAUSED");
        assertThat(assignment(request.taskId()).getSlotStatus()).isEqualTo("ACQUIRED");
        assertThat(experiments().selectById(experiment.getId()).getReservedTokenBudget()).isEqualTo(20);
    }

    @Test void dependencyRefusalCommitsPauseWithoutAcceptingOrReroutingFrozenRequest() {
        var experiment = experiment(1000, 100); var request = request(experiment, "301", 20);
        var route = new OnlineTaskRouteEntity();
        route.setId(Long.valueOf(request.taskId())); route.setSpaceId(experiment.getSpaceId()); route.setActorId(501L);
        route.setRequestKey(request.requestKey()); route.setRequestHash(request.requestHash());
        var routing = first.getBean(OnlineRoutingPersistenceService.class); routing.reserve(route);
        assertThat(routing.resolve(request, experiment.getId(), "f".repeat(64)).refusal()).isEqualTo("DEPENDENCY_DRIFT");
        assertThat(experiments().selectById(experiment.getId()).getStatus()).isEqualTo("PAUSED");
        assertThat(experiments().selectById(experiment.getId()).getAssignedTaskCount()).isZero();
    }

    @Test void independentConsumerProcessesProveLiveGroupLimitsDuplicateCrashAndTerminalRecovery() throws Exception {
        var experiment = experiment(1000000, 1000);
        var jdbc = new JdbcTemplate(first.getBean(DriverManagerDataSource.class));
        jdbc.execute("CREATE TABLE IF NOT EXISTS p602_process_execution(task_id BIGINT PRIMARY KEY,experiment_id BIGINT NOT NULL,variant VARCHAR(16) NOT NULL,pid BIGINT NOT NULL,started_at DATETIME(3),finished_at DATETIME(3))");
        var documents = LongStream.rangeClosed(301, 1300).mapToObj(Long::toString).toList();
        String baseline = documents.stream().filter(doc -> "BASELINE".equals(OnlineProtocolUtils.variant(OnlineProtocolUtils.bucket(experiment.getId().toString(), doc, "0123456789abcdef0123456789abcdef"), 5000))).findFirst().orElseThrow();
        String candidate = documents.stream().filter(doc -> !doc.equals(baseline) && "CANDIDATE".equals(OnlineProtocolUtils.variant(OnlineProtocolUtils.bucket(experiment.getId().toString(), doc, "0123456789abcdef0123456789abcdef"), 5000))).findFirst().orElseThrow();
        var one = request(experiment, baseline, 20); var two = request(experiment, candidate, 20); var waiting = request(experiment, baseline, 20);
        for (var item : List.of(one, two, waiting)) { authority(0).allocate(experiment.getId(), item); }
        try (var left = new ConsumerProcess(21); var right = new ConsumerProcess(22)) {
            assertThat(left.pid).isNotEqualTo(right.pid).isNotEqualTo(ProcessHandle.current().pid());
            assertThat(left.command("START", experiment.getId(), one.taskId()).path("status").asText()).isEqualTo("STARTED");
            assertThat(right.command("START", experiment.getId(), two.taskId()).path("status").asText()).isEqualTo("STARTED");
            assertThat(left.command("STATUS", experiment.getId(), one.taskId()).path("active").asInt()).isEqualTo(1);
            assertThat(right.command("STATUS", experiment.getId(), two.taskId()).path("active").asInt()).isEqualTo(1);
            assertThat(right.command("START", experiment.getId(), one.taskId()).path("status").asText()).isEqualTo("DUPLICATE");
            assertThat(right.command("START", experiment.getId(), waiting.taskId()).path("reason").asText()).contains("ONLINE_SLOT_BUSY");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM p602_process_execution WHERE experiment_id=? AND started_at IS NOT NULL AND finished_at IS NULL", Long.class, experiment.getId())).isEqualTo(2L);
            authority(0).reconcile(experiment.getId(), Long.valueOf(one.taskId()), null, null);
            assertThat(assignment(one.taskId()).getSlotStatus()).isEqualTo("ACQUIRED");
            left.command("FINISH", experiment.getId(), one.taskId());
            String finishedAt = jdbc.queryForObject("SELECT finished_at FROM p602_process_execution WHERE task_id=?", Timestamp.class, Long.valueOf(one.taskId())).toLocalDateTime().toString();
            var terminal = fact(one, "COMPLETED", null, "701", false, finishedAt);
            authority(0).reconcile(experiment.getId(), Long.valueOf(one.taskId()), null, terminal);
            assertThat(assignment(one.taskId()).getSlotStatus()).isEqualTo("RELEASED");
            assertThat(assignment(one.taskId()).getSettlementStatus()).isEqualTo("UNKNOWN");
            authority(1).reconcile(experiment.getId(), Long.valueOf(one.taskId()), fact(one, null, "7", "701", false, null), terminal);
            authority(0).reconcile(experiment.getId(), Long.valueOf(one.taskId()), fact(one, null, "7", "701", false, null), terminal);
            assertThat(experiments().selectById(experiment.getId()).getConsumedTokens()).isEqualTo(BigInteger.valueOf(7));
            var next = right.command("START", experiment.getId(), waiting.taskId());
            assertThat(next.path("status").asText()).isEqualTo("STARTED"); assertThat(next.path("generation").asLong()).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM p602_process_execution WHERE task_id=?", Long.class, Long.valueOf(one.taskId()))).isEqualTo(1L);
            // 仅杀本测试创建的消费者。缺失真实终态时，不因进程死亡释放任何占位。
            right.crash();
            authority(0).reconcile(experiment.getId(), Long.valueOf(two.taskId()), null, null);
            var unresolved = assignment(two.taskId()); unresolved.setUnresolvedSince(LocalDateTime.now().minusSeconds(301)); assignments().updateById(unresolved);
            authority(0).reconcile(experiment.getId(), Long.valueOf(two.taskId()), null, null);
            assertThat(experiments().selectById(experiment.getId()).getStatus()).isEqualTo("PAUSED");
            assertThat(assignment(two.taskId()).getSlotStatus()).isEqualTo("ACQUIRED");
            assertThat(assignment(waiting.taskId()).getSlotStatus()).isEqualTo("ACQUIRED");
            assertThat(left.command("START", experiment.getId(), two.taskId()).path("status").asText()).isEqualTo("DUPLICATE");
            var blocked = request(experiment, candidate, 20);
            // 原已接受项仍可排队，但未知执行所在组不能再开始模型替身。
            assertThat(authority(0).allocate(experiment.getId(), blocked).reason()).isEqualTo("ONLINE_GATE_CLOSED");
            var trace = jdbc.queryForList("SELECT variant,started_at,finished_at,pid FROM p602_process_execution WHERE experiment_id=? ORDER BY started_at,task_id", experiment.getId());
            assertThat(trace).hasSize(3);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM p602_process_execution a JOIN p602_process_execution b ON a.experiment_id=b.experiment_id AND a.variant=b.variant AND a.task_id<b.task_id WHERE a.experiment_id=? AND a.started_at IS NOT NULL AND b.started_at IS NOT NULL AND (a.finished_at IS NULL OR b.started_at<a.finished_at) AND (b.finished_at IS NULL OR a.started_at<b.finished_at)", Long.class, experiment.getId())).isZero();
            System.out.printf("P602_PROCESS_LIVE pids=%d,%d events=%s%n", left.pid, right.pid, trace);
        }
    }

    @Test void hotRowPerformanceAcrossTwoIndependentJvmConsumers() throws Exception {
        var experiment = experiment(1000000, 1000); int count = 100;
        try (var left = new ConsumerProcess(23); var right = new ConsumerProcess(24); var executor = Executors.newFixedThreadPool(2)) {
            long before = lockTime(); long start = System.nanoTime();
            var jobs = new ArrayList<Callable<List<Long>>>();
            for (var consumer : List.of(left, right)) {
                var requests = new ArrayList<OnlineAssignmentRequestDTO>();
                for (int index = 0; index < count; index++) { requests.add(request(experiment, "301", 1)); }
                jobs.add(() -> { var latencies = new ArrayList<Long>();
                    for (var item : requests) { var response = consumer.send(Map.of("type", "ALLOCATE", "experiment", experiment.getId(), "request", item));
                        assertThat(response.path("accepted").asBoolean()).isTrue(); latencies.add(response.path("nanos").asLong()); }
                    return latencies;
                });
            }
            var values = new ArrayList<Long>();
            for (var future : executor.invokeAll(jobs, 60, TimeUnit.SECONDS)) { values.addAll(future.get(1, TimeUnit.SECONDS)); }
            values.sort(Long::compareTo); long elapsed = System.nanoTime() - start;
            assertThat(experiments().selectById(experiment.getId()).getAssignedTaskCount()).isEqualTo(2 * count);
            System.out.printf("P602_PROCESS_HOT_ROW processes=2 count=%d throughput=%.2f/s p95=%.2fms elapsed=%.2fs globalLockTimeDelta=%dms%n",
                    values.size(), values.size() * 1e9 / elapsed, values.get((int) Math.ceil(values.size() * .95) - 1) / 1e6, elapsed / 1e9, lockTime() - before);
        }
    }

    private static final class ConsumerProcess implements AutoCloseable {
        private final Process process; private final BufferedWriter input;
        private final LinkedBlockingQueue<String> output = new LinkedBlockingQueue<>(); private final Path arguments;
        private final long pid;
        ConsumerProcess(int worker) throws Exception {
            arguments = Files.createTempFile("p602-owned-jvm-", ".args");
            String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            Files.writeString(arguments, "-cp\n\"" + classpath.replace('\\', '/') + "\"\n" + OnlineExecutionProcessFixture.class.getName() + "\n" + worker, StandardCharsets.UTF_8);
            String binary = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
            process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", binary).toString(), "@" + arguments).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            Thread.ofPlatform().daemon().start(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    for (String line; (line = reader.readLine()) != null;) { if (line.startsWith("P602_FIXTURE ")) { output.add(line.substring(13)); } }
                } catch (Exception failure) { output.add("{\"closed\":true,\"reason\":\"" + failure.getClass().getSimpleName() + "\"}"); }
            });
            try {
                var ready = receive(); assertThat(ready.path("ready").asBoolean()).isTrue(); pid = ready.path("pid").asLong();
            } catch (Exception | AssertionError failed) { close(); throw failed; }
        }
        JsonNode command(String type, Long experiment, String task) throws Exception { return send(Map.of("type", type, "experiment", experiment, "task", task)); }
        JsonNode send(Map<String, ?> command) throws Exception { input.write(JsonUtils.toJson(command)); input.newLine(); input.flush(); return receive(); }
        private JsonNode receive() throws Exception { String line = output.poll(45, TimeUnit.SECONDS); assertThat(line).as("独立消费者应在45秒内响应").isNotNull(); return JsonUtils.parseStrict(line, JsonNode.class); }
        void crash() throws Exception { process.destroyForcibly(); assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue(); }
        @Override public void close() throws Exception { try { if (process.isAlive()) { crash(); } } finally { input.close(); Files.deleteIfExists(arguments); } }
    }

    private long lockTime() throws Exception {
        try (var connection = first.getBean(DriverManagerDataSource.class).getConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("SHOW GLOBAL STATUS LIKE 'Innodb_row_lock_time'")) {
            assertThat(result.next()).isTrue(); return result.getLong(2);
        }
    }
}
