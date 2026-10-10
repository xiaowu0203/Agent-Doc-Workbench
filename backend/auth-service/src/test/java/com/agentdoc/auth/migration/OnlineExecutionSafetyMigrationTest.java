package com.agentdoc.auth.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.Connection;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

/** V32 仅在本批独立创建的 MySQL 中验证，保持已发布迁移校验不变。 */
@EnabledIfEnvironmentVariable(named = "P602_FRESH_MYSQL_URL", matches = ".+")
class OnlineExecutionSafetyMigrationTest {
    private String url(String kind) {
        String value = System.getenv("P602_" + kind.toUpperCase() + "_MYSQL_URL");
        assertThat(value).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/agentdoc_p602_" + kind + "_[a-f0-9]{12}\\?.*"); return value;
    }
    private Flyway flyway(String kind, String target) {
        return Flyway.configure().dataSource(url(kind), System.getenv("P602_MYSQL_USER"), System.getenv("P602_MYSQL_PASSWORD"))
                .locations("classpath:db/migration").target(target).load();
    }
    private Connection connection(String kind) throws Exception {
        return DriverManager.getConnection(url(kind), System.getenv("P602_MYSQL_USER"), System.getenv("P602_MYSQL_PASSWORD"));
    }
    private String scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue(); return result.getString(1);
        }
    }
    @Test void freshInstallationApplies35OnceAndKeepsExistingSubjectTriggers() throws Exception {
        var flyway = flyway("fresh", "35"); assertThat(flyway.migrate().migrationsExecuted).isEqualTo(35);
        flyway.validate(); assertThat(flyway.migrate().migrationsExecuted).isZero();
        try (var connection = connection("fresh")) {
            assertThat(scalar(connection, "SELECT COLUMN_TYPE FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='online_experiment' AND COLUMN_NAME='consumed_tokens'"))
                    .isEqualTo("decimal(38,0)");
            assertThat(scalar(connection, "SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name LIKE '%subject%'"))
                    .isEqualTo("6");
        }
    }
    @Test void upgradePreservesLongMaximumHistoricalConsumptionAndAllowsUnclippedActualCost() throws Exception {
        flyway("upgrade", "31").migrate();
        try (var connection = connection("upgrade"); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO online_experiment(id,space_id,agent_id,name,client_request_key,request_hash,manifest_schema_version,manifest_json,manifest_hash,status,authorized_token_budget,max_task_count,consumed_tokens,created_by) VALUES(1001,1,2,'synthetic','upgrade','" + "a".repeat(64) + "',2,'{}','" + "b".repeat(64) + "','CREATED',100,10,9223372036854775807,3)");
        }
        var flyway = flyway("upgrade", "35"); assertThat(flyway.migrate().migrationsExecuted).isEqualTo(4); flyway.validate();
        try (var connection = connection("upgrade"); var statement = connection.createStatement()) {
            assertThat(scalar(connection, "SELECT consumed_tokens FROM online_experiment WHERE id=1001")).isEqualTo("9223372036854775807");
            statement.execute("UPDATE online_experiment SET consumed_tokens=18446744073709551616 WHERE id=1001");
            assertThat(scalar(connection, "SELECT consumed_tokens FROM online_experiment WHERE id=1001")).isEqualTo("18446744073709551616");
            assertThat(scalar(connection, "SELECT status FROM online_experiment WHERE id=1001")).isEqualTo("CREATED");
        }
    }
    @Test void prepareOwnedKernelDatabaseForIndependentServiceContainers() {
        var flyway = flyway("kernel", "35"); assertThat(flyway.migrate().migrationsExecuted).isEqualTo(35); flyway.validate();
    }
    @Test
    @EnabledIfEnvironmentVariable(named = "P602_INTENT_MYSQL_URL", matches = ".+")
    void prepareOwnedTaskIntentDatabase() {
        var flyway = flyway("intent", "35"); assertThat(flyway.migrate().migrationsExecuted).isEqualTo(35); flyway.validate();
    }
}
