package com.agentdoc.common.utils;

import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.BadJwtException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** 固定用途隔离与 Task 集合规范化；不裁决业务授权。 */
public final class OnlineCapabilityUtils {
    private OnlineCapabilityUtils() { }

    public static Jwt rejectGeneralAccess(Jwt jwt) {
        if (jwt.hasClaim(OnlineCapabilityConstant.PURPOSE)
                || jwt.getAudience() != null && (jwt.getAudience().contains(OnlineCapabilityConstant.WAIT_AUDIENCE)
                || Arrays.stream(OnlineCapabilityPurpose.values()).anyMatch(p -> jwt.getAudience().contains(p.audience())))) {
            throw new BadJwtException("线上窄凭证不能用于普通鉴权入口");
        }
        return jwt;
    }

    public static List<String> taskIds(List<String> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > OnlineCapabilityConstant.MAX_TASK_COUNT
                || new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("线上 Task 集合非法");
        }
        ids.forEach(OnlineProtocolUtils::id);
        return ids.stream().sorted(Comparator.comparingLong(OnlineProtocolUtils::id)).toList();
    }

    public static String taskSetHash(List<String> ids) {
        return OnlineProtocolUtils.hash("online.task-set", Map.of("taskIds", taskIds(ids)));
    }

    /** 内部 HTTP 集合入口仍拒绝 JSON 数字自动转字符串，避免长 ID 精度丢失。 */
    public static List<String> parseTaskIds(String json) {
        JsonNode node = JsonUtils.parseStrict(json, JsonNode.class);
        if (node == null || !node.isArray() || node.isEmpty() || node.size() > OnlineCapabilityConstant.MAX_TASK_COUNT) {
            throw new IllegalArgumentException("线上 Task 集合非法");
        }
        var ids = new ArrayList<String>();
        for (JsonNode item : node) {
            if (!item.isTextual()) { throw new IllegalArgumentException("线上 Task ID 必须为文本"); }
            ids.add(item.textValue());
        }
        return taskIds(ids);
    }

    public static boolean internalPath(String path) {
        return OnlineCapabilityConstant.INTERNAL_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
