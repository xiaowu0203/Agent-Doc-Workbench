package com.agentdoc.agent.service;

import com.agentdoc.agent.config.SkillPackageProperties;
import com.agentdoc.agent.constant.SkillConstant;
import com.agentdoc.agent.enums.SkillStatus;
import com.agentdoc.agent.enums.SkillVersionStatus;
import com.agentdoc.agent.execution.runtime.SkillExecutionSnapshot;
import com.agentdoc.agent.mapper.AgentSkillMapper;
import com.agentdoc.agent.mapper.SkillMapper;
import com.agentdoc.agent.mapper.SkillVersionMapper;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.AgentSkillEntity;
import com.agentdoc.agent.pojo.entity.SkillEntity;
import com.agentdoc.agent.pojo.entity.SkillVersionEntity;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Skill执行快照服务
 * <p>
 * 执行开始时解析Agent绑定的Skill版本并生成不可变 Skill 快照。
 * 快照为本次Agent任务隔离只读视图，任务生命周期内不会随Skill/版本库变更而变化；
 * 完成绑定校验、状态校验、工具白名单合并、技能提示片段拼接、完整性哈希计算、大小校验，
 * 输出 {@link SkillExecutionSnapshot}，供后续提示词组装、MCP工具过滤、任务重放使用。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class SkillSnapshotService {

    private final AgentSkillMapper agentSkillMapper;
    private final SkillMapper skillMapper;
    private final SkillVersionMapper versionMapper;
    private final SkillPackageProperties skillPackageProperties;

    /**
     * 为指定Agent生成本次任务隔离的技能执行快照
     * <p>
     * 执行流程：
     * <ol>
     * <li>查询Agent已启用的Skill绑定关系</li>
     * <li>批量查询Skill本体与绑定的版本实体，做数据完整性、空间归属、状态校验</li>
     * <li>解析版本内工具白名单、资源文件清单，构造单条绑定技能快照</li>
     * <li>合并Skill工具集与Agent全局工具白名单，计算最终生效工具集合</li>
     * <li>拼接全部技能的提示片段，计算快照SHA‑256哈希用于缓存与溯源</li>
     * <li>校验全部Skill指令总字节上限，超限抛出业务异常</li>
     * <li>收集可读资源路径，序列化快照JSON，返回完整快照对象</li>
     * </ol>
     * </p>
     *
     * @param agent Agent数据库实体
     * @return 不可变的技能执行快照 {@link SkillExecutionSnapshot}，本次任务全程复用
     * @throws BusinessException 绑定数据无效、Skill停用、版本未发布、JSON解析失败、指令超上限抛出
     */
    public SkillExecutionSnapshot snapshot(AgentEntity agent) {
        // 查询Agent下已启用的Skill绑定关系，按skillId排序保证快照顺序稳定
        List<AgentSkillEntity> bindings = agentSkillMapper.selectList(
                new LambdaQueryWrapper<AgentSkillEntity>()
                        .eq(AgentSkillEntity::getAgentId, agent.getId())
                        .eq(AgentSkillEntity::getEnabled, true)
                        .orderByAsc(AgentSkillEntity::getSkillId));

        // 批量查询Skill本体
        Map<Long, SkillEntity> skillById = bindings.isEmpty() ? Map.of() : skillMapper.selectBatchIds(
                        bindings.stream().map(AgentSkillEntity::getSkillId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(SkillEntity::getId, value -> value));

        // 批量查询绑定的Skill版本
        Map<Long, SkillVersionEntity> versionById = bindings.isEmpty() ? Map.of() : versionMapper.selectBatchIds(
                        bindings.stream().map(AgentSkillEntity::getSkillVersionId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(SkillVersionEntity::getId, value -> value));


        List<SkillExecutionSnapshot.BoundSkillSnapshot> skills = new ArrayList<>();
        for (AgentSkillEntity binding : bindings) {
            SkillEntity skill = skillById.get(binding.getSkillId());
            SkillVersionEntity version = versionById.get(binding.getSkillVersionId());
            // 校验绑定引用完整性 + 空间归属，防止跨空间越权绑定
            if (skill == null || version == null || !agent.getSpaceId().equals(skill.getSpaceId())) {
                throw new BusinessException(ErrorCode.CONFLICT, "Agent Skill 绑定数据无效");
            }
            // Skill必须为启用状态
            if (!SkillStatus.ACTIVE.matches(skill.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "绑定的 Skill 已停用");
            }
            // 绑定的版本必须是已发布版本
            if (!SkillVersionStatus.PUBLISHED.matches(version.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "绑定的 Skill 版本未发布");
            }

            // 解析版本存储的工具白名单、资源文件清单
            List<String> tools = parseTools(version.getAllowedToolsJson());
            List<SkillPackageEntry> entries = parseEntries(version.getManifestJson());
            List<SkillPackageEntry> readable = entries.stream()
                    .filter(SkillPackageEntry::runtimeReadable).toList();

            skills.add(new SkillExecutionSnapshot.BoundSkillSnapshot(skill.getId(), version.getId(),
                    version.getVersionNo(), skill.getName(), version.getSha256(), version.getStorageKey(),
                    version.getInstructionText(), tools, readable));
        }

        // 合并所有Skill导出的工具，去重并排序，保证快照确定性
        List<String> skillTools = skills.stream().flatMap(value -> value.allowedTools().stream())
                .distinct().sorted().toList();
        // 解析Agent全局工具白名单
        List<String> agentTools = parseAgentTools(agent.getToolWhitelist());
        // 计算最终生效工具：Agent白名单不为空时取交集；无绑定Skill时返回null
        List<String> effectiveTools = skills.isEmpty() && agent.getToolWhitelist() == null
                ? null
                : agent.getToolWhitelist() == null ? skillTools
                : skillTools.stream().filter(agentTools::contains).toList();

        // 按skillId稳定排序，拼接全部Skill的提示片段
        String promptSection = skills.stream()
                .sorted(Comparator.comparing(SkillExecutionSnapshot.BoundSkillSnapshot::skillId))
                .map(this::promptSection).reduce("", (left, right) -> left + right);

        // 生成快照哈希：参与因子包含skillId、名称、版本号、包sha256、技能指令文本，顺序稳定
        String skillHash = hash(skills.stream()
                .sorted(Comparator.comparing(SkillExecutionSnapshot.BoundSkillSnapshot::skillId))
                .map(value -> value.name() + value.versionNo()
                        + value.sha256() + value.instructionText())
                .reduce("", String::concat));

        // 校验全部Skill指令文本总大小，超过配置阈值直接拒绝
        if (skills.stream().mapToInt(value ->
                value.instructionText().getBytes(StandardCharsets.UTF_8).length).sum()
                > skillPackageProperties.getMaxSkillInstructionsSize().toBytes()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Skill 指令正文超过限制");
        }

        // 收集全部可读资源文件路径，去重排序，用于运行时资源加载
        List<String> readableResourcePaths = skills.stream()
                .flatMap(value -> value.readableResources().stream().map(SkillPackageEntry::path))
                .distinct().sorted().toList();

        // 将绑定技能快照序列化为JSON，存入执行记录用于任务重放
        String snapshotJson = writeJson(skills);
        return new SkillExecutionSnapshot(List.copyOf(skills), readableResourcePaths,
                List.copyOf(effectiveTools), snapshotJson, skillHash, promptSection);
    }

    /**
     * 生成单个绑定Skill的提示片段文本，用于拼入系统提示词
     *
     * @param skill 单条绑定技能快照
     * @return 带分隔标记的技能指令片段字符串
     */
    private String promptSection(SkillExecutionSnapshot.BoundSkillSnapshot skill) {
        return "\n\n## Bound Skill: " + skill.name() + "@" + skill.versionNo()
                + "\nPackage-SHA256: " + skill.sha256()
                + "\n--- BEGIN SKILL INSTRUCTIONS ---\n" + skill.instructionText()
                + "\n--- END SKILL INSTRUCTIONS ---";
    }

    /**
     * 解析Agent级别工具白名单JSON
     *
     * @param json Agent工具白名单JSON字符串，允许null/空白
     * @return 工具名称列表；输入为空返回空集合
     * @throws BusinessException JSON格式非法抛出
     */
    private List<String> parseAgentTools(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<String> tools = JsonUtils.parse(json, new TypeReference<List<String>>() { });
        if (tools == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "Agent 工具白名单数据非法");
        }
        return tools;
    }

    /**
     * 解析Skill版本内工具白名单JSON
     *
     * @param json 版本存储的allowedToolsJson，允许null/空白
     * @return 工具名称列表；输入为空返回空集合
     * @throws BusinessException JSON格式非法抛出
     */
    private List<String> parseTools(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<String> tools = JsonUtils.parse(json, new TypeReference<List<String>>() { });
        if (tools == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "Skill 工具白名单数据非法");
        }
        return tools;
    }

    /**
     * 解析Skill版本存储的manifest清单JSON，得到包内文件条目
     *
     * @param json manifestJson，允许null/空白
     * @return 包文件条目列表；输入为空返回空集合
     * @throws BusinessException JSON格式非法抛出
     */
    private List<SkillPackageEntry> parseEntries(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<SkillPackageEntry> entries = JsonUtils.parse(json, new TypeReference<List<SkillPackageEntry>>() { });
        if (entries == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "Skill 文件清单数据非法");
        }
        return entries;
    }

    /**
     * 将对象序列化为快照JSON字符串
     *
     * @param value 待序列化对象
     * @return JSON文本
     */
    private String writeJson(Object value) {
        return JsonUtils.toJson(value);
    }

    /**
     * 对输入字符串计算SHA‑256十六进制哈希，用于快照标识与缓存key
     *
     * @param value 原始输入字符串
     * @return 小写十六进制SHA‑256摘要
     * @throws IllegalStateException JVM不支持SHA‑256算法时抛出
     */
    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(SkillConstant.SHA_256)
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
