package com.agentdoc.auth.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** 创建并清理本测试拥有的隔离库，绝不对应用库执行迁移。 */
@EnabledIfEnvironmentVariable(named = "ENGINEERING_UX_MYSQL_ADMIN_URL", matches = ".+")
class ExecutionModeReadProjectionMigrationTest {
    @Test void upgradePreservesSnapshotsAndLedgerAndLeavesMissingSourcesUnknown() throws Exception {
        var admin = System.getenv("ENGINEERING_UX_MYSQL_ADMIN_URL");
        assertThat(admin).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/\\?.*");
        var user = System.getenv("ENGINEERING_UX_MYSQL_USER");
        var password = System.getenv("ENGINEERING_UX_MYSQL_PASSWORD");
        var database = "agentdoc_ux_read_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        assertThat(database).matches("agentdoc_ux_read_[a-f0-9]{12}");
        var url = admin.replace("/?", "/" + database + "?");
        try (var connection = DriverManager.getConnection(admin, user, password); var sql = connection.createStatement()) {
            sql.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            try {
                Flyway.configure().dataSource(url, user, password).target("42").load().migrate();
                try (var target = DriverManager.getConnection(url, user, password); var rows = target.createStatement()) {
                    rows.execute("INSERT INTO task(id,task_no,space_id,name,root_task_id,lineage_type,execution_mode) VALUES(11,'T-11',7,'生产',11,'ORIGINAL','LIVE'),(12,'T-12',7,'隔离',12,'REPLAY','ISOLATED'),(13,'T-13',8,'其他空间',13,'ORIGINAL','LIVE')");
                    for (int index = 0; index < 4; index++) {
                        long task = index == 3 ? 99 : 11 + index;
                        rows.execute("INSERT INTO agent_execution(id,a2a_task_id,a2a_context_id,workbench_task_id,space_id,agent_id,agent_config_version,system_prompt_snapshot,model_snapshot,prompt_hash,status,execution_snapshot_json,execution_snapshot_hash,skill_selection_mode,skill_selection_effective_mode) VALUES("
                                + (21 + index) + ",'a" + index + "','ctx'," + task + ",7,1,1,'system','{}',REPEAT('a',64),'COMPLETED','frozen',REPEAT('b',64),'ALL_BOUND','ALL_BOUND')");
                    }
                    rows.execute("INSERT INTO token_usage_detail(id,space_id,task_id,execution_id,agent_id,model_id,input_tokens,output_tokens,call_time,estimated_cost) VALUES(31,7,11,21,1,1,123,45,NOW(),0.12)");
                    rows.execute("CREATE TEMPORARY TABLE original_checksums AS SELECT version,checksum FROM flyway_schema_history WHERE success=1");
                    var migration = Flyway.configure().dataSource(url, user, password).target("43").load();
                    assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
                    migration.validate(); assertThat(migration.migrate().migrationsExecuted).isZero();
                    try (var result = rows.executeQuery("SELECT execution_mode,execution_snapshot_json,execution_snapshot_hash,prompt_hash FROM agent_execution ORDER BY id")) {
                        for (var expected : new String[]{"LIVE", "ISOLATED", null, null}) {
                            assertThat(result.next()).isTrue(); assertThat(result.getString(1)).isEqualTo(expected);
                            assertThat(result.getString(2)).isEqualTo("frozen"); assertThat(result.getString(3)).isEqualTo("b".repeat(64)); assertThat(result.getString(4)).isEqualTo("a".repeat(64));
                        }
                    }
                    try (var result = rows.executeQuery("SELECT input_tokens,output_tokens,estimated_cost FROM token_usage_detail WHERE id=31")) {
                        result.next(); assertThat(result.getLong(1)).isEqualTo(123); assertThat(result.getLong(2)).isEqualTo(45); assertThat(result.getBigDecimal(3)).isEqualByComparingTo("0.12");
                    }
                    try (var result = rows.executeQuery("SELECT COUNT(*) FROM original_checksums o JOIN flyway_schema_history n ON o.version=n.version WHERE NOT(o.checksum <=> n.checksum)")) {
                        result.next(); assertThat(result.getLong(1)).isZero();
                    }
                }
            } finally {
                sql.execute("DROP DATABASE `" + database + "`");
            }
        }
    }
}
