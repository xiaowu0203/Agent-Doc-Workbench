package com.agentdoc.agent.execution.runtime;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AgentRuntime条件装配注解
 * <p>
 * 配合 {@link AgentRuntimeCondition} 使用，标注在类或者方法上。
 * 当配置项 {@code agent‑doc.agent.runtime.type} 与注解指定的{@link AgentRuntimeType}匹配时，
 * 对应的Bean才会被Spring容器注册。
 * </p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(AgentRuntimeCondition.class)
public @interface ConditionalOnAgentRuntime {
    /**
     * 需要匹配的Agent运行时类型
     * @return 期望启用的Runtime类型
     */
    AgentRuntimeType value();
}
