package com.agentdoc.common.pojo.vo;

import com.agentdoc.common.pojo.dto.PageParam;

import java.util.List;

/**
 * 统一分页响应体，所有分页查询接口统一返回该结构。
 *
 * @param <T> 列表元素类型
 */
public record PageVO<T>(List<T> records, long total, int pageNum, int pageSize) {

    /**
     * 构造分页响应。
     * @param records  当前页数据
     * @param total    总记录数
     * @param pageParam 分页请求参数（已校验）
     * @param <T>      列表元素类型
     * @return 分页响应
     */
    public static <T> PageVO<T> of(List<T> records, long total, PageParam pageParam) {
        return new PageVO<>(records, total, pageParam.getPageNum(), pageParam.getPageSize());
    }
}
