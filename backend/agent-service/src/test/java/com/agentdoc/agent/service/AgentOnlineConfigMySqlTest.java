package com.agentdoc.agent.service;

import com.agentdoc.agent.mapper.AgentOnlineConfigMapper;
import com.agentdoc.agent.pojo.entity.AgentOnlineConfigEntity;
import com.agentdoc.common.handler.CommonMetaObjectHandler;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/** 验证模板双写的真实事务回滚；仅连接本批自建隔离库。 */
@EnabledIfEnvironmentVariable(named = "P6_RESTART_MYSQL_API_URL", matches = ".+")
class AgentOnlineConfigMySqlTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfig {
        @Bean DriverManagerDataSource source() {
            String url = System.getenv("P6_RESTART_MYSQL_API_URL");
            if (!url.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/agentdoc_p601_new_api_[a-f0-9]{12}\\?.*")) {
                throw new IllegalArgumentException("Only owned isolated database allowed");
            }
            return new DriverManagerDataSource(url, System.getenv("P6_RESTART_MYSQL_USER"), System.getenv("P6_RESTART_MYSQL_PASSWORD"));
        }
        @Bean PlatformTransactionManager transactionManager(DriverManagerDataSource source) { return new DataSourceTransactionManager(source); }
        @Bean SqlSessionFactory factory(DriverManagerDataSource source) throws Exception {
            var configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setConfiguration(configuration);
            factory.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(new CommonMetaObjectHandler()));
            var built = factory.getObject();
            built.getConfiguration().addMapper(AgentOnlineConfigMapper.class);
            return built;
        }
        @Bean SqlSessionTemplate session(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean AgentOnlineConfigMapper mapper(SqlSessionTemplate session) { return session.getMapper(AgentOnlineConfigMapper.class); }
        @Bean AgentOnlineConfigPersistenceService persistence(AgentOnlineConfigMapper mapper) { return new AgentOnlineConfigPersistenceService(mapper); }
    }

    @Test
    void candidateFailureRollsBackBaselineAndSuccessfulPairIsReadable() {
        try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            var persistence = context.getBean(AgentOnlineConfigPersistenceService.class);
            var mapper = context.getBean(AgentOnlineConfigMapper.class);
            assertThatThrownBy(() -> persistence.savePair(List.of(template(98001L, "BASELINE"), template(98001L, "CANDIDATE"))))
                    .isInstanceOf(DuplicateKeyException.class);
            assertThat(mapper.selectById(98001L)).isNull();
            persistence.savePair(List.of(template(98001L, "BASELINE"), template(98002L, "CANDIDATE")));
            assertThat(mapper.selectById(98001L).getRole()).isEqualTo("BASELINE");
            assertThat(mapper.selectById(98002L).getRole()).isEqualTo("CANDIDATE");
            assertThat(mapper.selectById(98002L).getCreatedAt()).isNotNull();
        }
    }

    private AgentOnlineConfigEntity template(Long id, String role) {
        var value = new AgentOnlineConfigEntity();
        value.setId(id); value.setExperimentId(98000L); value.setSpaceId(101L); value.setAgentId(201L);
        value.setRole(role); value.setSchemaVersion(2); value.setTemplateJson("{}");
        value.setRequestHash("a".repeat(64)); value.setTemplateHash("b".repeat(64));
        value.setNonPromptHash("c".repeat(64)); value.setDependencyHash("d".repeat(64));
        value.setExecutionTimeoutSeconds(600); value.setMaxSystemPromptBytes(1048576L); value.setCreatedBy(501L);
        return value;
    }
}
