package com.agentdoc.task.service;

import com.agentdoc.common.enums.ChangeOp;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.ChangeItemDTO;
import com.agentdoc.common.handler.CommonMetaObjectHandler;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.mapper.ChangeRequestMapper;
import com.agentdoc.task.mapper.TaskOnlineOriginalProposalMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 真实 MySQL 验证原始提案与提交原子落库、审批不覆盖和终态封口。 */
@EnabledIfEnvironmentVariable(named = "P603_EVALUATION_MYSQL_URL", matches = ".+")
class TaskOnlineOriginalProposalMySqlTest {
    private static AnnotationConfigApplicationContext first, second;
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean DriverManagerDataSource source() {
            String url=System.getenv("P603_EVALUATION_MYSQL_URL");
            if(!url.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/agentdoc_p603_evaluation_[a-f0-9]{12}\\?.*")) { throw new IllegalArgumentException("仅允许本批隔离数据库"); }
            return new DriverManagerDataSource(url,System.getenv("P603_MYSQL_USER"),System.getenv("P603_MYSQL_PASSWORD"));
        }
        @Bean PlatformTransactionManager transactionManager(DriverManagerDataSource source) { return new DataSourceTransactionManager(source); }
        @Bean SqlSessionFactory factory(DriverManagerDataSource source) throws Exception {
            var config=new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
            var factory=new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(config);
            factory.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(new CommonMetaObjectHandler()));
            var built=factory.getObject();
            for(var mapper:List.of(TaskMapper.class,ChangeRequestMapper.class,TaskOnlineOriginalProposalMapper.class)) { built.getConfiguration().addMapper(mapper); }
            return built;
        }
        @Bean SqlSessionTemplate session(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean TaskOnlineOriginalProposalService originals(SqlSessionTemplate session) {
            return new TaskOnlineOriginalProposalService(session.getMapper(TaskMapper.class),session.getMapper(TaskOnlineOriginalProposalMapper.class));
        }
        @Bean ChangeRequestService proposals(SqlSessionTemplate session, TaskOnlineOriginalProposalService originals) {
            return new ChangeRequestService(session.getMapper(ChangeRequestMapper.class),mock(DocumentFeign.class),originals);
        }
    }
    @BeforeAll static void open() { first=new AnnotationConfigApplicationContext(Config.class); second=new AnnotationConfigApplicationContext(Config.class); }
    @AfterAll static void close() { if(first!=null) { first.close(); } if(second!=null) { second.close(); } }
    private JdbcTemplate sql() { return new JdbcTemplate(first.getBean(DriverManagerDataSource.class)); }
    private TaskEntity task() {
        long id=IdWorker.getId();
        sql().update("INSERT INTO task(id,task_no,root_task_id,lineage_type,space_id,agent_id,document_id,name,status,document_type,execution_mode,document_version_snapshot,document_content_sha256,online_experiment_id,online_assignment_id,online_binding_schema_version,online_binding_hash) VALUES(?,?,?,'ORIGINAL',10,20,41,'原始提案验证',1,1,'LIVE',3,?,?,?,2,?)",
                id,"P603-"+id,id,"b".repeat(64),id+1,id+2,"a".repeat(64));
        return first.getBean(SqlSessionTemplate.class).getMapper(TaskMapper.class).selectById(id);
    }
    private List<ChangeItemDTO> changes() { return List.of(new ChangeItemDTO(ChangeOp.REPLACE,null," 原始😀\r\n提案 ")); }
    @Test void proposalCaptureIsAtomicAndApprovalCannotOverwriteFrozenPayload() {
        var task=task(); var proposal=first.getBean(ChangeRequestService.class).submitFromAgent(task,changes(),3L,"原始摘要");
        var mapper=first.getBean(SqlSessionTemplate.class).getMapper(TaskOnlineOriginalProposalMapper.class); var original=mapper.selectById(proposal.getId());
        assertThat(original.getOriginalChanges()).isEqualTo(proposal.getChanges());
        assertThat(original.getPayloadHash()).isEqualTo(StableSnapshotUtils.sha256Utf8(proposal.getChanges()));
        assertThat(TaskOnlineOriginalProposalService.identityHash(original)).isEqualTo(original.getIdentityHash());
        sql().update("UPDATE change_request SET resolved_content='审批修改后的正文',changes='[]',status=3 WHERE id=?",proposal.getId());
        assertThat(mapper.selectById(proposal.getId())).isEqualTo(original);
    }
    @Test void staleTaskCannotSubmitAfterTerminalCommitAndBusinessInsertRollsBack() throws Exception {
        var task=task(); var locked=new CountDownLatch(1); var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var terminal=pool.submit(() -> new TransactionTemplate(first.getBean(PlatformTransactionManager.class)).execute(status -> {
                sql().update("UPDATE task SET status=2 WHERE id=?",task.getId()); locked.countDown();
                try { if(!release.await(10,TimeUnit.SECONDS)) { throw new IllegalStateException("锁验证超时"); } }
                catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                return null;
            }));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
            var submit=pool.submit(() -> second.getBean(ChangeRequestService.class).submitFromAgent(task,changes(),3L));
            release.countDown(); terminal.get(10,TimeUnit.SECONDS);
            assertThatThrownBy(() -> submit.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessException.class);
        } finally { release.countDown(); }
        assertThat(sql().queryForObject("SELECT COUNT(*) FROM change_request WHERE source_task_id=?",Long.class,task.getId())).isZero();
        assertThat(sql().queryForObject("SELECT COUNT(*) FROM task_online_original_proposal WHERE task_id=?",Long.class,task.getId())).isZero();
    }
    @Test void captureCannotBeCommittedWithoutBusinessSubmissionTransaction() {
        assertThatThrownBy(() -> first.getBean(TaskOnlineOriginalProposalService.class).capture(task(),null)).isInstanceOf(IllegalTransactionStateException.class);
    }
}
