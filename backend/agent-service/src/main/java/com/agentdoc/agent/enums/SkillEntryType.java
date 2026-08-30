package com.agentdoc.agent.enums;

/**
 * Skill包内资源条目类型
 * <p>对应Skill压缩包manifest中每个条目的type字段，区分不同用途的文件资源</p>
 */
public enum SkillEntryType {
    /**
     * 指令文本：系统提示词、指令说明类文本文件
     */
    INSTRUCTION,
    /**
     * 脚本文件：可执行脚本、工具逻辑脚本
     */
    SCRIPT,
    /**
     * 参考资料：知识库、参考文档、上下文参考素材
     */
    REFERENCE,
    /**
     * 静态资源资产：图片、配置、其他附属资源文件
     */
    ASSET,
    /**
     * 示例文件：样例输入输出、示例模板
     */
    EXAMPLE
}
