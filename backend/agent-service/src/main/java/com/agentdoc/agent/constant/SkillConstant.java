package com.agentdoc.agent.constant;

/**
 * Skill 模块常量定义类
 * <p>
 * 存放技能模块全局固定常量：存储路径前缀、MIME类型、工具名称、哈希算法、长度/字节硬编码阈值等。
 * 本类仅保留无法外部配置的业务硬编码常量。
 * </p>
 */
public final class SkillConstant {

    /**
     * 对象存储中技能数据存储路径前缀
     * 所有技能相关对象key统一以 skills/ 开头，方便做存储隔离、前缀遍历
     */
    public static final String STORAGE_PREFIX = "skills/";

    /**
     * zip压缩包http Content‑Type
     */
    public static final String ZIP_CONTENT_TYPE = "application/zip";

    /**
     * 技能内置工具：列出技能内部资源文件列表的工具名称
     */
    public static final String RESOURCE_LIST_TOOL = "skill_list_resources";

    /**
     * 技能内置工具：读取技能内部资源文件内容的工具名称
     */
    public static final String RESOURCE_READ_TOOL = "skill_read_resource";

    /**
     * 文件哈希校验算法：SHA‑256，用于技能包完整性校验
     */
    public static final String SHA_256 = "SHA-256";

    /**
     * 文本标准化换行符，统一转换为 LF(\n)，消除 Windows(\r\n) / Unix 换行差异
     */
    public static final String NORMALIZED_LINE_SEPARATOR = "\n";

    /**
     * 技能最大绑定变量数量，限制上下文绑定参数个数，防止参数爆炸
     */
    public static final int MAX_BINDINGS = 20;

    /**
     * 技能名称最大字符长度
     */
    public static final int MAX_NAME_LENGTH = 100;

    /**
     * 技能自定义工具名称最大字符长度
     */
    public static final int MAX_TOOL_NAME_LENGTH = 100;

    private SkillConstant() {
    }
}
