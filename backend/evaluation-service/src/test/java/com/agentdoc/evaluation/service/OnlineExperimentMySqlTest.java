package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.handler.CommonMetaObjectHandler;
import com.agentdoc.common.feign.AgentOnlineConfigFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.vo.AgentOnlineConfigPairVO;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.convertor.OnlineAssignmentConvertor;
import com.agentdoc.evaluation.convertor.OnlineExperimentConvertor;
import com.agentdoc.evaluation.evaluator.EvaluatorContractValidator;
import com.agentdoc.evaluation.mapper.*;
import com.agentdoc.evaluation.pojo.entity.EvaluatorVersionEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentCreateIntentEntity;
import com.agentdoc.evaluation.pojo.param.OnlineExperimentSearchParam;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实 MyBatis/事务/分页与隔离 MySQL；Feign 边界替身不运行模型。 */
@EnabledIfEnvironmentVariable(named = "P6_RESTART_MYSQL_API_URL", matches = ".+")
class OnlineExperimentMySqlTest {
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
            var interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setConfiguration(configuration);
            factory.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(new CommonMetaObjectHandler()));
            factory.setPlugins(interceptor);
            SqlSessionFactory built = factory.getObject();
            built.getConfiguration().addMapper(OnlineExperimentMapper.class);
            built.getConfiguration().addMapper(OnlineAssignmentMapper.class);
            built.getConfiguration().addMapper(OnlineExperimentCreateIntentMapper.class);
            built.getConfiguration().addMapper(EvaluatorVersionMapper.class);
            return built;
        }
        @Bean SqlSessionTemplate session(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean OnlineExperimentMapper experiments(SqlSessionTemplate session) { return session.getMapper(OnlineExperimentMapper.class); }
        @Bean OnlineAssignmentMapper assignments(SqlSessionTemplate session) { return session.getMapper(OnlineAssignmentMapper.class); }
        @Bean OnlineExperimentCreateIntentMapper intents(SqlSessionTemplate session) { return session.getMapper(OnlineExperimentCreateIntentMapper.class); }
        @Bean EvaluatorVersionMapper versions(SqlSessionTemplate session) { return session.getMapper(EvaluatorVersionMapper.class); }
        @Bean OnlineExperimentPersistenceService persistence(OnlineExperimentMapper experiments, OnlineExperimentCreateIntentMapper intents) {
            return new OnlineExperimentPersistenceService(experiments, intents);
        }
    }

    @Test
    void creationRetryPaginationAndPreflightUseRealMapperAndTransactionBoundary() {
        var jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject("501")
                .claim(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_USER).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            var experiments = context.getBean(OnlineExperimentMapper.class);
            var intents = context.getBean(OnlineExperimentCreateIntentMapper.class);
            var versions = context.getBean(EvaluatorVersionMapper.class);
            var version = new EvaluatorVersionEntity();
            version.setId(401L); version.setSpaceId(101L); version.setEvaluatorId(400L); version.setVersionNo(1);
            version.setStatus("PUBLISHED"); version.setEvaluatorKey("online-original-text-assertion");
            version.setConfigSchemaVersion(1); version.setResultSchemaVersion(1); version.setConfigJson("{}");
            version.setImplementationVersion("online-contract-v2"); version.setCreatedBy(501L);
            version.setContentHash(StableSnapshotUtils.snapshotHash(1, Map.of("evaluatorKey", version.getEvaluatorKey(),
                    "configSchemaVersion", 1, "config", Map.of(), "resultSchemaVersion", 1, "implementationVersion", "online-contract-v2")));
            versions.insert(version);
            var access = mock(SpaceAccessService.class);
            var agents = mock(AgentOnlineConfigFeign.class);
            var documents = mock(DocumentFeign.class);
            when(documents.getDocumentRefs(anyList())).thenReturn(Result.ok(List.of(
                    new DocumentRefVO(301L, 101L, "doc1"), new DocumentRefVO(302L, 101L, "doc2"))));
            var pair = new AgentOnlineConfigPairVO(new AgentOnlineConfigPairVO.TemplateIdentity("601", 2, "a".repeat(64), "b".repeat(64)),
                    new AgentOnlineConfigPairVO.TemplateIdentity("602", 2, "c".repeat(64), "b".repeat(64)), "d".repeat(64), 600, 1048576);
            when(agents.prepareOnlineConfigs(any())).thenReturn(Result.ok(pair));
            when(agents.onlineDependency(anyLong(), eq(101L))).thenReturn(Result.ok(pair.dependencyHash()));
            var service = new OnlineExperimentService(experiments, context.getBean(OnlineAssignmentMapper.class),
                    new OnlineAssignmentConvertor(), new OnlineExperimentConvertor(), intents, context.getBean(OnlineExperimentPersistenceService.class),
                    versions, new EvaluatorContractValidator(), access, agents, documents);
            var created = service.create(OnlineExperimentRequestValidatorTest.request());
            assertThat(service.create(OnlineExperimentRequestValidatorTest.request()).summary().id()).isEqualTo(created.summary().id());
            assertThat(created.summary().createdAt()).isNotNull();
            assertThat(service.preflight(created.summary().id()).startable()).isFalse();
            var search = new OnlineExperimentSearchParam();
            search.setSpaceId("101");
            assertThat(service.search(search).records()).hasSize(1);
            assertThat(service.search(search).total()).isEqualTo(1);
            assertThat(service.search(search).records().getFirst().participatingDocumentCount()).isZero();
            var intent = intents.selectById(Long.valueOf(created.summary().id()));
            assertThat(intent.getStatus()).isEqualTo("CREATED");
            verify(agents, times(1)).prepareOnlineConfigs(any());
            // 相同 Space/操作者/key 的创建意图由数据库唯一约束保护。
            var conflicting = new OnlineExperimentCreateIntentEntity();
            conflicting.setId(99901L); conflicting.setSpaceId(101L); conflicting.setCreatedBy(501L);
            conflicting.setRequestKey("review-1"); conflicting.setRequestHash("e".repeat(64)); conflicting.setStatus("PREPARING");
            assertThatThrownBy(() -> context.getBean(OnlineExperimentPersistenceService.class).reserve(conflicting))
                    .isInstanceOf(DuplicateKeyException.class);
            assertThat(intents.selectById(99901L)).isNull();
        } finally { SecurityContextHolder.clearContext(); }
    }
}
