package com.agentdoc.agent.service;

import com.agentdoc.agent.enums.SkillEntryType;

/**
 * 规范化 Skill 文件清单项
 * <p>
 * 代表技能包内单个文件/目录条目，由 {@link ParsedSkillPackage} 持有。
 * 记录路径、条目类型、文件大小、文件哈希、运行时是否可读标记。
 * runtimeReadable 控制该资源是否可以在Agent执行阶段被加载读取。
 * </p>
 *
 * @param path           包内相对路径，例如 docs/readme.md、scripts/main.js
 * @param type           条目类型：文件 / 目录 {@link SkillEntryType}
 * @param size           文件字节大小；目录时填0
 * @param sha256         当前文件内容SHA‑256摘要；目录可为null
 * @param runtimeReadable 运行时是否允许Agent读取该资源；false代表仅用于包管理，执行期不可访问
 */
public record SkillPackageEntry(
        String path,
        SkillEntryType type,
        long size,
        String sha256,
        boolean runtimeReadable) {
}
