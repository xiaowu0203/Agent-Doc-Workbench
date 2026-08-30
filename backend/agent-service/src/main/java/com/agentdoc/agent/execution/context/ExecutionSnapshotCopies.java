package com.agentdoc.agent.execution.context;

import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import org.springframework.beans.BeanUtils;

/**
 * 执行快照拷贝工具类
 * <p>
 * 用于Agent执行上下文场景，对数据库实体做浅拷贝，生成快照副本。
 * 目的：避免执行流程中直接操作原始数据库实体对象，防止原始实体字段被意外修改、污染数据库持久化对象；
 * 拷贝后的快照对象仅供执行阶段读取使用，不用于写库。
 * <p>
 * 注意：基于 {@link BeanUtils#copyProperties(Object, Object)} 浅拷贝；嵌套对象依然共用引用，不会深度克隆。
 */
public final class ExecutionSnapshotCopies {

    private ExecutionSnapshotCopies() {
    }

    /**
     * 拷贝 AgentEntity 实体，生成快照副本
     *
     * @param source 源Agent实体（数据库原始对象，可为null）
     * @return 浅拷贝后的Agent快照对象；source为null则返回null
     */
    public static AgentEntity agent(AgentEntity source) {
        if (source == null) {
            return null;
        }
        AgentEntity target = new AgentEntity();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    /**
     * 拷贝 ModelEntity 实体，生成快照副本
     *
     * @param source 源模型实体（数据库原始对象，可为null）
     * @return 浅拷贝后的Model快照对象；source为null则返回null
     */
    public static ModelEntity model(ModelEntity source) {
        if (source == null) {
            return null;
        }
        ModelEntity target = new ModelEntity();
        BeanUtils.copyProperties(source, target);
        return target;
    }
}
