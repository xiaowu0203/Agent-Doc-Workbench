package com.agentdoc.common.pojo.dto;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import lombok.Data;

/**
 * 分页请求参数，所有分页查询接口统一接收前端传入。
 * <p>
 * 提供默认参数（pageNum=1 / pageSize=10）与参数校验方法 {@link #validate()}，
 * 使用方式：Controller 直接接收本对象，业务入口先调用 validate() 再执行查询。
 * </p>
 */
@Data
public class PageParam {

    /** 默认页码 */
    public static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页条数上限，防止一次拉取过多数据 */
    public static final int MAX_PAGE_SIZE = 100;

    /** 页码，从 1 开始，默认 1 */
    private Integer pageNum = DEFAULT_PAGE_NUM;

    /** 每页条数，默认 10 */
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    /**
     * 参数校验：页码从 1 开始，每页条数在 1~{@link #MAX_PAGE_SIZE} 之间。
     * 校验失败抛出 {@link BusinessException}（{@link ErrorCode#BAD_REQUEST}）。
     */
    public void validate() {
        if (pageNum == null || pageNum < DEFAULT_PAGE_NUM) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "页码 pageNum 必须大于等于 1");
        }
        if (pageSize == null || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "每页条数 pageSize 必须在 1~" + MAX_PAGE_SIZE + " 之间");
        }
    }
}
