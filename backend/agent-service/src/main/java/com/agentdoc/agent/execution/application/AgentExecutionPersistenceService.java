package com.agentdoc.agent.execution.application;

import com.agentdoc.agent.convertor.AgentExecutionConvertor;
import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.execution.runtime.AgentRuntimeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agent执行实例持久化服务
 * <p>
 * 负责Agent任务执行记录的数据库CRUD，封装任务状态流转的数据库更新操作，
 * 所有写操作均开启事务，任意异常触发事务回滚。
 * 配合 {@link AgentExecutionConvertor} 完成实体状态字段填充，通过Mapper完成入库与更新。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AgentExecutionPersistenceService {

    private final AgentExecutionMapper executionMapper;

    /**
     * 插入已提交状态的Agent执行记录
     * <p>任务刚被提交、尚未开始调度时调用，写入初始执行实体。</p>
     *
     * @param execution Agent执行实体，状态为已提交(submitted)
     */
    @Transactional(rollbackFor = Exception.class)
    public void insertSubmitted(AgentExecutionEntity execution) {
        executionMapper.insert(execution);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateToolDefinitionSnapshot(Long executionId, String snapshotJson) {
        AgentExecutionEntity execution = new AgentExecutionEntity();
        execution.setId(executionId);
        execution.setToolDefinitionSnapshotJson(snapshotJson);
        if (executionMapper.updateById(execution) != 1) {
            throw new IllegalStateException("持久化工具定义时 Agent 执行记录不存在: " + executionId);
        }
    }

    /**
     * 将Agent执行记录更新为【运行中】状态
     * <p>任务正式开始执行、进入Agent调度循环时调用。</p>
     *
     * @param execution Agent执行实体，会被填充运行中相关时间与状态字段
     */
    @Transactional(rollbackFor = Exception.class)
    public void markWorking(AgentExecutionEntity execution) {
        AgentExecutionConvertor.markWorking(execution);
        executionMapper.updateById(execution);
    }

    /**
     * 将Agent执行记录更新为【正常完成】状态
     * <p>Agent任务执行完毕、得到运行结果时调用，回填结果、结束时间等信息。</p>
     *
     * @param execution Agent执行实体
     * @param result    Agent运行输出结果对象，用于填充实体结果字段
     */
    @Transactional(rollbackFor = Exception.class)
    public void markCompleted(AgentExecutionEntity execution, AgentRuntimeResult result) {
        AgentExecutionConvertor.complete(execution, result);
        executionMapper.updateById(execution);
    }

    /**
     * 将Agent执行记录更新为【已取消】状态
     * <p>收到外部取消信号、任务被主动终止时调用。</p>
     *
     * @param execution Agent执行实体
     */
    @Transactional(rollbackFor = Exception.class)
    public void markCanceled(AgentExecutionEntity execution) {
        AgentExecutionConvertor.cancel(execution);
        executionMapper.updateById(execution);
    }

    /**
     * 将Agent执行记录更新为【执行失败】状态
     * <p>任务运行抛出异常、不可继续执行时调用，记录错误信息与失败时间。</p>
     *
     * @param execution    Agent执行实体
     * @param errorMessage 失败简要描述/异常信息文本
     */
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(AgentExecutionEntity execution, String errorMessage) {
        AgentExecutionConvertor.fail(execution, errorMessage);
        executionMapper.updateById(execution);
    }
}
