package com.agentdoc.evaluation.enums;

/** 已核验会返回分组或配置对应关系的当前人类入口。 */
public enum OnlineReviewExposureSource {
    REPORT,
    RELATED_RESOURCE_READ,
    EXPERIMENT_PREFLIGHT,
    EXPERIMENT_LIST,
    /** 创建或重试返回基线/候选配置。 */ EXPERIMENT_CREATE,
    /** 实验详情返回两组配置身份。 */ EXPERIMENT_DETAIL,
    /** 分配列表或分组过滤结果返回对应关系。 */ ASSIGNMENT_LIST
}
