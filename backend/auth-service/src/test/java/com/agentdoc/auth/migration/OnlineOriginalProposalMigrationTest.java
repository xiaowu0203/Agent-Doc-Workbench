package com.agentdoc.auth.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.*;

/** 本批隔离库验证 V37 新装、升级、原始 Unicode 与不可覆盖主键。 */
@EnabledIfEnvironmentVariable(named = "P603_CHANGE_FRESH_MYSQL_URL", matches = ".+")
class OnlineOriginalProposalMigrationTest {
    private String url(String kind) {
        String value = System.getenv("P603_CHANGE_"+kind.toUpperCase()+"_MYSQL_URL");
        assertThat(value).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/agentdoc_p603_change_"+kind+"_[a-f0-9]{12}\\?.*"); return value;
    }
    private Flyway flyway(String kind, String target) {
        return Flyway.configure().dataSource(url(kind),System.getenv("P603_MYSQL_USER"),System.getenv("P603_MYSQL_PASSWORD"))
                .locations("classpath:db/migration").target(target).load();
    }
    @Test void freshMigrationPreservesOriginalProposalAndRejectsDuplicateIdentity() throws Exception {
        var migration = flyway("fresh","37"); assertThat(migration.migrate().migrationsExecuted).isEqualTo(37); migration.validate();
        assertThat(migration.migrate().migrationsExecuted).isZero();
        String changes = "[{\"op\":\"replace\",\"newText\":\" 原始😀\\r\\n提案 \"}]";
        try (var connection = DriverManager.getConnection(url("fresh"),System.getenv("P603_MYSQL_USER"),System.getenv("P603_MYSQL_PASSWORD"));
             var insert = connection.prepareStatement("INSERT INTO task_online_original_proposal(id,task_id,space_id,agent_id,experiment_id,assignment_id,document_id,base_version,input_hash,binding_hash,payload_hash,identity_hash,original_changes,captured_at) VALUES(91,61,10,20,11,51,41,3,?,?,?,?,?,'2026-10-10 20:00:00.123')")) {
            for(int index=1;index<=4;index++) { insert.setString(index,"a".repeat(64)); } insert.setString(5,changes); insert.executeUpdate();
            assertThatThrownBy(insert::executeUpdate).isInstanceOf(SQLException.class);
            try(var statement=connection.createStatement(); var rows=statement.executeQuery("SELECT original_changes FROM task_online_original_proposal WHERE id=91")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo(changes);
            }
        }
    }
    @Test void upgradeFrom36LeavesHistoricalChecksumsAndDoesNotBackfillProposals() throws Exception {
        flyway("upgrade","36").migrate();
        try(var connection=DriverManager.getConnection(url("upgrade"),System.getenv("P603_MYSQL_USER"),System.getenv("P603_MYSQL_PASSWORD")); var statement=connection.createStatement()) {
            statement.execute("CREATE TEMPORARY TABLE p603_change_history AS SELECT version,checksum FROM flyway_schema_history WHERE success=1");
            assertThat(flyway("upgrade","37").migrate().migrationsExecuted).isEqualTo(1); flyway("upgrade","37").validate();
            try(var rows=statement.executeQuery("SELECT COUNT(*) FROM p603_change_history h JOIN flyway_schema_history n ON h.version=n.version WHERE NOT(h.checksum <=> n.checksum)")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getLong(1)).isZero();
            }
            try(var rows=statement.executeQuery("SELECT COUNT(*) FROM task_online_original_proposal")) { assertThat(rows.next()).isTrue(); assertThat(rows.getLong(1)).isZero(); }
        }
    }
    @Test void prepareOwnedTaskAndEvaluationDatabase() {
        String value=System.getenv("P603_EVALUATION_MYSQL_URL");
        assertThat(value).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/agentdoc_p603_evaluation_[a-f0-9]{12}\\?.*");
        var migration=Flyway.configure().dataSource(value,System.getenv("P603_MYSQL_USER"),System.getenv("P603_MYSQL_PASSWORD"))
                .locations("classpath:db/migration").target("37").load();
        assertThat(migration.migrate().migrationsExecuted).isEqualTo(37); migration.validate();
    }
}
