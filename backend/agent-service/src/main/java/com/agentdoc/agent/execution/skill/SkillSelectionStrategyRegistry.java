package com.agentdoc.agent.execution.skill;

import com.agentdoc.agent.enums.SkillSelectionMode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Skill选择策略注册表
 * <p>
 * 基于策略模式，统一管理所有{@link SkillSelectionStrategy}实现Bean。
 * Spring容器启动时自动注入所有实现了SkillSelectionStrategy接口的Bean，完成注册校验：
 * 1. 不允许同一个{@link SkillSelectionMode}重复注册多个策略实现；
 * 2. 强制要求枚举内全部模式都要有对应的策略实现，缺失直接启动报错；
 * 3. 内部使用不可变Map存储策略实例，运行时只读，线程安全。
 * <p>
 * 使用方通过模式字符串/mode枚举，调用{@link #require(String)}获取对应策略Bean执行技能选择逻辑。
 * </p>
 */
@Component
public class SkillSelectionStrategyRegistry {

    /**
     * 策略映射表：key为技能选择模式枚举，value为对应策略实现实例，构造完成后为不可变集合
     */
    private final Map<SkillSelectionMode, SkillSelectionStrategy> strategies;

    /**
     * 构造器：Spring自动注入容器中全部 {@link SkillSelectionStrategy} 的实现Bean列表
     * <p>注册校验逻辑：
     * 1. 使用EnumMap做索引，遍历所有策略Bean，按mode()枚举值放入map；
     * 2. 如果同一个mode已经存在实例，抛出启动异常，禁止重复注册；
     * 3. 校验注册完成后的策略数量，必须和SkillSelectionMode枚举全部常量数量一致，防止策略漏实现；
     * 4. 使用Map.copyOf转为不可变Map，防止运行时外部修改内部映射关系，保证线程安全。
     * </p>
     * @param strategies Spring容器中所有SkillSelectionStrategy实现类Bean集合
     */
    public SkillSelectionStrategyRegistry(List<SkillSelectionStrategy> strategies) {
        Map<SkillSelectionMode, SkillSelectionStrategy> indexed = new EnumMap<>(SkillSelectionMode.class);
        strategies.forEach(strategy -> {
            // put返回旧值，旧值不为null代表该mode已经注册过策略，重复实现冲突
            if (indexed.put(strategy.mode(), strategy) != null) {
                throw new IllegalStateException("Skill 选择策略重复注册: " + strategy.mode());
            }
        });
        // 校验：枚举定义的全部模式必须都有对应实现，少一个则启动失败，避免运行时找不到策略
        if (indexed.size() != SkillSelectionMode.values().length) {
            throw new IllegalStateException("Skill 选择策略未完整注册");
        }
        this.strategies = Map.copyOf(indexed);
    }

    /**
     * 根据模式名称获取对应的策略实例，获取不到直接抛出异常，不返回null
     * <p>执行流程：
     * 1. 将字符串mode解析为{@link SkillSelectionMode}枚举；字符串非法/空值抛出异常；
     * 2. 从内部不可变Map查找策略实例；
     * 3. 找不到实例抛出异常，上层需要捕获处理。
     * </p>
     * @param mode 技能选择模式字符串，对应SkillSelectionMode枚举名称，例如 ALL_BOUND、ROUTER
     * @return 对应模式的策略实现Bean
     * @throws IllegalStateException 模式字符串非法、模式未注册时抛出
     */
    public SkillSelectionStrategy require(String mode) {
        SkillSelectionMode selectionMode;
        try {
            // 将传入字符串转枚举，字符串不存在、null会抛出异常
            selectionMode = SkillSelectionMode.valueOf(mode);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalStateException("不支持的 Agent Skill 选择模式: " + mode, exception);
        }
        SkillSelectionStrategy strategy = strategies.get(selectionMode);
        if (strategy == null) {
            throw new IllegalStateException("Skill 选择策略未注册: " + mode);
        }
        return strategy;
    }
}
