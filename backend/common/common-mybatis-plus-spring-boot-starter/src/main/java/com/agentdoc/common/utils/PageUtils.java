package com.agentdoc.common.utils;

import com.agentdoc.common.pojo.dto.PageParam;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * MyBatis-Plus 分页工具：将统一分页参数 {@link PageParam} 转换为 MP 分页对象。
 * <p>
 * 因 common-core 为纯库不依赖 mybatis-plus-extension，故实现落位于
 * common-mybatis-plus-spring-boot-starter（使用方需已引入该 starter）。
 * </p>
 */
public final class PageUtils {

    private PageUtils() {
    }

    /**
     * 校验分页参数并转换为 MP 分页对象（页码从 1 开始，每页上限 100）。
     * @param param 统一分页参数，不允许为 null
     * @return MP 分页对象
     * @throws com.agentdoc.common.exception.BusinessException 参数非法时抛出（{@code BAD_REQUEST}）
     */
    public static <T> Page<T> toPage(PageParam param) {
        param.validate();
        return new Page<>(param.getPageNum(), param.getPageSize());
    }
}
