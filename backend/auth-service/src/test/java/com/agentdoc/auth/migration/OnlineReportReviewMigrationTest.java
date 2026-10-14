package com.agentdoc.auth.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.*;

/** 仅在本批拥有的隔离库验证 V39～V42；不补造历史盲态或结论哈希。 */
@EnabledIfEnvironmentVariable(named = "P603_CHANGE_FRESH_MYSQL_URL", matches = ".+")
class OnlineReportReviewMigrationTest {
    private String url(String kind) {
        String value = System.getenv(kind.equals("evaluation") ? "P603_EVALUATION_MYSQL_URL" : "P603_CHANGE_" + kind.toUpperCase() + "_MYSQL_URL");
        assertThat(value).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/agentdoc_p603_(change_fresh|change_upgrade|evaluation)_[a-f0-9]{12}\\?.*"); return value;
    }
    private Flyway migration(String kind, String target) {
        return Flyway.configure().dataSource(url(kind), System.getenv("P603_MYSQL_USER"), System.getenv("P603_MYSQL_PASSWORD"))
                .locations("classpath:db/migration").target(target).load();
    }
    @Test void freshAndRetryRetainReceiptUniqueness() throws Exception {
        var flyway = migration("fresh", "42"); assertThat(flyway.migrate().migrationsExecuted).isEqualTo(42); flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        try (var connection = DriverManager.getConnection(url("fresh"), System.getenv("P603_MYSQL_USER"), System.getenv("P603_MYSQL_PASSWORD")); var sql = connection.createStatement()) {
            String values = "11,10,31,501,61,71,REPEAT('a',64),'ORIGINAL_TEXT',71,REPEAT('b',64),1,'2026-10-12 12:00:00.123',REPEAT('c',64)";
            String insert = "INSERT INTO online_blind_review_receipt(id,experiment_id,space_id,assignment_id,actor_id,task_id,execution_id,input_hash,original_evidence_type,original_evidence_id,original_evidence_hash,policy_version,captured_at,source_hash) VALUES(";
            sql.execute(insert + "91," + values + ")");
            assertThatThrownBy(() -> sql.execute(insert + "92," + values + ")")).isInstanceOf(SQLException.class);
        }
    }
    @Test void upgradePreservesChecksumsFeedbackAndExistingReports() throws Exception {
        migration("upgrade", "38").migrate();
        try (var connection = DriverManager.getConnection(url("upgrade"), System.getenv("P603_MYSQL_USER"), System.getenv("P603_MYSQL_PASSWORD")); var sql = connection.createStatement()) {
            sql.execute("CREATE TEMPORARY TABLE p603_report_history AS SELECT version,checksum FROM flyway_schema_history WHERE success=1");
            sql.execute("INSERT INTO online_experiment_report(id,experiment_id,space_id,revision,schema_version,report_hash,input_hash,input_json,report_json,observed_at,created_by) VALUES(91,11,10,1,1,REPEAT('a',64),REPEAT('b',64),'old input','old report','2026-10-12',501)");
            assertThat(migration("upgrade", "42").migrate().migrationsExecuted).isEqualTo(4); migration("upgrade", "42").validate();
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM p603_report_history h JOIN flyway_schema_history n ON h.version=n.version WHERE NOT(h.checksum <=> n.checksum)")) { rows.next(); assertThat(rows.getLong(1)).isZero(); }
            try (var rows = sql.executeQuery("SELECT report_json,report_hash FROM online_experiment_report WHERE id=91")) { rows.next(); assertThat(rows.getString(1)).isEqualTo("old report"); assertThat(rows.getString(2)).isEqualTo("a".repeat(64)); }
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM online_blind_review_receipt")) { rows.next(); assertThat(rows.getLong(1)).isZero(); }
        }
    }
    @Test void prepareOwnedEvaluationDatabase() { var flyway = migration("evaluation", "42"); assertThat(flyway.migrate().migrationsExecuted).isEqualTo(42); flyway.validate(); }
}
