package com.agentdoc.task.service;

import com.agentdoc.task.config.OnlineTaskProperties;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.task.enums.ActorType;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.vo.AgentExecutionProfileVO;
import com.agentdoc.common.feign.vo.DocumentExecutionContextVO;
import com.agentdoc.common.feign.vo.DocumentVersionExecutionContextVO;
import com.agentdoc.common.feign.vo.SpaceBudgetVO;
import com.agentdoc.common.handler.CommonMetaObjectHandler;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import com.agentdoc.task.a2a.A2aTaskClient;
import com.agentdoc.task.config.ReplayProperties;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.enums.AuditAction;
import com.agentdoc.task.enums.AuditTargetType;
import com.agentdoc.task.mapper.AuditLogMapper;
import com.agentdoc.task.mapper.TaskCreationIntentMapper;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.mapper.TokenUsageDetailMapper;
import com.agentdoc.task.pojo.dto.TaskCreateDTO;
import com.agentdoc.task.pojo.entity.TaskCreationIntentEntity;
import com.agentdoc.task.pojo.entity.AuditLogEntity;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.vo.TaskVO;
import com.agentdoc.task.security.TaskCapabilityCryptoService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 实际Task/意图/审计事务，两个独立容器；Feign、MQ、模型均为替身。 */
@EnabledIfEnvironmentVariable(named = "P602_INTENT_MYSQL_URL", matches = ".+")
class TaskCreationIntentMySqlTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean DriverManagerDataSource source() {
            String url = System.getenv("P602_INTENT_MYSQL_URL");
            if (!url.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/agentdoc_p602_intent_[a-f0-9]{12}\\?.*")) { throw new IllegalArgumentException("仅允许本批隔离库"); }
            return new DriverManagerDataSource(url, System.getenv("P602_MYSQL_USER"), System.getenv("P602_MYSQL_PASSWORD"));
        }
        @Bean PlatformTransactionManager transactionManager(DriverManagerDataSource source) { return new DataSourceTransactionManager(source); }
        @Bean SqlSessionFactory factory(DriverManagerDataSource source) throws Exception {
            var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
            var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(configuration);
            factory.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(new CommonMetaObjectHandler()));
            var built = factory.getObject(); built.getConfiguration().addMapper(TaskMapper.class);
            built.getConfiguration().addMapper(TaskCreationIntentMapper.class); built.getConfiguration().addMapper(AuditLogMapper.class); return built;
        }
        @Bean SqlSessionTemplate session(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean TaskMapper tasks(SqlSessionTemplate session) { return session.getMapper(TaskMapper.class); }
        @Bean TaskCreationIntentMapper intents(SqlSessionTemplate session) { return session.getMapper(TaskCreationIntentMapper.class); }
        @Bean AuditLogMapper logs(SqlSessionTemplate session) { return session.getMapper(AuditLogMapper.class); }
        @Bean DocumentFeign documents() { return mock(DocumentFeign.class); }
        @Bean AgentFeign agents() { return mock(AgentFeign.class); }
        @Bean AuthFeign auth() { return mock(AuthFeign.class); }
        @Bean TaskMessagePublisher messages() { return mock(TaskMessagePublisher.class); }
        @Bean TaskCapabilityCryptoService crypto() { return mock(TaskCapabilityCryptoService.class); }
        @Bean AuditLogService audit(AuditLogMapper logs, DocumentFeign documents, AuthFeign auth, AgentFeign agents) {
            return spy(new AuditLogService(logs, documents, auth, agents));
        }
        @Bean TaskCreationIntentPersistenceService persistence(TaskCreationIntentMapper intents, TaskMapper tasks, AuditLogService audit) {
            return new TaskCreationIntentPersistenceService(intents, tasks, audit);
        }
        @Bean TaskCreationIntentService creation(TaskCreationIntentMapper intents, TaskMapper tasks, TaskCreationIntentPersistenceService persistence, TaskMessagePublisher messages) {
            return new TaskCreationIntentService(intents, tasks, persistence, messages);
        }
        @Bean TaskService taskService(TaskMapper tasks, AgentFeign agents, DocumentFeign documents, TaskMessagePublisher messages,
                TaskCapabilityCryptoService crypto, AuthFeign auth, AuditLogService audit, TaskCreationIntentService creation, TaskCreationIntentMapper creationIntentMapper) {
            return new TaskService(tasks, mock(TokenUsageDetailMapper.class), mock(A2aTaskClient.class), agents, documents,
                    messages, crypto, auth, audit, new ObjectMapper(), mock(TaskCapabilityVerifier.class), new ReplayProperties(), creation, new TaskOnlineRoutingService(new OnlineTaskProperties(), mock(OnlineEvaluationFeign.class), creationIntentMapper), mock(TaskOnlineDispatchService.class));
        }
    }
    private AnnotationConfigApplicationContext first;
    private AnnotationConfigApplicationContext second;
    private long space;
    @BeforeEach void setup() {
        first = new AnnotationConfigApplicationContext(Config.class); second = new AnnotationConfigApplicationContext(Config.class); space = IdWorker.getId();
        human(501);
        for (var context : List.of(first, second)) {
            var documents = context.getBean(DocumentFeign.class); when(documents.checkSpacePermission(space, "task:create")).thenReturn(Result.ok());
            when(documents.getExecutionContext(301L)).thenReturn(Result.ok(new DocumentExecutionContextVO(301L, space, DocType.DRAFT.getCode(), 1, 1L, "a".repeat(64), 100L)));
            when(documents.getVersionExecutionContext(301L, 1L, "a".repeat(64))).thenReturn(Result.ok(new DocumentVersionExecutionContextVO(301L, 1L, "a".repeat(64), 100L)));
            when(documents.getSpaceExecutionBudget(space)).thenReturn(Result.ok(new SpaceBudgetVO(space, 500L)));
            when(context.getBean(AgentFeign.class).getExecutionProfile(201L)).thenReturn(Result.ok(new AgentExecutionProfileVO(201L, space, 11L, 1000L, null, 9L, true, null, null)));
            when(context.getBean(AuthFeign.class).issueTaskCapability(any())).thenReturn(Result.ok("capability"));
            when(context.getBean(TaskCapabilityCryptoService.class).encrypt("capability")).thenReturn("encrypted");
        }
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); if (first != null) { first.close(); } if (second != null) { second.close(); } }
    private static void human(long actor) {
        var jwt = Jwt.withTokenValue("fixture-user").header("alg", "RS256").subject(Long.toString(actor)).claim(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_USER).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
    private TaskCreateDTO request(String key) { return new TaskCreateDTO(space, 201L, 301L, "  重试任务  ", "  新指令  ", null, null, null, key); }
    private TaskCreationIntentEntity intent(long actor, String key) {
        return first.getBean(TaskCreationIntentMapper.class).selectOne(new LambdaQueryWrapper<TaskCreationIntentEntity>()
                .eq(TaskCreationIntentEntity::getSpaceId, space).eq(TaskCreationIntentEntity::getActorId, actor).eq(TaskCreationIntentEntity::getRequestKey, key));
    }
    private TaskEntity task(Long id) { return first.getBean(TaskMapper.class).selectById(id); }
    private void currentVersionChanged() {
        var documents = first.getBean(DocumentFeign.class);
        when(documents.getExecutionContext(301L)).thenReturn(Result.ok(new DocumentExecutionContextVO(301L, space, DocType.DRAFT.getCode(), 1, 2L, "b".repeat(64), 200L)));
        when(documents.getSpaceExecutionBudget(space)).thenReturn(Result.ok(new SpaceBudgetVO(space, 100L)));
    }

    @Test void messageFailureRetryKeepsOriginalTaskVersionBudgetAndSingleCreationAudit() {
        var messages = first.getBean(TaskMessagePublisher.class); doThrow(new IllegalStateException("lost acknowledgement")).doNothing().when(messages).publish(anyLong());
        var service = first.getBean(TaskService.class);
        assertThatThrownBy(() -> service.create(request("message-retry"))).hasMessageContaining("TASK_MESSAGE_PENDING");
        var intent = intent(501, "message-retry"); var original = task(intent.getTaskId()); String originalHash = original.getInputSnapshotHash();
        assertThat(original.getStatus()).isEqualTo(TaskStatus.PENDING.getCode()); assertThat(original.getEndTime()).isNull();
        currentVersionChanged(); var returned = service.create(request("message-retry")); assertThat(returned.id()).isEqualTo(original.getId());
        assertThat(task(original.getId()).getDocumentVersionSnapshot()).isEqualTo(1); assertThat(task(original.getId()).getTokenBudget()).isEqualTo(500);
        assertThat(task(original.getId()).getInputSnapshotHash()).isEqualTo(originalHash);
        assertThat(service.create(request("message-retry")).id()).isEqualTo(original.getId());
        verify(messages, times(2)).publish(original.getId()); verify(first.getBean(AuthFeign.class), times(1)).issueTaskCapability(any());
        verify(first.getBean(DocumentFeign.class), times(1)).getSpaceExecutionBudget(space);
        assertThat(intent(501, "message-retry").getStatus()).isEqualTo("PUBLISHED"); assertThat(intent(501, "message-retry").getReasonCode()).isNull();
        assertThat(first.getBean(AuditLogMapper.class).selectCount(new LambdaQueryWrapper<AuditLogEntity>()
                .eq(AuditLogEntity::getTargetId, original.getId()).eq(AuditLogEntity::getAction, "TASK_CREATED"))).isEqualTo(1);
    }

    @Test void capabilityFailureDoesNotCreateAFailedExecutionAndRetryOnlySignsTheOriginalTask() {
        var auth = first.getBean(AuthFeign.class); when(auth.issueTaskCapability(any())).thenThrow(new IllegalStateException("unavailable")).thenReturn(Result.ok("capability"));
        var service = first.getBean(TaskService.class);
        assertThatThrownBy(() -> service.create(request("sign-retry"))).hasMessageContaining("TASK_CAPABILITY_PENDING");
        var intent = intent(501, "sign-retry"); assertThat(task(intent.getTaskId()).getStatus()).isEqualTo(TaskStatus.PENDING.getCode());
        assertThat(task(intent.getTaskId()).getCapabilityToken()).isNull(); currentVersionChanged();
        assertThat(service.create(request("sign-retry")).id()).isEqualTo(intent.getTaskId());
        assertThat(task(intent.getTaskId()).getCapabilityToken()).isEqualTo("encrypted");
        verify(first.getBean(DocumentFeign.class), times(1)).getSpaceExecutionBudget(space);
    }

    @Test void permissionRevocationAndScopeMoveBlockRetryWithoutChangingFrozenInput() {
        var service = first.getBean(TaskService.class); var documents = first.getBean(DocumentFeign.class);
        when(first.getBean(AuthFeign.class).issueTaskCapability(any())).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(() -> service.create(request("revoked"))).hasMessageContaining("TASK_CAPABILITY_PENDING");
        var intent = intent(501, "revoked"); String frozen = intent.getInputJson();
        when(documents.checkSpacePermission(space, "task:create")).thenReturn(Result.fail(ErrorCode.FORBIDDEN, "revoked"));
        assertThatThrownBy(() -> service.create(request("revoked"))).isInstanceOf(BusinessException.class).hasMessageContaining("revoked");
        when(documents.checkSpacePermission(space, "task:create")).thenReturn(Result.ok());
        when(documents.getExecutionContext(301L)).thenReturn(Result.ok(new DocumentExecutionContextVO(301L, space + 1, DocType.DRAFT.getCode(), 1, 2L, "b".repeat(64), 100L)));
        assertThatThrownBy(() -> service.create(request("revoked"))).hasMessageContaining("原任务文档当前不可访问");
        assertThat(intent(501, "revoked").getInputJson()).isEqualTo(frozen); verify(first.getBean(AuthFeign.class), times(1)).issueTaskCapability(any());
        verifyNoInteractions(first.getBean(TaskMessagePublisher.class));
    }

    @Test void keyConflictRejectsDifferentInputButDifferentActorsHaveIndependentKeys() {
        var service = first.getBean(TaskService.class); var one = service.create(request("scoped"));
        var changed = new TaskCreateDTO(space, 201L, 301L, "重试任务", "新指令", 500L, null, null, "scoped");
        assertThatThrownBy(() -> service.create(changed)).hasMessageContaining("IDEMPOTENCY_CONFLICT");
        human(502); var two = service.create(request("scoped")); assertThat(two.id()).isNotEqualTo(one.id());
        assertThat(intent(501, "scoped").getTaskId()).isEqualTo(one.id()); assertThat(intent(502, "scoped").getTaskId()).isEqualTo(two.id());
    }

    @Test void concurrentRequestsAcrossIndependentContainersProduceOneFrozenTask() throws Exception {
        var jobs = new ArrayList<Callable<TaskVO>>();
        for (int index = 0; index < 16; index++) { int instance = index;
            jobs.add(() -> { human(501); try { return (instance % 2 == 0 ? first : second).getBean(TaskService.class).create(request("race")); }
                finally { SecurityContextHolder.clearContext(); } }); }
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = executor.invokeAll(jobs, 60, TimeUnit.SECONDS); var ids = new ArrayList<Long>();
            for (var future : futures) { ids.add(future.get(10, TimeUnit.SECONDS).id()); }
            assertThat(ids.stream().distinct()).hasSize(1); assertThat(intent(501, "race").getStatus()).isEqualTo("PUBLISHED");
            assertThat(first.getBean(TaskMapper.class).selectCount(new LambdaQueryWrapper<TaskEntity>().eq(TaskEntity::getSpaceId, space))).isEqualTo(1);
        }
    }

    @Test void deletedOriginalTaskCannotBeRecreatedByRetryingTheKey() {
        var service = first.getBean(TaskService.class); var created = service.create(request("deleted"));
        first.getBean(TaskMapper.class).deleteById(created.id());
        assertThatThrownBy(() -> service.create(request("deleted"))).hasMessageContaining("MANIFEST_INVALID");
        assertThat(intent(501, "deleted").getTaskId()).isEqualTo(created.id());
    }

    @Test void auditFailureRollsBackTaskAndAuditButRetainsFrozenCreationIntent() {
        var audit = first.getBean(AuditLogService.class);
        doAnswer(invocation -> { invocation.callRealMethod(); throw new IllegalStateException("audit unavailable"); })
                .when(audit).recordHuman(eq(space), eq(AuditAction.TASK_CREATED), eq(AuditTargetType.TASK), anyLong(), isNull());
        var service = first.getBean(TaskService.class);
        assertThatThrownBy(() -> service.create(request("audit-retry"))).hasMessageContaining("audit unavailable");
        var original = intent(501, "audit-retry"); assertThat(original.getStatus()).isEqualTo("FROZEN");
        assertThat(task(original.getTaskId())).isNull();
        assertThat(first.getBean(AuditLogMapper.class).selectCount(new LambdaQueryWrapper<AuditLogEntity>()
                .eq(AuditLogEntity::getSpaceId, space))).isZero();
        verifyNoInteractions(first.getBean(TaskMessagePublisher.class));
        doCallRealMethod().when(audit).recordHuman(eq(space), eq(AuditAction.TASK_CREATED), eq(AuditTargetType.TASK), anyLong(), isNull());
        currentVersionChanged();
        assertThat(service.create(request("audit-retry")).id()).isEqualTo(original.getTaskId());
        assertThat(task(original.getTaskId()).getDocumentVersionSnapshot()).isEqualTo(1L);
        assertThat(first.getBean(AuditLogMapper.class).selectCount(new LambdaQueryWrapper<AuditLogEntity>()
                .eq(AuditLogEntity::getSpaceId, space))).isEqualTo(1);
    }

    @Test void machineRepairOnlyCreatesOriginalAcceptedFrozenIdentityAndNeverRevivesDeletedHistory() {
        var audit = first.getBean(AuditLogService.class);
        doThrow(new IllegalStateException("audit unavailable")).when(audit).recordHuman(eq(space), eq(AuditAction.TASK_CREATED), eq(AuditTargetType.TASK), anyLong(), isNull());
        assertThatThrownBy(() -> first.getBean(TaskService.class).create(request("accepted-repair"))).hasMessageContaining("audit unavailable");
        var original = intent(501, "accepted-repair"); assertThat(original.getStatus()).isEqualTo("FROZEN");
        // 模拟 Evaluation 已接受而 Task 尚未收到路由响应；冻结输入来自真实创建用例。
        first.getBean(TaskCreationIntentMapper.class).update(null, new LambdaUpdateWrapper<TaskCreationIntentEntity>()
                .eq(TaskCreationIntentEntity::getId, original.getId()).set(TaskCreationIntentEntity::getBindingJson, null));
        original = intent(501, "accepted-repair"); var frozen = TaskCreationIntentPersistenceService.frozen(original);
        var binding = new OnlineTaskBindingDTO("61", "11", "a".repeat(64), "1", "BASELINE", 9000, "501", frozen.getId().toString(),
                space + "", "201", "301", frozen.getDocumentVersionSnapshot().toString(), frozen.getDocumentContentSha256(),
                frozen.getInputSnapshotSchemaVersion(), frozen.getInputSnapshotHash(), "71", 2, "b".repeat(64), "c".repeat(64),
                "d".repeat(64), frozen.getTokenBudget().toString(), 600, "LIVE", "ORIGINAL", 2);
        SecurityContextHolder.clearContext();
        var persistence = first.getBean(TaskCreationIntentPersistenceService.class);
        var repaired = persistence.repairAccepted(original.getId(), binding, 502L, true);
        assertThat(repaired.getId()).isEqualTo(original.getTaskId()); assertThat(repaired.getStatus()).isEqualTo(TaskStatus.TERMINATED.getCode());
        assertThat(repaired.getOnlineBindingHash()).isEqualTo(OnlineProtocolUtils.hash("online.binding", binding));
        assertThat(persistence.repairAccepted(original.getId(), binding, 502L, true).getId()).isEqualTo(original.getTaskId());
        var logs = first.getBean(AuditLogMapper.class).selectList(new LambdaQueryWrapper<AuditLogEntity>().eq(AuditLogEntity::getSpaceId, space));
        assertThat(logs).hasSize(1); assertThat(logs.getFirst().getActorType()).isEqualTo(ActorType.SERVICE.getCode());
        first.getBean(TaskMapper.class).deleteById(repaired.getId()); var identity = original.getId();
        assertThatThrownBy(() -> persistence.repairAccepted(identity, binding, 502L, false)).hasMessageContaining("MANIFEST_INVALID");
        assertThat(task(repaired.getId())).isNull(); verifyNoInteractions(first.getBean(TaskMessagePublisher.class));
    }

    @Test void requestKeysRemainCaseSensitiveInTheRealUniqueIndex() {
        var service = first.getBean(TaskService.class);
        var upper = service.create(request("Case-Key")); var lower = service.create(request("case-key"));
        assertThat(upper.id()).isNotEqualTo(lower.id());
        assertThat(service.create(request("Case-Key")).id()).isEqualTo(upper.id());
        assertThat(service.create(request("case-key")).id()).isEqualTo(lower.id());
    }
}
