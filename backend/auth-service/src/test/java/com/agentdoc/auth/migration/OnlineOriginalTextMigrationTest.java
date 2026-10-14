package com.agentdoc.auth.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.*;

/** 只验证本批拥有的隔离数据库；旧迁移目标与 checksum 不变。 */
@EnabledIfEnvironmentVariable(named = "P603_FRESH_MYSQL_URL", matches = ".+")
class OnlineOriginalTextMigrationTest {
    private String url(String kind) {
        String value = System.getenv("P603_" + kind.toUpperCase() + "_MYSQL_URL");
        assertThat(value).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/agentdoc_p603_" + kind + "_[a-f0-9]{12}\\?.*"); return value;
    }
    private Flyway flyway(String kind, String target) {
        return Flyway.configure().dataSource(url(kind), System.getenv("P603_MYSQL_USER"), System.getenv("P603_MYSQL_PASSWORD"))
                .locations("classpath:db/migration").target(target).load();
    }
    private Connection connection(String kind) throws Exception {
        return DriverManager.getConnection(url(kind), System.getenv("P603_MYSQL_USER"), System.getenv("P603_MYSQL_PASSWORD"));
    }
    @Test void freshInstallationIncludes36OnceAndPreservesExactUnicodeText() throws Exception {
        var migration = flyway("fresh","36"); assertThat(migration.migrate().migrationsExecuted).isEqualTo(36); migration.validate();
        assertThat(migration.migrate().migrationsExecuted).isZero();
        String text = " 原始😀\r\n输出 ";
        try (var connection = connection("fresh"); var insert = connection.prepareStatement("INSERT INTO agent_online_original_text(id,task_id,space_id,agent_id,experiment_id,assignment_id,binding_hash,content_hash,identity_hash,original_text,captured_at) VALUES(71,61,10,20,11,51,?,?,?,?,'2026-10-10 20:00:00.123')")) {
            insert.setString(1,"a".repeat(64)); insert.setString(2,"b".repeat(64)); insert.setString(3,"c".repeat(64)); insert.setString(4,text); insert.executeUpdate();
            assertThatThrownBy(insert::executeUpdate).isInstanceOf(SQLException.class);
            try (var select = connection.createStatement(); var rows = select.executeQuery("SELECT original_text FROM agent_online_original_text WHERE id=71")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo(text);
            }
        }
    }
    @Test void upgradeFrom35AddsOnly36WithoutChangingHistoricalChecksums() throws Exception {
        flyway("upgrade","35").migrate();
        try (var connection = connection("upgrade"); var statement = connection.createStatement()) {
            statement.execute("CREATE TEMPORARY TABLE p603_history AS SELECT version,checksum FROM flyway_schema_history WHERE success=1");
            assertThat(flyway("upgrade","36").migrate().migrationsExecuted).isEqualTo(1); flyway("upgrade","36").validate();
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM p603_history h JOIN flyway_schema_history n ON h.version=n.version WHERE NOT(h.checksum <=> n.checksum)")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getLong(1)).isZero();
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM agent_online_original_text")) { assertThat(rows.next()).isTrue(); assertThat(rows.getLong(1)).isZero(); }
        }
    }
    @Test void prepareOwnedEvaluationDatabase() {
        var migration = flyway("evaluation","36"); assertThat(migration.migrate().migrationsExecuted).isEqualTo(36); migration.validate();
    }
}
