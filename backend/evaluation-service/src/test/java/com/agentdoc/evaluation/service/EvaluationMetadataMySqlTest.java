package com.agentdoc.evaluation.service;

import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.evaluation.evaluator.EvaluatorContractValidator;
import com.agentdoc.evaluation.mapper.EvaluationDatasetCaseMapper;
import com.agentdoc.evaluation.mapper.EvaluationDatasetMapper;
import com.agentdoc.evaluation.mapper.EvaluationDatasetVersionMapper;
import com.agentdoc.evaluation.mapper.EvaluationTestCaseMapper;
import com.agentdoc.evaluation.mapper.EvaluationTestCaseVersionMapper;
import com.agentdoc.evaluation.mapper.EvaluatorMapper;
import com.agentdoc.evaluation.mapper.EvaluatorVersionMapper;
import com.agentdoc.evaluation.mapper.TestCaseEvaluatorMapper;
import com.agentdoc.evaluation.pojo.dto.DatasetVersionCreateDTO;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetCaseEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetVersionEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseVersionEntity;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** 独立临时库验证真实 V44，以及创建草稿中途失败时 Spring 事务的回滚。 */
@EnabledIfEnvironmentVariable(named = "ENGINEERING_UX_MYSQL_ADMIN_URL", matches = ".+")
class EvaluationMetadataMySqlTest {
    @Test
    void migrationKeepsLegacyNamesNullAndFailedBindingCopyRollsBack() throws Exception {
        var admin = System.getenv("ENGINEERING_UX_MYSQL_ADMIN_URL");
        assertThat(admin).matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/\\?.*");
        var user = System.getenv("ENGINEERING_UX_MYSQL_USER");
        var password = System.getenv("ENGINEERING_UX_MYSQL_PASSWORD");
        var database = "agentdoc_ux_metadata_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var url = admin.replace("/?", "/" + database + "?");
        try (var connection = DriverManager.getConnection(admin, user, password); var sql = connection.createStatement()) {
            sql.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4");
            try {
                var source = new DriverManagerDataSource(url, user, password);
                var jdbc = new JdbcTemplate(source);
                jdbc.execute("CREATE TABLE evaluation_run(id BIGINT PRIMARY KEY,space_id BIGINT)");
                jdbc.execute("CREATE TABLE experiment(id BIGINT PRIMARY KEY,space_id BIGINT)");
                jdbc.update("INSERT INTO evaluation_run VALUES(1,9)");
                var migration = Path.of(System.getProperty("basedir"), "../auth-service/src/main/resources/db/migration/V44__evaluation_display_names.sql");
                for (var statement : Files.readString(migration).split(";")) {
                    if (!statement.isBlank()) jdbc.execute(statement);
                }
                assertThat(jdbc.queryForObject("SELECT name FROM evaluation_run WHERE id=1", String.class)).isNull();
                jdbc.update("INSERT INTO experiment(id,space_id,name) VALUES(2,9,?)", "中文回归实验");
                assertThat(jdbc.queryForObject("SELECT name FROM experiment WHERE id=2", String.class)).isEqualTo("中文回归实验");
                jdbc.execute("CREATE TABLE evaluation_dataset_version(id BIGINT PRIMARY KEY,content_hash VARCHAR(64))");
                jdbc.execute("CREATE TABLE evaluation_dataset_case(id BIGINT PRIMARY KEY,dataset_version_id BIGINT)");
                jdbc.update("INSERT INTO evaluation_dataset_version VALUES(11,'source-frozen')");
                verifyCopyRollback(source, jdbc);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_dataset_version", Long.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_dataset_case", Long.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT content_hash FROM evaluation_dataset_version WHERE id=11", String.class)).isEqualTo("source-frozen");
            } finally {
                sql.execute("DROP DATABASE `" + database + "`");
            }
        }
    }

    private void verifyCopyRollback(DriverManagerDataSource source, JdbcTemplate jdbc) {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "copy-rollback");
        TableInfoHelper.initTableInfo(assistant, EvaluationDatasetVersionEntity.class);
        TableInfoHelper.initTableInfo(assistant, EvaluationDatasetCaseEntity.class);
        var datasets = mock(EvaluationDatasetMapper.class);
        var versions = mock(EvaluationDatasetVersionMapper.class);
        var bindings = mock(EvaluationDatasetCaseMapper.class);
        var cases = mock(EvaluationTestCaseMapper.class);
        var caseVersions = mock(EvaluationTestCaseVersionMapper.class);
        var parent = new EvaluationDatasetEntity(); parent.setId(1L); parent.setSpaceId(9L);
        when(datasets.selectById(1L)).thenReturn(parent);
        var prior = new EvaluationDatasetVersionEntity(); prior.setId(11L); prior.setDatasetId(1L);
        prior.setSpaceId(9L); prior.setStatus("PUBLISHED"); prior.setVersionNo(1);
        when(versions.selectById(11L)).thenReturn(prior);
        when(versions.selectOne(any())).thenReturn(prior);
        var binding = new EvaluationDatasetCaseEntity(); binding.setTestCaseVersionId(31L); binding.setSortOrder(1); binding.setEnabled(true);
        when(bindings.selectList(any())).thenReturn(List.of(binding));
        var child = new EvaluationTestCaseVersionEntity(); child.setId(31L); child.setTestCaseId(32L); child.setSpaceId(9L); child.setStatus("PUBLISHED");
        when(caseVersions.selectBatchIds(any())).thenReturn(List.of(child));
        var testCase = new EvaluationTestCaseEntity(); testCase.setId(32L); testCase.setSpaceId(9L);
        when(cases.selectBatchIds(any())).thenReturn(List.of(testCase));
        doAnswer(call -> {
            EvaluationDatasetVersionEntity value = call.getArgument(0);
            return jdbc.update("INSERT INTO evaluation_dataset_version VALUES(?,NULL)", value.getId());
        }).when(versions).insert(any(EvaluationDatasetVersionEntity.class));
        doAnswer(call -> {
            List<EvaluationDatasetCaseEntity> values = call.getArgument(0);
            jdbc.update("INSERT INTO evaluation_dataset_case VALUES(?,?)", values.getFirst().getId(), values.getFirst().getDatasetVersionId());
            throw new BusinessException(500, "模拟绑定写入失败");
        }).when(bindings).insertBatch(any());
        var service = new EvaluationCatalogService(datasets, versions, bindings, cases, caseVersions,
                mock(TestCaseEvaluatorMapper.class), mock(EvaluatorMapper.class), mock(EvaluatorVersionMapper.class),
                mock(SpaceAccessService.class), mock(TaskFeign.class), mock(DocumentFeign.class), mock(EvaluatorContractValidator.class));
        var proxy = new ProxyFactory(service);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source), new AnnotationTransactionAttributeSource()));
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserIdOrException).thenReturn(5L);
            assertThatThrownBy(() -> ((EvaluationCatalogService) proxy.getProxy()).createDatasetVersion(new DatasetVersionCreateDTO(1L, 11L)))
                    .hasMessageContaining("模拟绑定写入失败");
        }
    }
}
