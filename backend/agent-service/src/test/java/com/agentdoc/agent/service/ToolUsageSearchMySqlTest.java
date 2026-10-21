package com.agentdoc.agent.service;

import com.agentdoc.agent.mapper.AgentExecutionToolCallMapper;
import com.agentdoc.agent.pojo.entity.AgentExecutionToolCallEntity;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.common.feign.dto.AgentToolUsageQueryDTO;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

/** 真实 MySQL 验证工具汇总和独立分页共用条件，且历史未知模式不混入 LIVE。 */
@EnabledIfEnvironmentVariable(named = "ENGINEERING_UX_MYSQL_ADMIN_URL", matches = ".+")
class ToolUsageSearchMySqlTest {
    @Test void independentPagesMatchSummaryAndRespectFalseParticipationModeAndSpace() throws Exception {
        var admin = System.getenv("ENGINEERING_UX_MYSQL_ADMIN_URL");
        assertThat(admin).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/\\?.*");
        var user = System.getenv("ENGINEERING_UX_MYSQL_USER"); var password = System.getenv("ENGINEERING_UX_MYSQL_PASSWORD");
        var database = "agentdoc_ux_tools_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var url = admin.replace("/?", "/" + database + "?");
        try (var connection = DriverManager.getConnection(admin, user, password); var sql = connection.createStatement()) {
            sql.execute("CREATE DATABASE `" + database + "`");
            try {
                try (var target = DriverManager.getConnection(url, user, password); var seed = target.createStatement()) {
                    seed.execute("CREATE TABLE agent_execution(id BIGINT PRIMARY KEY,space_id BIGINT,agent_id BIGINT,execution_mode VARCHAR(16),online_experiment_id BIGINT,status VARCHAR(32))");
                    seed.execute("CREATE TABLE agent_execution_model_call(execution_id BIGINT,model_id BIGINT)");
                    seed.execute("CREATE TABLE agent_execution_tool_call(id BIGINT PRIMARY KEY,execution_id BIGINT,sequence_no INT,tool_name VARCHAR(100),tool_source VARCHAR(32),mcp_server_id BIGINT,tool_source_key VARCHAR(100),started_at DATETIME(3),status VARCHAR(32))");
                    seed.execute("INSERT INTO agent_execution VALUES(1,7,1,'ISOLATED',NULL,'COMPLETED'),(2,7,1,'LIVE',NULL,'COMPLETED'),(3,7,1,'ISOLATED',91,'COMPLETED'),(4,8,1,'ISOLATED',NULL,'COMPLETED'),(5,7,1,NULL,NULL,'COMPLETED')");
                    seed.execute("INSERT INTO agent_execution_model_call VALUES(1,99),(2,99),(3,99),(4,99),(5,99)");
                    int callId = 100;
                    for (int execution = 1; execution <= 5; execution++) {
                        int count = switch (execution) { case 1 -> 14; case 2 -> 4; case 3 -> 2; case 4 -> 3; default -> 1; };
                        for (int number = 0; number < count; number++) seed.execute("INSERT INTO agent_execution_tool_call VALUES(" + callId++ + "," + execution + "," + number + ",'document_read','WORKBENCH_MCP',NULL,'workbench','2026-10-01 12:00:00','SUCCEEDED')");
                    }
                }
                var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
                config.setEnvironment(new Environment("tools-test", new JdbcTransactionFactory(), new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, user, password)));
                var paging = new MybatisPlusInterceptor(); paging.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL)); config.addInterceptor(paging);
                var resource = "mapper/AgentExecutionToolCallMapper.xml";
                try (var input = getClass().getClassLoader().getResourceAsStream(resource)) { new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse(); }
                try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession()) {
                    var mapper = session.getMapper(AgentExecutionToolCallMapper.class);
                    var from = LocalDateTime.of(2026, 10, 1, 0, 0);
                    var query = new AgentToolUsageQueryDTO(7L, from, from.plusDays(1), 1L, 99L, "COMPLETED", TaskExecutionMode.ISOLATED, false);
                    var page = mapper.search(new Page<AgentExecutionToolCallEntity>(2, 10), query);
                    assertThat(page.getTotal()).isEqualTo(14); assertThat(page.getRecords()).hasSize(4);
                    assertThat(page.getRecords()).extracting(AgentExecutionToolCallEntity::getId).containsExactly(103L, 102L, 101L, 100L);
                    assertThat(mapper.aggregateBySource(query).getFirst().calls()).isEqualTo(page.getTotal());
                    var live = new AgentToolUsageQueryDTO(7L, from, from.plusDays(1), null, null, null, TaskExecutionMode.LIVE, false);
                    assertThat(mapper.search(new Page<>(1, 10), live).getTotal()).isEqualTo(4);
                    var all = new AgentToolUsageQueryDTO(7L, from, from.plusDays(1), null, null, null, null, null);
                    assertThat(mapper.search(new Page<>(1, 10), all).getTotal()).isEqualTo(21);
                }
            } finally { sql.execute("DROP DATABASE `" + database + "`"); }
        }
    }
}
