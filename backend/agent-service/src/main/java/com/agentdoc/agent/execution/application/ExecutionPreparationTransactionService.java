package com.agentdoc.agent.execution.application;

import com.agentdoc.agent.enums.AgentStatus;
import com.agentdoc.agent.execution.context.ExternalMcpConnection;
import com.agentdoc.agent.execution.context.ExecutionSnapshotCopies;
import com.agentdoc.agent.execution.skill.SkillCandidate;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.service.AgentMcpBindingService;
import com.agentdoc.agent.service.AgentService;
import com.agentdoc.agent.service.ModelService;
import com.agentdoc.agent.service.SkillSnapshotService;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 执行准备阶段事务服务
 * <p>
 * 提供短事务，行锁读取Agent及关联绑定配置，完成执行前配置捕获。
 * 使用 {@code select for update} 锁定Agent记录，校验Agent状态、加载绑定Skill与MCP连接；
 * 事务结束立刻释放数据库锁，避免长事务。
 * 返回的{@link CapturedExecution}内部对实体与集合做防御拷贝，防止JPA持久态实体后续被脏写，保证快照数据不可变。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class ExecutionPreparationTransactionService {
    private final AgentService agentService;
    private final ModelService modelService;
    private final SkillSnapshotService skillSnapshotService;
    private final AgentMcpBindingService agentMcpBindingService;

    /**
     * 事务内捕获Agent执行所需全套绑定配置快照
     * <p>
     * 事务流程：
     * 1. 行锁查询Agent（for‑update），防止并发修改Agent配置；
     * 2. 校验Agent状态必须为启用；
     * 3. 校验并加载Agent绑定的可用模型；
     * 4. 加载该Agent绑定的Skill候选集合；
     * 5. 加载已启用的外部MCP连接绑定信息；
     * 6. 返回封装快照对象；事务提交释放行锁。
     * </p>
     * <p>
     * 注意：本方法只做配置读取与校验，不生成Skill工具快照、不组装提示词；快照生成与业务计算放在事务外部执行。
     * </p>
     *
     * @param agentId Agent唯一标识
     * @return 捕获完成的执行配置快照 {@link CapturedExecution}
     * @throws BusinessException Agent不存在、Agent已禁用、关联模型不可用时抛出业务异常
     */
    @Transactional
    public CapturedExecution capture(Long agentId) {
        // 根据AgentId获取Agent信息
        AgentEntity agent = agentService.requireForUpdate(agentId);
        if (!AgentStatus.ENABLED.matches(agent.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent 已禁用");
        }
        // 根据模型Id获取模型信息
        ModelEntity model = modelService.requireEnabled(agent.getModelId());
        // 构建Agent详情（包含Agent信息、模型信息、绑定的Skill信息、绑定的外部MCP信息）
        return new CapturedExecution(
                agent,
                model,
                // 根据Agent加载绑定的【Skill】
                skillSnapshotService.loadBoundSkills(agent),
                // 根据Agent加载所需的【外部MCP】连接
                agentMcpBindingService.captureEnabled(agent));
    }

    /**
     * 事务内捕获的执行原始配置快照记录
     * <p>
     * 全部为事务内读出来的原始绑定数据；record构造块与访问器做防御拷贝：
     * <ul>
     * <li>对Agent/Model实体做深度快照拷贝，脱离JPA持久上下文，规避后续脏写；</li>
     * <li>生成不可变集合，禁止外部增删修改；</li>
     * </ul>
     * <strong>注意：此处只是绑定关系快照，还未做技能选择、未生成最终工具提示片段。</strong>
     * </p>
     *
     * @param agent               行锁读取的Agent实体（返回时为拷贝后的快照对象）
     * @param model               Agent关联模型实体（返回时为拷贝后的快照对象）
     * @param boundSkills         Agent绑定的Skill候选集合，不可变列表
     * @param externalMcpConnections Agent绑定的已启用外部MCP连接集合，不可变列表
     */
    public record CapturedExecution(AgentEntity agent, ModelEntity model, List<SkillCandidate> boundSkills,
                                    List<ExternalMcpConnection> externalMcpConnections) {
        /**
         * record构造块：执行防御拷贝逻辑
         * <p>
         * 将JPA持久态实体转为快照副本；集合转为不可变List，防止外部修改内部状态。
         * </p>
         */
        public CapturedExecution {
            agent = ExecutionSnapshotCopies.agent(agent);
            model = ExecutionSnapshotCopies.model(model);
            boundSkills = List.copyOf(boundSkills);
            externalMcpConnections = List.copyOf(externalMcpConnections);
        }

        /**
         * 获取Agent快照副本，再次拷贝避免外部拿到引用修改内部对象
         *
         * @return Agent隔离快照实体
         */
        @Override
        public AgentEntity agent() {
            return ExecutionSnapshotCopies.agent(agent);
        }

        /**
         * 获取模型快照副本，再次拷贝避免外部拿到引用修改内部对象
         *
         * @return Model隔离快照实体
         */
        @Override
        public ModelEntity model() {
            return ExecutionSnapshotCopies.model(model);
        }
    }
}
