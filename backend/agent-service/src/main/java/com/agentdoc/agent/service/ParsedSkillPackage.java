package com.agentdoc.agent.service;

import java.util.List;

/**
 * 解析完成后的技能包结构化记录
 * <p>
 * 对应原始技能压缩包解析后的内存模型，保存元信息、工具白名单、条目列表、大小统计、校验哈希。
 * 由 SkillPackageParser 解析压缩包得到，用于后续生成 {@link SkillExecutionSnapshot}。
 * </p>
 *
 * @param name               技能包名称
 * @param description        技能包描述说明
 * @param instructionText    技能包内置指令文本，会注入系统提示词
 * @param allowedTools       本技能包允许调用的工具名称白名单（MCP/内置工具名集合）
 * @param entries            技能包内部条目列表，包含脚本、资源文件、配置等 {@link SkillPackageEntry}
 * @param packageSize        原始压缩包文件大小(字节)
 * @param uncompressedSize   解压后总大小(字节)
 * @param fileCount          包内总文件条目数量
 * @param readableResourcesSize 可读取资源文件总字节大小（过滤元文件/目录，用于提示词长度统计）
 * @param sha256             原始技能包文件的SHA‑256摘要哈希，用于完整性校验、缓存键
 */
public record ParsedSkillPackage(
        String name,
        String description,
        String instructionText,
        List<String> allowedTools,
        List<SkillPackageEntry> entries,
        long packageSize,
        long uncompressedSize,
        int fileCount,
        long readableResourcesSize,
        String sha256) {
}
