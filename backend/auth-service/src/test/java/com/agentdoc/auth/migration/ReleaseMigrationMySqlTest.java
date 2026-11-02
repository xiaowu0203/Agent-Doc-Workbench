package com.agentdoc.auth.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 在独立临时库验证发布迁移，不使用应用数据库。 */
@EnabledIfEnvironmentVariable(named = "ENGINEERING_UX_MYSQL_ADMIN_URL", matches = ".+")
class ReleaseMigrationMySqlTest {

    @Test
    void freshInstallAndV22UpgradeReachV44() throws Exception {
        String admin = System.getenv("ENGINEERING_UX_MYSQL_ADMIN_URL");
        assertThat(admin).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/\\?.*");
        String user = System.getenv("ENGINEERING_UX_MYSQL_USER");
        String password = System.getenv("ENGINEERING_UX_MYSQL_PASSWORD");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String fresh = "agentdoc_p7_fresh_" + suffix;
        String upgrade = "agentdoc_p7_upgrade_" + suffix;
        try (var connection = DriverManager.getConnection(admin, user, password);
             var sql = connection.createStatement()) {
            try {
                sql.execute("CREATE DATABASE `" + fresh + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                sql.execute("CREATE DATABASE `" + upgrade + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                String freshUrl = admin.replace("/?", "/" + fresh + "?");
                String upgradeUrl = admin.replace("/?", "/" + upgrade + "?");
                Flyway freshFlyway = Flyway.configure().dataSource(freshUrl, user, password).load();
                assertThat(freshFlyway.migrate().migrationsExecuted).isEqualTo(44);
                freshFlyway.validate();
                assertThat(freshFlyway.migrate().migrationsExecuted).isZero();

                Flyway oldFlyway = Flyway.configure().dataSource(upgradeUrl, user, password).target("22").load();
                assertThat(oldFlyway.migrate().migrationsExecuted).isEqualTo(22);
                try (var target = DriverManager.getConnection(upgradeUrl, user, password);
                     var rows = target.createStatement()) {
                    rows.execute("INSERT INTO document(id,space_id,title,content,version) VALUES (101,7,'历史文档','历史正文',1)");
                    rows.execute("INSERT INTO document_version(id,document_id,version_no,content,content_sha256) VALUES (102,101,1,'历史正文',SHA2('历史正文',256))");
                    rows.execute("INSERT INTO task(id,task_no,space_id,document_id,document_type,name,instruction,status) VALUES (201,'T-P7-201',7,101,1,'历史任务','历史指令',2)");
                    rows.execute("CREATE TEMPORARY TABLE p7_checksums AS SELECT version,checksum FROM flyway_schema_history WHERE success=1");
                    Flyway upgraded = Flyway.configure().dataSource(upgradeUrl, user, password).load();
                    assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(22);
                    upgraded.validate();
                    assertThat(upgraded.migrate().migrationsExecuted).isZero();
                    try (var result = rows.executeQuery("SELECT d.title,d.content,d.version,v.content,t.root_task_id,t.lineage_type,t.execution_mode,t.instruction FROM document d JOIN document_version v ON v.document_id=d.id JOIN task t ON t.document_id=d.id WHERE d.id=101 AND t.id=201")) {
                        assertThat(result.next()).isTrue();
                        assertThat(result.getString(1)).isEqualTo("历史文档");
                        assertThat(result.getString(2)).isEqualTo("历史正文");
                        assertThat(result.getLong(3)).isEqualTo(1);
                        assertThat(result.getString(4)).isEqualTo("历史正文");
                        assertThat(result.getLong(5)).isEqualTo(201);
                        assertThat(result.getString(6)).isEqualTo("ORIGINAL");
                        assertThat(result.getString(7)).isEqualTo("LIVE");
                        assertThat(result.getString(8)).isEqualTo("历史指令");
                    }
                    try (var result = rows.executeQuery("SELECT COUNT(*) FROM p7_checksums o JOIN flyway_schema_history n ON o.version=n.version WHERE NOT(o.checksum <=> n.checksum)")) {
                        assertThat(result.next()).isTrue();
                        assertThat(result.getLong(1)).isZero();
                    }
                }
            } finally {
                sql.execute("DROP DATABASE IF EXISTS `" + fresh + "`");
                sql.execute("DROP DATABASE IF EXISTS `" + upgrade + "`");
            }
        }
    }
}
