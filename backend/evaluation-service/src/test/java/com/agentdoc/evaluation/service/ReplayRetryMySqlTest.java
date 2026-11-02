package com.agentdoc.evaluation.service;

import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
import com.agentdoc.evaluation.mapper.EvaluationCaseRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationRunMapper;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationRunEntity;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 使用真实 MyBatis Mapper 和隔离 MySQL 验证重试后的旧终态清除。 */
@EnabledIfEnvironmentVariable(named = "ENGINEERING_UX_MYSQL_ADMIN_URL", matches = ".+")
class ReplayRetryMySqlTest {
    @Test
    void replayRetryClearsStoredFinishTime() throws Exception {
        String admin = System.getenv("ENGINEERING_UX_MYSQL_ADMIN_URL");
        assertThat(admin).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/\\?.*");
        String user = System.getenv("ENGINEERING_UX_MYSQL_USER");
        String password = System.getenv("ENGINEERING_UX_MYSQL_PASSWORD");
        String database = "agentdoc_p7_retry_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String url = admin.replace("/?", "/" + database + "?");
        try (var connection = DriverManager.getConnection(admin, user, password); var sql = connection.createStatement()) {
            sql.execute("CREATE DATABASE `" + database + "`");
            try {
                try (var target = DriverManager.getConnection(url, user, password); var seed = target.createStatement()) {
                    seed.execute("CREATE TABLE evaluation_run(id BIGINT PRIMARY KEY,space_id BIGINT,status VARCHAR(32),finished_at DATETIME,pause_reason VARCHAR(100),reconciliation_failure_count INT,updated_at DATETIME)");
                    seed.execute("CREATE TABLE evaluation_case_run(id BIGINT PRIMARY KEY,current_attempt_id BIGINT,status VARCHAR(32),updated_at DATETIME)");
                    seed.execute("CREATE TABLE evaluation_case_attempt(id BIGINT PRIMARY KEY,case_run_id BIGINT,run_id BIGINT,space_id BIGINT,attempt_no INT,status VARCHAR(32),created_at DATETIME)");
                    seed.execute("INSERT INTO evaluation_run VALUES(71,9,'COMPLETED','2026-10-01 12:00:00','OLD',1,NOW())");
                    seed.execute("INSERT INTO evaluation_case_run VALUES(81,91,'FAILED',NOW())");
                }
                var config = new MybatisConfiguration();
                config.setMapUnderscoreToCamelCase(true);
                config.setEnvironment(new Environment("p7-retry", new JdbcTransactionFactory(),
                        new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, user, password)));
                config.addMapper(EvaluationRunMapper.class);
                config.addMapper(EvaluationCaseRunMapper.class);
                config.addMapper(EvaluationCaseAttemptMapper.class);
                try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession()) {
                    var service = new EvaluationRunPersistenceService(session.getMapper(EvaluationRunMapper.class),
                            session.getMapper(EvaluationCaseRunMapper.class), session.getMapper(EvaluationCaseAttemptMapper.class),
                            mock(WorkerCapabilitySegmentService.class));
                    var run = new EvaluationRunEntity();
                    run.setId(71L); run.setSpaceId(9L); run.setFinishedAt(LocalDateTime.of(2026, 10, 1, 12, 0));
                    var caseRun = new EvaluationCaseRunEntity(); caseRun.setId(81L);
                    var previous = new EvaluationCaseAttemptEntity(); previous.setAttemptNo(1);
                    var retry = service.createReplayRetry(run, caseRun, previous);
                    try (var result = session.getConnection().createStatement().executeQuery(
                            "SELECT status,finished_at,pause_reason FROM evaluation_run WHERE id=71")) {
                        assertThat(result.next()).isTrue();
                        assertThat(result.getString(1)).isEqualTo("DISPATCHING");
                        assertThat(result.getTimestamp(2)).isNull();
                        assertThat(result.getString(3)).isNull();
                    }
                    try (var result = session.getConnection().createStatement().executeQuery(
                            "SELECT current_attempt_id,status FROM evaluation_case_run WHERE id=81")) {
                        assertThat(result.next()).isTrue();
                        assertThat(result.getLong(1)).isEqualTo(retry.attempt().getId());
                        assertThat(result.getString(2)).isEqualTo("CREATED");
                    }
                }
            } finally {
                sql.execute("DROP DATABASE `" + database + "`");
            }
        }
    }
}
