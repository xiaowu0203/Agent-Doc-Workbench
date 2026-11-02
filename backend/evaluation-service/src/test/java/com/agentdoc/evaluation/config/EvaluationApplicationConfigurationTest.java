package com.agentdoc.evaluation.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证评估服务可使用与其他业务服务一致的部署变量。 */
class EvaluationApplicationConfigurationTest {

    @Test
    void defaultsToLocalJwksAndRedis() throws Exception {
        var environment = environment();
        assertThat(environment.getProperty("agent-doc.security.jwks-url"))
                .isEqualTo("http://localhost:8081/oauth2/jwks");
        assertThat(environment.getProperty("spring.data.redis.host")).isEqualTo("localhost");
        assertThat(environment.getProperty("spring.data.redis.port")).isEqualTo("6379");
    }

    @Test
    void acceptsSharedDeploymentVariables() throws Exception {
        var environment = environment();
        environment.setProperty("AUTH_JWKS_URL", "http://auth:8081/oauth2/jwks");
        environment.setProperty("REDIS_HOST", "redis");
        environment.setProperty("REDIS_PORT", "6380");
        environment.setProperty("REDIS_PASSWORD", "test-password");
        assertThat(environment.getProperty("agent-doc.security.jwks-url"))
                .isEqualTo("http://auth:8081/oauth2/jwks");
        assertThat(environment.getProperty("spring.data.redis.host")).isEqualTo("redis");
        assertThat(environment.getProperty("spring.data.redis.port")).isEqualTo("6380");
        assertThat(environment.getProperty("spring.data.redis.password")).isEqualTo("test-password");
    }

    @Test
    void usesSharedDatabaseVariablesUnlessEvaluationOverridesThem() throws Exception {
        var environment = environment();
        environment.setProperty("MYSQL_HOST", "mysql");
        environment.setProperty("MYSQL_PORT", "3307");
        environment.setProperty("MYSQL_DATABASE", "isolated");
        environment.setProperty("MYSQL_USERNAME", "fixture");
        environment.setProperty("MYSQL_PASSWORD", "fixture-password");
        assertThat(environment.getProperty("spring.datasource.url")).startsWith("jdbc:mysql://mysql:3307/isolated?");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("fixture");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("fixture-password");

        environment.setProperty("DB_URL", "jdbc:mysql://other:3306/override");
        environment.setProperty("DB_USERNAME", "override");
        environment.setProperty("DB_PASSWORD", "override-password");
        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:mysql://other:3306/override");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("override");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("override-password");
    }

    private MockEnvironment environment() throws Exception {
        var environment = new MockEnvironment();
        var source = new YamlPropertySourceLoader().load("evaluation", new ClassPathResource("application.yml"));
        source.forEach(environment.getPropertySources()::addLast);
        return environment;
    }
}
