package com.agentdoc.agent.skill.archive;

import com.agentdoc.common.exception.BusinessException;

/**
 * Skill ZIP 包不符合规范时抛出的内部校验异常。
 * <p>
 * 用于技能包解析阶段校验失败：包格式损坏、超出大小限制、非法文件路径、清单格式错误等场景。
 * 属于非业务对外异常，上层需要捕获转换为 {@link BusinessException} 返回给调用方。
 * </p>
 */
public class SkillPackageValidationException extends RuntimeException {

    /**
     * 构造技能包校验异常
     * @param message 校验失败描述信息
     */
    public SkillPackageValidationException(String message) {
        super(message);
    }

    /**
     * 构造带原始异常堆栈的技能包校验异常
     * @param message 校验失败描述信息
     * @param cause 底层原始异常（解压IO异常、解析异常等）
     */
    public SkillPackageValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
