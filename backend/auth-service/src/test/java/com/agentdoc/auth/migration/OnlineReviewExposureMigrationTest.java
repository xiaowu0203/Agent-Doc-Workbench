package com.agentdoc.auth.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.*;

/** V38 新装/升级在本批具名隔离库执行，历史反馈与迁移校验值保持原样。 */
@EnabledIfEnvironmentVariable(named = "P603_CHANGE_FRESH_MYSQL_URL", matches = ".+")
class OnlineReviewExposureMigrationTest {
    private String url(String kind) {
        String key = kind.equals("evaluation") ? "P603_EVALUATION_MYSQL_URL" : "P603_CHANGE_" + kind.toUpperCase() + "_MYSQL_URL";
        String value = System.getenv(key);
        assertThat(value).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/agentdoc_p603_(change_fresh|change_upgrade|evaluation)_[a-f0-9]{12}\\?.*"); return value;
    }
    private Flyway migration(String kind, String target) {
        return Flyway.configure().dataSource(url(kind), System.getenv("P603_MYSQL_USER"), System.getenv("P603_MYSQL_PASSWORD"))
                .locations("classpath:db/migration").target(target).load();
    }
    @Test void freshMigrationCreatesUniqueActorAndKeepsFirstIdentity() throws Exception {
        var flyway = migration("fresh", "38"); assertThat(flyway.migrate().migrationsExecuted).isEqualTo(38); flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        try (var connection = DriverManager.getConnection(url("fresh"), System.getenv("P603_MYSQL_USER"), System.getenv("P603_MYSQL_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.execute("INSERT INTO online_review_exposure(id,experiment_id,space_id,actor_id,source,captured_at,source_hash) VALUES(91,11,10,501,'EXPERIMENT_DETAIL','2026-10-12 12:00:00.123',REPEAT('a',64))");
            assertThatThrownBy(() -> statement.execute("INSERT INTO online_review_exposure(id,experiment_id,space_id,actor_id,source,captured_at,source_hash) VALUES(92,11,10,501,'ASSIGNMENT_LIST','2026-10-12 13:00:00.123',REPEAT('b',64))")).isInstanceOf(SQLException.class);
        }
    }
    @Test void upgradePreservesAllPreviousChecksumsAndFeedbackAndDoesNotBackfillKnowledge() throws Exception {
        migration("upgrade", "37").migrate();
        try (var connection = DriverManager.getConnection(url("upgrade"), System.getenv("P603_MYSQL_USER"), System.getenv("P603_MYSQL_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.execute("CREATE TEMPORARY TABLE p603_review_history AS SELECT version,checksum FROM flyway_schema_history WHERE success=1");
            statement.execute("INSERT INTO evaluation_feedback(id,space_id,task_id,execution_id,source_type,source_business_id,source_hash,label,score,rubric_score,blindness,created_by) VALUES(91,10,61,71,'MANUAL','91',REPEAT('a',64),'NEEDS_CHANGES',0.25,1,'UNKNOWN',501)");
            assertThat(migration("upgrade", "38").migrate().migrationsExecuted).isEqualTo(1); migration("upgrade", "38").validate();
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM p603_review_history h JOIN flyway_schema_history n ON h.version=n.version WHERE NOT(h.checksum <=> n.checksum)")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getLong(1)).isZero();
            }
            try (var rows = statement.executeQuery("SELECT score,source_hash,blindness FROM evaluation_feedback WHERE id=91")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("0.25");
                assertThat(rows.getString(2)).isEqualTo("a".repeat(64)); assertThat(rows.getString(3)).isEqualTo("UNKNOWN");
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM online_review_exposure")) { assertThat(rows.next()).isTrue(); assertThat(rows.getLong(1)).isZero(); }
        }
    }
    @Test void prepareOwnedEvaluationDatabase() {
        var flyway = migration("evaluation", "38"); assertThat(flyway.migrate().migrationsExecuted).isEqualTo(38); flyway.validate();
    }
}
