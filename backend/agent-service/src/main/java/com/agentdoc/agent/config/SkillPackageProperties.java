package com.agentdoc.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Skill 技能包安全校验配置属性
 * <p>
 * 配置项前缀：agent‑doc.skill.package
 * 用于限制导入的技能压缩包各项安全阈值：包大小、解压后大小、文件数量、单文件大小、路径长度、YAML解析限制等，
 * 防止超大文件、恶意嵌套、解压炸弹、超长路径等风险，全部配置项提供合理默认值，可在配置文件覆盖调整。
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "agent-doc.skill.package")
public class SkillPackageProperties {

    /**
     * 上传的技能归档压缩包最大总大小，默认 2 MB
     */
    private DataSize maxArchiveSize = DataSize.ofMegabytes(2);

    /**
     * 压缩包解压之后总占用大小上限，默认 6MB
     * <p>用于防护解压炸弹，防止极小压缩包解压后产生海量数据</p>
     */
    private DataSize maxUncompressedSize = DataSize.ofMegabytes(6);

    /**
     * 压缩包内允许最大文件总数量，默认80个
     * <p>防止压缩包包含成千上万个小文件造成内存/IO压力</p>
     */
    private int maxFileCount = 80;

    /**
     * 压缩包内单个文件最大尺寸，默认 2MB
     */
    private DataSize maxSingleFileSize = DataSize.ofMegabytes(2);

    /**
     * 技能主定义文件 skill.md 文件大小上限，默认 128KB
     */
    private DataSize maxSkillMdSize = DataSize.ofKilobytes(128);

    /**
     * 单个可读资源文件最大大小，默认 256KB
     */
    private DataSize maxReadableResourceSize = DataSize.ofKilobytes(256);

    /**
     * 全部可读资源文件合计总大小上限，默认 1MB
     */
    private DataSize maxReadableResourcesSize = DataSize.ofMegabytes(1);

    /**
     * 最大压缩比，默认15倍
     * <p>校验压缩包压缩率，压缩后大小 * maxCompressionRatio < 解压大小 判定为解压炸弹风险</p>
     */
    private int maxCompressionRatio = 15;

    /**
     * 压缩包内单条文件路径字符串最大长度，默认240字符
     * <p>防御超长路径、路径穿越相关风险</p>
     */
    private int maxPathLength = 240;

    /**
     * 技能指令配置内容最大大小，默认64KB
     */
    private DataSize maxSkillInstructionsSize = DataSize.ofKilobytes(64);

    /**
     * 技能内置系统提示词 system‑prompt 内容大小上限，默认128KB
     */
    private DataSize maxSystemPromptSize = DataSize.ofKilobytes(128);

    /**
     * YAML解析字符码点上限，独立于UTF‑8字节大小限制，默认32768
     * <p>防护YAML超大输入，避免解析时内存暴涨；统计的是Unicode码点而非字节数</p>
     */
    private int maxYamlCodePoints = 32768;

    /**
     * YAML最大嵌套层级深度，默认12层
     * <p>防御YAML深度嵌套炸弹，防止解析栈溢出</p>
     */
    private int maxYamlNestingDepth = 12;
}
