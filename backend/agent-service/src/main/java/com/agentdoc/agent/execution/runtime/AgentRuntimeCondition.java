package com.agentdoc.agent.execution.runtime;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Locale;

/**
 * AgentRuntime 自定义条件注解解析器
 * <p>
 * Spring Boot 条件装配实现，配合 {@link ConditionalOnAgentRuntime} 使用。
 * 根据配置项 {@code agent-doc.agent.runtime.type} 的值，控制不同实现版本的AgentRuntime Bean是否注册。
 * 支持大小写、下划线/横杠兼容，例如 CUSTOM、custom、CUSTOM 都视为同一个配置。
 * </p>
 */
public final class AgentRuntimeCondition extends SpringBootCondition {
    /** 配置key：agent‑doc.agent.runtime.type */
    private static final String PROPERTY = "agent-doc.agent.runtime.type";

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // 获取注解上声明期望匹配的运行时类型
        Object value = metadata.getAnnotationAttributes(ConditionalOnAgentRuntime.class.getName())
                .get("value");

        // 读取环境配置，取不到默认 CUSTOM
        AgentRuntimeType expected = (AgentRuntimeType) value;
        String configured = context.getEnvironment().getProperty(PROPERTY, AgentRuntimeType.CUSTOM.name());

        // 标准化处理配置值：去空格、下划线转横杠、转小写，做宽松匹配
        String normalized = normalize(configured);
        String expectedValue = normalize(expected.name());
        ConditionMessage.Builder message = ConditionMessage.forCondition("Agent runtime");
        if (expectedValue.equals(normalized)) {
            // 配置与注解期望匹配，Bean生效
            return ConditionOutcome.match(message.found("value").items(configured));
        }
        // 不匹配，不注册该Bean
        return ConditionOutcome.noMatch(message.didNotFind("matching value").items(configured));
    }

    /**
     * 标准化配置字符串，实现宽松匹配
     * @param value 原始配置字符串
     * @return 统一小写、下划线替换为横杠、去除首尾空白
     */
    private String normalize(String value) {
        return value.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    }
}
