package com.agentdoc.task.a2a;

import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.service.ExecutionArtifactService;
import com.agentdoc.task.service.TokenUsageService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 真实 Spring 事务/Task Mapper；测试表模拟下游账本/产物写入，验证同事务回滚边界。 */
class TaskTerminalPersistenceServiceTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private TaskTerminalPersistenceService service;
    private TokenUsageService ledger;
    private ExecutionArtifactService artifacts;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = context.getBean(JdbcTemplate.class);
        service = context.getBean(TaskTerminalPersistenceService.class);
        ledger = context.getBean(TokenUsageService.class);
        artifacts = context.getBean(ExecutionArtifactService.class);
        jdbc.execute("CREATE TABLE task (id BIGINT PRIMARY KEY, space_id BIGINT, agent_id BIGINT, document_id BIGINT, "
                + "execution_mode VARCHAR(20), document_version_snapshot BIGINT, document_content_sha256 VARCHAR(64), "
                + "input_snapshot_schema_version INT, input_snapshot_hash VARCHAR(64), derivation_request_hash VARCHAR(64), "
                + "agent_execution_id BIGINT, a2a_task_id VARCHAR(255), a2a_context_id VARCHAR(255), status INT, "
                + "last_heartbeat_at TIMESTAMP, end_time TIMESTAMP, result_summary TEXT, error_message TEXT, tokens_used BIGINT, "
                + "tokens_estimated BOOLEAN, prompt_hash VARCHAR(64), deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE recovery_test_ledger (execution_id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE recovery_test_artifact (task_id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO task (id, space_id, agent_id, document_id, execution_mode, a2a_task_id, a2a_context_id, "
                + "agent_execution_id, status) VALUES (1, 3, 2, 4, 'ISOLATED', 'remote', 'context', 5, ?)", TaskStatus.RUNNING.getCode());
        when(ledger.recordRemote(any(), any())).thenAnswer(call -> { jdbc.update("INSERT INTO recovery_test_ledger VALUES (5)"); return true; });
        doAnswer(call -> { jdbc.update("INSERT INTO recovery_test_artifact VALUES (1)"); return null; }).when(artifacts).appendResultSummary(any());
    }

    @AfterEach
    void close() { context.close(); }

    @Test
    void commitsTogetherAndRepeatedOrDifferentRemoteBindingCannotOverwriteTerminalTask() {
        assertThat(service.persist(terminal(), usage(), () -> true)).isTrue();
        assertThat(service.persist(terminal(), usage(), () -> true)).isFalse();
        assertThat(status()).isEqualTo(TaskStatus.COMPLETED.getCode());
        assertThat(count("recovery_test_ledger")).isEqualTo(1);
        assertThat(count("recovery_test_artifact")).isEqualTo(1);
    }

    @Test
    void lockTakenOverBeforeCommitRollsBackTaskLedgerAndArtifact() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> service.persist(terminal(), usage(), () -> calls.incrementAndGet() == 1))
                .hasMessage("RECOVERY_CAPACITY_EXCEEDED");
        assertRolledBack();
    }

    @Test
    void ledgerAndArtifactFailuresRollbackAllLocalWrites() {
        doAnswer(call -> { jdbc.update("INSERT INTO recovery_test_artifact VALUES (1)"); throw new IllegalStateException("artifact failed"); })
                .when(artifacts).appendResultSummary(any());
        assertThatThrownBy(() -> service.persist(terminal(), usage(), () -> true)).hasMessage("artifact failed");
        assertRolledBack();
        doThrow(new IllegalStateException("ledger failed")).when(ledger).recordRemote(any(), any());
        assertThatThrownBy(() -> service.persist(terminal(), usage(), () -> true)).hasMessage("ledger failed");
        assertRolledBack();
    }

    @Test
    void changedSpaceOrExecutionBindingFailsConditionallyWithoutWritingLedger() {
        jdbc.update("UPDATE task SET space_id=99 WHERE id=1");
        assertThat(service.persist(terminal(), usage(), () -> true)).isFalse();
        jdbc.update("UPDATE task SET space_id=3, agent_execution_id=99 WHERE id=1");
        assertThat(service.persist(terminal(), usage(), () -> true)).isFalse();
        assertRolledBack();
    }

    @Test
    void newCancellationIntentIsNotOverwrittenByAnOlderActiveProjection() {
        jdbc.update("UPDATE task SET status=? WHERE id=1", TaskStatus.CANCELING.getCode());
        TaskEntity running = terminal();
        running.setStatus(TaskStatus.RUNNING.getCode());
        assertThat(service.persist(running, null, () -> true)).isFalse();
        assertThat(status()).isEqualTo(TaskStatus.CANCELING.getCode());
    }

    private void assertRolledBack() {
        assertThat(status()).isEqualTo(TaskStatus.RUNNING.getCode());
        assertThat(count("recovery_test_ledger")).isZero();
        assertThat(count("recovery_test_artifact")).isZero();
    }

    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private int status() { return jdbc.queryForObject("SELECT status FROM task WHERE id=1", Integer.class); }

    private TaskEntity terminal() {
        TaskEntity task = new TaskEntity();
        task.setId(1L);
        task.setSpaceId(3L);
        task.setAgentId(2L);
        task.setDocumentId(4L);
        task.setAgentExecutionId(5L);
        task.setA2aTaskId("remote");
        task.setA2aContextId("context");
        task.setExecutionMode("ISOLATED");
        task.setStatus(TaskStatus.COMPLETED.getCode());
        return task;
    }

    private A2aTokenUsage usage() { return new A2aTokenUsage(10L, 0L, 20L, false, false, false); }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""); }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(ds);
            MybatisConfiguration config = new MybatisConfiguration();
            config.setMapUnderscoreToCamelCase(true);
            config.addMapper(TaskMapper.class);
            factory.setConfiguration(config);
            return factory.getObject();
        }
        @Bean TaskMapper taskMapper(SqlSessionFactory factory) { return new SqlSessionTemplate(factory).getMapper(TaskMapper.class); }
        @Bean TokenUsageService ledger() { return mock(TokenUsageService.class); }
        @Bean ExecutionArtifactService artifacts() { return mock(ExecutionArtifactService.class); }
        @Bean TaskTerminalPersistenceService persistence(TaskMapper mapper, TokenUsageService ledger, ExecutionArtifactService artifacts) {
            return new TaskTerminalPersistenceService(mapper, ledger, artifacts);
        }
    }
}
