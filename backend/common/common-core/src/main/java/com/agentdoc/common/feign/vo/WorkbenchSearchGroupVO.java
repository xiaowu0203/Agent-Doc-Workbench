package com.agentdoc.common.feign.vo;

import java.util.List;

/**
 * 单类工作台搜索结果。
 *
 * @param records 当前返回记录
 * @param total 匹配总数
 */
public record WorkbenchSearchGroupVO(List<WorkbenchSearchItemVO> records, long total) {

    public static WorkbenchSearchGroupVO empty() {
        return new WorkbenchSearchGroupVO(List.of(), 0);
    }
}
