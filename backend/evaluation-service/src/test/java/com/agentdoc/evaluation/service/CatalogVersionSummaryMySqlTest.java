package com.agentdoc.evaluation.service;

import com.agentdoc.evaluation.mapper.EvaluationDatasetVersionMapper;
import com.agentdoc.evaluation.mapper.EvaluationTestCaseVersionMapper;
import com.agentdoc.evaluation.mapper.EvaluatorVersionMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** 隔离库验证三类目录概要的真实 MySQL 5.7 聚合与空值语义。 */
@EnabledIfEnvironmentVariable(named = "ENGINEERING_UX_MYSQL_ADMIN_URL", matches = ".+")
class CatalogVersionSummaryMySqlTest {
    @Test void summariesKeepLatestPublishedDraftsEnabledBindingsAndSpaceBoundaries() throws Exception {
        var admin = System.getenv("ENGINEERING_UX_MYSQL_ADMIN_URL");
        assertThat(admin).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/\\?.*");
        var user = System.getenv("ENGINEERING_UX_MYSQL_USER"); var password = System.getenv("ENGINEERING_UX_MYSQL_PASSWORD");
        var database = "agentdoc_ux_catalog_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var url = admin.replace("/?", "/" + database + "?");
        try (var connection = DriverManager.getConnection(admin, user, password); var sql = connection.createStatement()) {
            sql.execute("CREATE DATABASE `" + database + "`");
            try {
                try (var target = DriverManager.getConnection(url, user, password); var seed = target.createStatement()) {
                    seed.execute("CREATE TABLE evaluation_dataset_version(id BIGINT PRIMARY KEY,dataset_id BIGINT,space_id BIGINT,version_no INT,status VARCHAR(16))");
                    seed.execute("CREATE TABLE evaluation_test_case_version(id BIGINT PRIMARY KEY,test_case_id BIGINT,space_id BIGINT,version_no INT,status VARCHAR(16))");
                    seed.execute("CREATE TABLE evaluator_version(id BIGINT PRIMARY KEY,evaluator_id BIGINT,space_id BIGINT,version_no INT,status VARCHAR(16))");
                    seed.execute("CREATE TABLE evaluation_dataset_case(dataset_version_id BIGINT,test_case_version_id BIGINT,enabled TINYINT)");
                    seed.execute("CREATE TABLE test_case_evaluator(test_case_version_id BIGINT,evaluator_version_id BIGINT)");
                    seed.execute("INSERT INTO evaluation_dataset_version VALUES(101,11,7,1,'PUBLISHED'),(102,11,7,2,'DRAFT'),(103,11,7,3,'ARCHIVED'),(104,11,7,4,'PUBLISHED'),(105,11,99,99,'PUBLISHED'),(106,12,7,1,'DRAFT')");
                    seed.execute("INSERT INTO evaluation_test_case_version VALUES(201,21,7,1,'PUBLISHED'),(202,21,7,2,'DRAFT'),(203,22,7,1,'PUBLISHED'),(204,23,99,1,'PUBLISHED')");
                    seed.execute("INSERT INTO evaluator_version VALUES(301,31,7,1,'PUBLISHED'),(302,31,7,2,'DRAFT'),(303,32,7,1,'PUBLISHED')");
                    seed.execute("INSERT INTO evaluation_dataset_case VALUES(101,201,1),(104,201,1),(104,203,0)");
                    seed.execute("INSERT INTO test_case_evaluator VALUES(201,301),(201,303),(203,301),(204,301)");
                }
                var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
                config.setEnvironment(new Environment("catalog-test", new JdbcTransactionFactory(), new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, user, password)));
                config.addMapper(EvaluationDatasetVersionMapper.class); config.addMapper(EvaluationTestCaseVersionMapper.class); config.addMapper(EvaluatorVersionMapper.class);
                try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession()) {
                    var datasets = session.getMapper(EvaluationDatasetVersionMapper.class).resourceSummaries(7L, List.of(11L, 12L));
                    var published = datasets.stream().filter(row -> row.resourceId() == 11L).findFirst().orElseThrow();
                    assertThat(published.versionCount()).isEqualTo(4); assertThat(published.draftCount()).isEqualTo(1);
                    assertThat(published.latestPublishedVersionId()).isEqualTo(104); assertThat(published.latestPublishedVersionNo()).isEqualTo(4); assertThat(published.publishedBindingCount()).isEqualTo(1);
                    var draft = datasets.stream().filter(row -> row.resourceId() == 12L).findFirst().orElseThrow();
                    assertThat(draft.latestPublishedVersionId()).isNull(); assertThat(draft.publishedBindingCount()).isNull();
                    assertThat(session.getMapper(EvaluationTestCaseVersionMapper.class).resourceSummaries(7L, List.of(21L)).getFirst().publishedBindingCount()).isEqualTo(2);
                    assertThat(session.getMapper(EvaluatorVersionMapper.class).resourceSummaries(7L, List.of(31L)).getFirst().publishedBindingCount()).isEqualTo(2);
                    assertThat(session.getMapper(EvaluatorVersionMapper.class).resourceSummaries(8L, List.of(31L))).isEmpty();
                }
            } finally { sql.execute("DROP DATABASE `" + database + "`"); }
        }
    }
}
