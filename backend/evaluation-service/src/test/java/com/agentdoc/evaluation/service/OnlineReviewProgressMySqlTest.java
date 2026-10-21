package com.agentdoc.evaluation.service;

import com.agentdoc.evaluation.mapper.EvaluationFeedbackMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** 仅在本测试创建的隔离库验证当前用户/空间/页面ID范围，不操作业务库。 */
@EnabledIfEnvironmentVariable(named = "ENGINEERING_UX_MYSQL_ADMIN_URL", matches = ".+")
class OnlineReviewProgressMySqlTest {
    @Test void ownProgressIsDistinctAndCannotRevealOtherActorsOrSpaces() throws Exception {
        var admin = System.getenv("ENGINEERING_UX_MYSQL_ADMIN_URL");
        assertThat(admin).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/\\?.*");
        var user = System.getenv("ENGINEERING_UX_MYSQL_USER");
        var password = System.getenv("ENGINEERING_UX_MYSQL_PASSWORD");
        var database = "agentdoc_review_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        assertThat(database).matches("agentdoc_review_[a-f0-9]{12}");
        var url = admin.replace("/?", "/" + database + "?");
        try (var connection = DriverManager.getConnection(admin, user, password); var sql = connection.createStatement()) {
            sql.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4");
            try {
                try (var target = DriverManager.getConnection(url, user, password); var rows = target.createStatement()) {
                    rows.execute("CREATE TABLE evaluation_feedback(id BIGINT PRIMARY KEY,space_id BIGINT,created_by BIGINT,online_assignment_id BIGINT,source_type VARCHAR(16),rubric_score INT)");
                    rows.execute("INSERT INTO evaluation_feedback VALUES(1,7,501,51,'MANUAL',0),(2,7,501,51,'MANUAL',4),(3,7,502,52,'MANUAL',3),(4,8,501,52,'MANUAL',2),(5,7,501,53,'CHANGE_REQUEST',3),(6,7,501,54,'MANUAL',NULL)");
                }
                var config = new MybatisConfiguration();
                config.setEnvironment(new Environment("review-progress", new JdbcTransactionFactory(), new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, user, password)));
                config.addMapper(EvaluationFeedbackMapper.class);
                try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession()) {
                    var mapper = session.getMapper(EvaluationFeedbackMapper.class);
                    assertThat(mapper.ownOnlineReviewedAssignments(7L, 501L, List.of(51L, 52L, 53L, 54L))).containsExactly(51L);
                    assertThat(mapper.ownOnlineReviewedAssignments(7L, 502L, List.of(51L, 52L))).containsExactly(52L);
                    assertThat(mapper.ownOnlineReviewedAssignments(7L, 501L, List.of(52L))).isEmpty();
                    assertThat(mapper.ownOnlineReviewedAssignments(8L, 501L, List.of(51L, 52L))).containsExactly(52L);
                }
            } finally { sql.execute("DROP DATABASE `" + database + "`"); }
        }
    }
}
