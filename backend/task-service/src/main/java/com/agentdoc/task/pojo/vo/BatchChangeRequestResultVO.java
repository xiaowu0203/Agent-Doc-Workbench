package com.agentdoc.task.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 批量审批结果；单条冲突不影响其他请求。 */
@Schema(description = "批量审批结果")
public record BatchChangeRequestResultVO(
        List<Long> succeededIds,
        List<Failure> failures) {

    public record Failure(Long id, Integer code, String message) {
    }
}
