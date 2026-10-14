package com.agentdoc.auth.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** 只接受专用隔离库；不连接业务库，不调用模型。 */
@EnabledIfEnvironmentVariable(named = "P6_RESTART_MYSQL_FRESH_URL", matches = ".+")
class OnlineAbMigrationTest {
    private String url(String kind) {
        String value = System.getenv("P6_RESTART_MYSQL_" + kind.toUpperCase() + "_URL");
        assertThat(value).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/agentdoc_p601_new_" + kind + "_[a-f0-9]{12}\\?.*");
        return value;
    }
    private Connection connect(String kind) throws SQLException {
        return DriverManager.getConnection(url(kind), System.getenv("P6_RESTART_MYSQL_USER"), System.getenv("P6_RESTART_MYSQL_PASSWORD"));
    }
    private Flyway flyway(String kind, String target) {
        return Flyway.configure().dataSource(url(kind), System.getenv("P6_RESTART_MYSQL_USER"),
                System.getenv("P6_RESTART_MYSQL_PASSWORD")).locations("classpath:db/migration").target(target).load();
    }
    private static void sql(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
    private static String value(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }
    private static String experiment(long identity, long space, long agent, long creator, String key, String status, Integer slot, int schema) {
        return "INSERT INTO online_experiment(id,space_id,agent_id,name,client_request_key,request_hash,manifest_schema_version,"
                + "manifest_json,manifest_hash,status,active_slot,authorized_token_budget,max_task_count,created_by) VALUES ("
                + identity + "," + space + "," + agent + ",'synthetic','" + key + "','" + "a".repeat(64) + "',"
                + schema + ",'{}','" + "b".repeat(64) + "','" + status + "'," + slot + ",10000,10," + creator + ")";
    }
    private static String result(long identity, String subject, String offline, String online) {
        return "INSERT INTO evaluation_result(id,space_id,run_id,case_attempt_id,evaluator_version_id,evaluation_attempt_no,status,"
                + "summary_code,implementation_version,started_at,finished_at,subject_type,online_assignment_id,online_evaluation_attempt_id,task_id,execution_id)"
                + " VALUES (" + identity + ",1," + offline + ",99,1,'PASSED','synthetic','test',NOW(),NOW(),'" + subject + "'," + online + ")";
    }

    @Test
    void freshInstallationApplies31OnceAndEnforcesSubjectsAndActorScopedRequests() throws Exception {
        var flyway = flyway("fresh", "31");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(31);
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        try (var connection = connect("fresh")) {
            assertThat(value(connection, "SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name LIKE '%subject%' AND event_object_table IN ('evaluation_result','evaluation_metric','evaluation_evidence_reference')")).isEqualTo("6");
            sql(connection, result(90001, "CASE_ATTEMPT", "11,12", "NULL,NULL,NULL,NULL"));
            sql(connection, result(90002, "ONLINE_TASK", "NULL,NULL", "21,22,23,24"));
            assertThatThrownBy(() -> sql(connection, result(90003, "ONLINE_TASK", "11,12", "21,25,23,24")))
                    .isInstanceOf(SQLException.class).hasMessageContaining("EVALUATION_SUBJECT_INVALID");
            assertThatThrownBy(() -> sql(connection, result(90004, "ONLINE_TASK", "NULL,NULL", "21,26,23,NULL")))
                    .isInstanceOf(SQLException.class).hasMessageContaining("EVALUATION_SUBJECT_INVALID");
            assertThatThrownBy(() -> sql(connection, "UPDATE evaluation_result SET task_id=23 WHERE id=90001"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("EVALUATION_SUBJECT_INVALID");
            sql(connection, experiment(90101, 1, 1, 10, "same", "CREATED", null, 2));
            sql(connection, experiment(90102, 1, 2, 20, "same", "CREATED", null, 2));
            assertThatThrownBy(() -> sql(connection, experiment(90103, 1, 2, 10, "same", "CREATED", null, 2)))
                    .isInstanceOf(SQLException.class);
            String metricColumns = "id,space_id,run_id,case_run_id,case_attempt_id,test_case_version_id,contract_version,source,producer_id,metric_key,value_type,boolean_value,unit,direction,subject_type,online_assignment_id,online_evaluation_attempt_id,task_id,execution_id";
            sql(connection, "INSERT INTO evaluation_metric(" + metricColumns + ") VALUES (90201,1,11,12,13,14,1,'EVALUATOR',7,'same','BOOLEAN',0,'boolean','HIGHER_IS_BETTER','CASE_ATTEMPT',NULL,NULL,NULL,NULL)");
            sql(connection, "INSERT INTO evaluation_metric(" + metricColumns + ") VALUES (90202,1,NULL,NULL,NULL,NULL,1,'EVALUATOR',7,'same','BOOLEAN',0,'boolean','HIGHER_IS_BETTER','ONLINE_TASK',21,22,23,24)");
            assertThat(value(connection, "SELECT COUNT(*) FROM evaluation_metric WHERE producer_id=7 AND boolean_value=0")).isEqualTo("2");
            assertThatThrownBy(() -> sql(connection, "UPDATE evaluation_metric SET case_attempt_id=13 WHERE id=90202"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("EVALUATION_SUBJECT_INVALID");
            sql(connection, "INSERT INTO evaluation_evidence_reference(id,space_id,case_attempt_id,evidence_type,business_id) VALUES(90301,1,12,'TASK','synthetic')");
            assertThatThrownBy(() -> sql(connection, "UPDATE evaluation_evidence_reference SET subject_type='ONLINE_TASK' WHERE id=90301"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("EVALUATION_SUBJECT_INVALID");
        }
    }

    @Test
    void upgradeKeepsHistoricalResultFeedbackReportAndSchemaOneVerbatim() throws Exception {
        var before = flyway("upgrade", "30");
        assertThat(before.migrate().migrationsExecuted).isEqualTo(30);
        try (var connection = connect("upgrade")) {
            sql(connection, result(91001, "CASE_ATTEMPT", "11,12", "NULL,NULL,NULL,NULL"));
            sql(connection, "INSERT INTO evaluation_feedback(id,space_id,source_type,label,score,facts_json,created_by) VALUES(91002,1,'MANUAL','ACCEPTED',0.75,'{\"history\":true}',10)");
            sql(connection, "INSERT INTO experiment_report(id,experiment_id,space_id,revision,manifest_hash,calculation_schema_version,calculation_input_hash,selected_record_ids_json,report_schema_version,report_json,content_hash,generated_by) VALUES(91003,2,1,1,'" + "a".repeat(64) + "',1,'" + "b".repeat(64) + "','[]',1,'{\"history\":true}','" + "c".repeat(64) + "',10)");
            sql(connection, experiment(91004, 1, 1, 10, "legacy", "CREATED", null, 1));
        }
        var after = flyway("upgrade", "31");
        assertThat(after.migrate().migrationsExecuted).isEqualTo(1);
        after.validate();
        try (var connection = connect("upgrade")) {
            assertThat(value(connection, "SELECT CONCAT(run_id,':',case_attempt_id,':',summary_code) FROM evaluation_result WHERE id=91001")).isEqualTo("11:12:synthetic");
            assertThat(value(connection, "SELECT CONCAT(score,':',facts_json) FROM evaluation_feedback WHERE id=91002")).isEqualTo("0.750000:{\"history\":true}");
            assertThat(value(connection, "SELECT CONCAT(content_hash,':',report_json) FROM experiment_report WHERE id=91003")).isEqualTo("c".repeat(64) + ":{\"history\":true}");
            assertThat(value(connection, "SELECT CONCAT(manifest_schema_version,':',manifest_json) FROM online_experiment WHERE id=91004")).isEqualTo("1:{}");
        }
    }

    @Test
    void migrationBlocksLegacyActiveRowsBeforeAnyPermanentSchemaChange() throws Exception {
        flyway("conflict", "30").migrate();
        try (var connection = connect("conflict")) {
            sql(connection, experiment(92001, 1, 1, 10, "legacy-active", "ACTIVE", 1, 1));
        }
        assertThatThrownBy(() -> flyway("conflict", "31").migrate()).isInstanceOf(RuntimeException.class);
        try (var connection = connect("conflict")) {
            assertThat(value(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='agent_online_config'")).isEqualTo("0");
            assertThat(value(connection, "SELECT CONCAT(manifest_schema_version,':',status,':',active_slot) FROM online_experiment WHERE id=92001")).isEqualTo("1:ACTIVE:1");
        }
    }

    @Test
    void spaceUniqueSlotRejectsConcurrentDifferentAgents() throws Exception {
        // 升级库由独立测试方法准备，避免依赖 JUnit 方法顺序。
        var flyway = flyway("race", "31");
        flyway.migrate();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int n = 0; n < 2; n++) {
                final int index = n;
                futures.add(executor.submit(() -> {
                    try (var connection = connect("race")) {
                        barrier.await(10, TimeUnit.SECONDS);
                        try {
                            sql(connection, experiment(93001 + index, 3, 10 + index, 10, "race-" + index, "ACTIVE", 1, 2));
                            return true;
                        } catch (SQLException uniqueConflict) {
                            assertThat(uniqueConflict.getErrorCode()).isEqualTo(1062);
                            return false;
                        }
                    }
                }));
            }
            int successes = 0;
            for (var future : futures) { if (future.get(20, TimeUnit.SECONDS)) { successes++; } }
            assertThat(successes).isEqualTo(1);
            try (var connection = connect("race")) {
                assertThat(value(connection, "SELECT COUNT(*) FROM online_experiment WHERE space_id=3 AND active_slot=1")).isEqualTo("1");
            }
        } finally { executor.shutdownNow(); }
    }

    @Test
    void representativeListAndAssignmentQueriesHaveUsableIndices() throws Exception {
        flyway("api", "31").migrate();
        try (var connection = connect("api")) {
            for (int n = 0; n < 100; n++) {
                sql(connection, experiment(95001 + n, 10000 + n % 10, 1 + n % 4, 10, "representative-" + n, "CREATED", null, 2));
            }
            String insert = "INSERT INTO online_assignment(id,experiment_id,space_id,agent_id,task_id,document_id,created_by,client_request_key,request_hash,input_snapshot_schema_version,input_snapshot_hash,bucket,variant,config_schema_version,config_hash,binding_schema_version,binding_hash,reserved_token_budget,accepted_sequence) VALUES(?,95001,10000,1,?,?,10,?,? ,1,?,5000,'BASELINE',2,?,2,?,1000,?)";
            try (var statement = connection.prepareStatement(insert)) {
                for (int n = 0; n < 10000; n++) {
                    statement.setLong(1, 960000 + n);
                    statement.setLong(2, 10000000 + n);
                    statement.setLong(3, 300000 + n % 1000);
                    statement.setString(4, "task-" + n);
                    for (int column = 5; column <= 8; column++) { statement.setString(column, "a".repeat(64)); }
                    statement.setLong(9, n + 1);
                    statement.addBatch();
                    if ((n + 1) % 1000 == 0) { statement.executeBatch(); }
                }
            }
            sql(connection, "ANALYZE TABLE online_experiment,online_assignment");
            for (String query : List.of(
                    "SELECT * FROM online_experiment WHERE space_id=10000 AND agent_id=1 AND status='CREATED' ORDER BY id DESC LIMIT 10",
                    "SELECT * FROM online_assignment WHERE experiment_id=95001 AND space_id=10000 AND binding_schema_version=2 ORDER BY id DESC LIMIT 10",
                    "SELECT * FROM online_assignment WHERE experiment_id=95001 AND document_id=300001 ORDER BY id DESC LIMIT 10",
                    "SELECT experiment_id,COUNT(DISTINCT document_id) FROM online_assignment WHERE binding_schema_version=2 AND experiment_id IN(95001) GROUP BY experiment_id")) {
                try (var statement = connection.createStatement(); var result = statement.executeQuery("EXPLAIN " + query)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("key")).isNotBlank();
                    assertThat(result.getString("type")).isNotEqualTo("ALL");
                    System.out.println("P6_EXPLAIN key=" + result.getString("key") + " type=" + result.getString("type")
                            + " estimatedRows=" + result.getString("rows") + " extra=" + result.getString("Extra"));
                }
            }
        }
    }

}
