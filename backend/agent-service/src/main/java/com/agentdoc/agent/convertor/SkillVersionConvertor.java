package com.agentdoc.agent.convertor;

import com.agentdoc.agent.enums.SkillVersionStatus;
import com.agentdoc.agent.pojo.entity.SkillVersionEntity;
import com.agentdoc.agent.pojo.vo.SkillVersionVO;
import com.agentdoc.agent.skill.archive.SkillPackageEntry;
import com.agentdoc.common.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;

/**
 * Skill版本转换器
 * <p>负责{@link SkillVersionEntity}数据库实体转为视图VO；
 * 同时提供内部JSON解析工具，提取允许工具列表、运行时可读资源路径</p>
 */
public final class SkillVersionConvertor {

    private SkillVersionConvertor() {
    }

    /**
     * Skill版本实体转换为对外VO
     *
     * @param entity Skill版本数据库实体，非null
     * @return 组装完成的 {@link SkillVersionVO}
     */
    public static SkillVersionVO toVO(SkillVersionEntity entity) {
        return new SkillVersionVO(entity.getId(), entity.getSkillId(), entity.getVersionNo(),
                SkillVersionStatus.fromCode(entity.getStatus()), entity.getActivationDescription(),
                entity.getSha256(), entity.getPackageSize(),
                readJsonList(entity.getAllowedToolsJson()), readReadablePaths(entity.getManifestJson()),
                entity.getCreatedBy(), entity.getCreatedAt(), entity.getPublishedAt());
    }

    /**
     * 对象序列化为JSON字符串
     *
     * @param value 待序列化对象
     * @return JSON字符串
     */
    public static String toJson(Object value) {
        return JsonUtils.toJson(value);
    }

    /**
     * 解析JSON字符串为字符串列表，null兜底返回空集合
     *
     * @param json JSON数组字符串（如allowedToolsJson）
     * @return 字符串列表；解析为null返回空集合{@code List.of()}
     */
    private static List<String> readJsonList(String json) {
        List<String> values = JsonUtils.parse(json, new TypeReference<List<String>>() { });
        return values == null ? List.of() : values;
    }

    /**
     * 从manifest‑json中过滤提取运行时可读的资源路径
     * <p>解析{@link SkillPackageEntry}数组，只保留标记runtimeReadable=true的path路径</p>
     *
     * @param json manifestJson，存储Skill包条目数组JSON
     * @return 可读资源路径列表；解析为null返回空集合{@code List.of()}
     */
    private static List<String> readReadablePaths(String json) {
        List<SkillPackageEntry> entries = JsonUtils.parse(json, new TypeReference<List<SkillPackageEntry>>() { });
        return entries == null ? List.of() : entries.stream()
                .filter(SkillPackageEntry::runtimeReadable)
                .map(SkillPackageEntry::path)
                .toList();
    }
}
