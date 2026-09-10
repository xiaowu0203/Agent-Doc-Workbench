package com.agentdoc.common.feign.vo;

import com.agentdoc.common.enums.WorkbenchSearchType;

import java.time.LocalDateTime;

/**
 * 工作台全局搜索结果项。
 *
 * @param type 资源类型
 * @param id 资源 ID
 * @param title 主标题
 * @param subtitle 辅助说明
 * @param status 状态或类型标识
 * @param updatedAt 最近更新时间
 */
public record WorkbenchSearchItemVO(
        WorkbenchSearchType type,
        Long id,
        String title,
        String subtitle,
        String status,
        LocalDateTime updatedAt
) {
}
