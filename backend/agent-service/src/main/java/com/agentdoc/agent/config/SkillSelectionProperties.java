package com.agentdoc.agent.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

import static com.agentdoc.agent.constant.SkillConstant.MAX_BINDINGS;
import static com.agentdoc.agent.constant.SkillConstant.MAX_ROUTER_OUTPUT_TOKENS;
import static com.agentdoc.agent.constant.SkillConstant.MAX_ROUTER_TIMEOUT_MILLIS;
import static com.agentdoc.agent.constant.SkillConstant.MIN_ROUTER_TIMEOUT_MILLIS;

/**
 * Agent 技能选择模块配置属性(Skill为ROUTER模式的Agent专用)
 * 配置前缀：agent-doc.skill.selection
 * 用于读取application.yml/application.yaml中技能路由选择相关配置，开启参数校验
 */
@Data
@Validated
@ConfigurationProperties(prefix = "agent-doc.skill.selection")
public class SkillSelectionProperties {
    /**
     * 技能选择路由配置
     */
    @Valid
    @NotNull
    private Router router = new Router();

    /**
     * 技能路由子配置
     * 控制大模型选择工具/技能时的行为参数
     */
    @Data
    public static class Router {
        /**
         * 单次请求最多可选择的技能数量
         * 约束：最小为1，不能选择0个技能
         */
        @Min(1)
        @Max(MAX_BINDINGS)
        private int maxSelectedSkills = 5;

        /**
         * 技能选择大模型调用超时时间
         * 默认8秒，超过该时间视为选择失败
         */
        @NotNull
        private Duration timeout = Duration.ofSeconds(8);

        /**
         * 技能选择阶段模型最大输出token
         * 限制路由输出JSON的长度，防止输出过大消耗token
         */
        @Min(1)
        @Max(MAX_ROUTER_OUTPUT_TOKENS)
        private int maxOutputTokens = 256;

        /**
         * 校验 Router 超时时间处于安全范围。
         *
         * @return 超时配置是否合法
         */
        @AssertTrue(message = "Skill Router timeout 必须介于 100ms 与 60s 之间")
        public boolean isTimeoutWithinRange() {
            if (timeout == null) {
                return true;
            }
            long millis = timeout.toMillis();
            return millis >= MIN_ROUTER_TIMEOUT_MILLIS && millis <= MAX_ROUTER_TIMEOUT_MILLIS;
        }
    }
}
