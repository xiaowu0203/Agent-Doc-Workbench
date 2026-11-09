package com.agentdoc.auth.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 仅操作专用随机测试库；验证MySQL 5.7全新安装及V44升级。 */
@EnabledIfEnvironmentVariable(named = "P0_IDENTITY_FRESH_URL", matches = ".+")
class IdentityBindingMigrationMySqlTest {
    private String url(String kind) {
        String url = System.getenv("P0_IDENTITY_" + kind + "_URL");
        assertThat(url).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):13316/adw_p0_identity_(fresh|upgrade)_[a-f0-9]{12}\\?.*");
        return url;
    }

    private Flyway flyway(String kind, String target) {
        return Flyway.configure().dataSource(url(kind), System.getenv("P0_IDENTITY_MYSQL_USER"), System.getenv("P0_IDENTITY_MYSQL_PASSWORD"))
                .locations("classpath:db/migration").target(target).load();
    }

    @Test
    void freshDatabaseAppliesAndValidatesAllMigrations() {
        var flyway = flyway("FRESH", "45");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(45);
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void upgradePreservesExistingUserAndRejectsDuplicateEvenAfterRevocation() throws Exception {
        flyway("UPGRADE", "44").migrate();
        try (var connection = DriverManager.getConnection(url("UPGRADE"), System.getenv("P0_IDENTITY_MYSQL_USER"), System.getenv("P0_IDENTITY_MYSQL_PASSWORD"));
             var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO `user` (id, username, password_hash, status) VALUES (9001, 'p0-fixture', 'fixture', 1)");
            var upgrade = flyway("UPGRADE", "45");
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            upgrade.validate();
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM `user` WHERE id=9001 AND username='p0-fixture'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
            sql.executeUpdate("INSERT INTO identity_binding(id, identity_key, issuer, subject, user_id, status, bound_by, bound_at) VALUES (1, UNHEX(REPEAT('ab',32)), 'issuer', 'sub', 9001, 'REVOKED', 9001, NOW())");
            assertThatThrownBy(() -> sql.executeUpdate("INSERT INTO identity_binding(id, identity_key, issuer, subject, user_id, status, bound_by, bound_at) VALUES (2, UNHEX(REPEAT('ab',32)), 'issuer', 'sub', 9001, 'ACTIVE', 9001, NOW())"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
