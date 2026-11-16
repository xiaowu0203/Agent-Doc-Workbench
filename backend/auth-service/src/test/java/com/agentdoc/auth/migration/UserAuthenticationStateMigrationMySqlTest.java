package com.agentdoc.auth.migration;

import com.agentdoc.auth.mapper.UserMapper;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 仅在专用MySQL5.7随机库验证V46/V47新装、升级及既有凭据保留。 */
@EnabledIfEnvironmentVariable(named = "P1_IDENTITY_FRESH_URL", matches = ".+")
class UserAuthenticationStateMigrationMySqlTest {

    @Test
    void freshDatabaseSupportsExternalAccountsAndNonNullAuthenticationVersion() throws Exception {
        Flyway migration = flyway("FRESH", "47");
        assertThat(migration.migrate().migrationsExecuted).isEqualTo(47);
        migration.validate();
        assertThat(migration.migrate().migrationsExecuted).isZero();

        try (Connection connection = connection("FRESH"); var sql = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseProductVersion()).startsWith("5.7.");
            sql.executeUpdate("INSERT INTO `user` (id, username, password_hash, status) VALUES (9001, 'p1-oidc-only', NULL, 1)");
            try (var rows = sql.executeQuery("SELECT password_hash, auth_version, oidc_auth_not_before FROM `user` WHERE id=9001")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("password_hash")).isNull();
                assertThat(rows.getLong("auth_version")).isZero();
                assertThat(rows.wasNull()).isFalse();
                assertThat(rows.getLong("oidc_auth_not_before")).isZero();
                assertThat(rows.wasNull()).isFalse();
            }
            assertThatThrownBy(() -> sql.executeUpdate("UPDATE `user` SET auth_version=NULL WHERE id=9001"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.executeUpdate("UPDATE `user` SET oidc_auth_not_before=NULL WHERE id=9001"))
                    .isInstanceOf(SQLException.class);
        }
        staleEntityUpdateDoesNotOverwriteAuthenticationVersion();
        concurrentBindingKeepsUniqueIdentity();
    }

    private void staleEntityUpdateDoesNotOverwriteAuthenticationVersion() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("p1-mysql", new JdbcTransactionFactory(),
                new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url("FRESH"),
                        System.getenv("P1_IDENTITY_MYSQL_USER"), System.getenv("P1_IDENTITY_MYSQL_PASSWORD"))));
        configuration.addMapper(UserMapper.class);
        var factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        try (var session = factory.openSession(true);
             Connection connection = connection("FRESH"); var sql = connection.createStatement()) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            UserEntity stale = mapper.selectById(9001L);
            assertThat(stale.getAuthVersion()).isZero();
            sql.executeUpdate("UPDATE `user` SET auth_version=7, oidc_auth_not_before=123 WHERE id=9001");
            stale.setLastLoginAt(LocalDateTime.of(2026, 11, 10, 0, 0));
            assertThat(mapper.updateById(stale)).isEqualTo(1);
            try (var rows = sql.executeQuery("SELECT auth_version, oidc_auth_not_before, last_login_at FROM `user` WHERE id=9001")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("auth_version")).isEqualTo(7);
                assertThat(rows.getLong("oidc_auth_not_before")).isEqualTo(123);
                assertThat(rows.getObject("last_login_at", LocalDateTime.class)).isEqualTo(stale.getLastLoginAt());
            }
            CountDownLatch start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var futures = IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    try (var concurrentSession = factory.openSession(true)) {
                        return concurrentSession.getMapper(UserMapper.class).advanceAuthenticationVersion(9001L, 200 - index);
                    }
                })).toList();
                start.countDown();
                for (var future : futures) {
                    assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo(1);
                }
            }
            assertThat(mapper.advanceAuthenticationVersion(9001L, 100)).isEqualTo(1);
            try (var rows = sql.executeQuery("SELECT auth_version, oidc_auth_not_before FROM `user` WHERE id=9001")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(10);
                assertThat(rows.getLong(2)).isEqualTo(200);
            }
        }
    }

    private void concurrentBindingKeepsUniqueIdentity() throws Exception {
        try (Connection connection = connection("FRESH"); var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO `user` (id, username, password_hash, status) VALUES (9002, 'p1-second-user', NULL, 1)");
        }
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> insertBindingAfterSignal(start, 9101));
            var second = executor.submit(() -> insertBindingAfterSignal(start, 9102));
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        }
        try (Connection connection = connection("FRESH"); var sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM identity_binding WHERE identity_key=UNHEX(REPEAT('ab',32))")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
            sql.executeUpdate("UPDATE identity_binding SET status='REVOKED' WHERE identity_key=UNHEX(REPEAT('ab',32))");
            assertThatThrownBy(() -> sql.executeUpdate("INSERT INTO identity_binding "
                    + "(id, identity_key, issuer, subject, user_id, status, bound_by, bound_at) VALUES "
                    + "(9103, UNHEX(REPEAT('ab',32)), 'https://p1.example.invalid', 'same-sub', 9002, 'ACTIVE', 9001, NOW())"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private int insertBindingAfterSignal(CountDownLatch start, long id) throws Exception {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try (Connection connection = connection("FRESH"); var sql = connection.createStatement()) {
            sql.setQueryTimeout(5);
            return sql.executeUpdate("INSERT INTO identity_binding "
                    + "(id, identity_key, issuer, subject, user_id, status, bound_by, bound_at) VALUES "
                    + "(" + id + ", UNHEX(REPEAT('ab',32)), 'https://p1.example.invalid', 'same-sub', "
                    + (id == 9101 ? 9001 : 9002) + ", 'ACTIVE', 9001, NOW())");
        } catch (SQLException error) {
            if (error.getErrorCode() == 1062) {
                return 0;
            }
            throw error;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"44", "45", "46"})
    void upgradePreservesUsersPasswordsAndHistoricalChecksums(String sourceVersion) throws Exception {
        String kind = "UPGRADE" + sourceVersion;
        flyway(kind, sourceVersion).migrate();
        try (Connection connection = connection(kind); var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO `user` (id, username, password_hash, nickname, status) "
                    + "VALUES (9001, 'p1-existing', 'existing-password-hash', 'existing-nickname', 1)");
            if ("46".equals(sourceVersion)) {
                sql.executeUpdate("UPDATE `user` SET auth_version=7 WHERE id=9001");
            }
            Map<String, Integer> originalChecksums = checksums(connection);

            Flyway migration = flyway(kind, "47");
            assertThat(migration.migrate().migrationsExecuted).isEqualTo(47 - Integer.parseInt(sourceVersion));
            migration.validate();
            assertThat(migration.migrate().migrationsExecuted).isZero();
            assertThat(checksums(connection)).containsAllEntriesOf(originalChecksums);
            try (var rows = sql.executeQuery("SELECT username, password_hash, nickname, status, auth_version, oidc_auth_not_before "
                    + "FROM `user` WHERE id=9001")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("username")).isEqualTo("p1-existing");
                assertThat(rows.getString("password_hash")).isEqualTo("existing-password-hash");
                assertThat(rows.getString("nickname")).isEqualTo("existing-nickname");
                assertThat(rows.getInt("status")).isEqualTo(1);
                assertThat(rows.getLong("auth_version")).isEqualTo("46".equals(sourceVersion) ? 7 : 0);
                assertThat(rows.getLong("oidc_auth_not_before")).isZero();
            }
        }
    }

    private Map<String, Integer> checksums(Connection connection) throws SQLException {
        Map<String, Integer> checksums = new LinkedHashMap<>();
        try (var sql = connection.createStatement();
             var rows = sql.executeQuery("SELECT version, checksum FROM flyway_schema_history WHERE success=1")) {
            while (rows.next()) {
                checksums.put(rows.getString("version"), rows.getInt("checksum"));
            }
        }
        return checksums;
    }

    private Flyway flyway(String kind, String target) {
        return Flyway.configure().dataSource(url(kind), System.getenv("P1_IDENTITY_MYSQL_USER"),
                        System.getenv("P1_IDENTITY_MYSQL_PASSWORD"))
                .locations("classpath:db/migration").target(target).load();
    }

    private Connection connection(String kind) throws SQLException {
        return DriverManager.getConnection(url(kind), System.getenv("P1_IDENTITY_MYSQL_USER"),
                System.getenv("P1_IDENTITY_MYSQL_PASSWORD"));
    }

    private String url(String kind) {
        String url = System.getenv("P1_IDENTITY_" + kind + "_URL");
        assertThat(url).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):13316/"
                    + "adw_p1_identity_(fresh|upgrade44|upgrade45|upgrade46)_[a-f0-9]{12}\\?.*");
        return url;
    }
}
