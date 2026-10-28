package com.agentdoc.task.service;

import com.agentdoc.task.mapper.TokenUsageDetailMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** 独立库验证权威账本筛选、缺失分母及跨空间关系，不修改应用数据库。 */
@EnabledIfEnvironmentVariable(named = "ENGINEERING_UX_MYSQL_ADMIN_URL", matches = ".+")
class TokenUsageFilterMySqlTest {
    @Test void ledgerSummaryAndTrendShareModeParticipationAndSpaceBoundary() throws Exception {
        var admin = System.getenv("ENGINEERING_UX_MYSQL_ADMIN_URL");
        assertThat(admin).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/\\?.*");
        var user = System.getenv("ENGINEERING_UX_MYSQL_USER"); var password = System.getenv("ENGINEERING_UX_MYSQL_PASSWORD");
        var database = "agentdoc_ux_ledger_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var url = admin.replace("/?", "/" + database + "?");
        try (var connection = DriverManager.getConnection(admin, user, password); var sql = connection.createStatement()) {
            sql.execute("CREATE DATABASE `" + database + "`");
            try {
                try (var target = DriverManager.getConnection(url, user, password); var seed = target.createStatement()) {
                    seed.execute("CREATE TABLE task(id BIGINT PRIMARY KEY,space_id BIGINT,execution_mode VARCHAR(16),online_experiment_id BIGINT,status INT)");
                    seed.execute("CREATE TABLE token_usage_detail(id BIGINT PRIMARY KEY,space_id BIGINT,task_id BIGINT,agent_id BIGINT,model_id BIGINT,call_time DATETIME,input_tokens BIGINT,output_tokens BIGINT,estimated_cost DECIMAL(14,6),input_tokens_estimated TINYINT,output_tokens_estimated TINYINT)");
                    seed.execute("INSERT INTO task VALUES(1,7,'LIVE',NULL,2),(2,7,'ISOLATED',NULL,2),(3,7,'LIVE',91,2),(4,8,'LIVE',NULL,2),(5,7,'ISOLATED',NULL,2)");
                    seed.execute("INSERT INTO token_usage_detail VALUES(11,7,1,1,99,'2026-10-01',100,100,0.2,0,0),(12,7,2,1,99,'2026-10-01',30,40,0.1,0,0),(13,7,3,1,99,'2026-10-01',50,50,0.1,0,0),(14,7,4,1,99,'2026-10-01',900,900,0.9,0,0),(15,7,5,1,99,'2026-10-02',NULL,10,NULL,0,0)");
                }
                var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
                config.setEnvironment(new Environment("ledger-test", new JdbcTransactionFactory(), new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, user, password)));
                var resource = "mapper/TokenUsageDetailMapper.xml";
                try (var input = getClass().getClassLoader().getResourceAsStream(resource)) { new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse(); }
                try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession()) {
                    var mapper = session.getMapper(TokenUsageDetailMapper.class); var from = LocalDateTime.of(2026, 10, 1, 0, 0); var to = from.plusDays(2);
                    var isolated = mapper.summarize(7L, from, to, 1L, 99L, 2, "ISOLATED", false);
                    assertThat(isolated.taskCount()).isEqualTo(2); assertThat(isolated.inputTokens()).isEqualTo(30); assertThat(isolated.outputTokens()).isEqualTo(50);
                    assertThat(isolated.missingInputCount()).isEqualTo(1); assertThat(isolated.missingCostCount()).isEqualTo(1);
                    var daily = mapper.summarizeDaily(7L, from, to, 1L, 99L, 2, "ISOLATED", false);
                    assertThat(daily).hasSize(2); assertThat(daily.getLast().missingInputCount()).isEqualTo(1);
                    var online = mapper.summarize(7L, from, to, null, null, null, "LIVE", true);
                    assertThat(online.taskCount()).isEqualTo(1); assertThat(online.inputTokens()).isEqualTo(50);
                    var all = mapper.summarize(7L, from, to, null, null, null, null, null);
                    assertThat(all.taskCount()).isEqualTo(4); assertThat(all.inputTokens()).isEqualTo(180);
                }
            } finally { sql.execute("DROP DATABASE `" + database + "`"); }
        }
    }
}
