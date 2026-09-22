package com.agentdoc.evaluation.enums;

/**
 * 版本化评估资源状态枚举。
 * <p>
 * 数据集版本、测试用例版本、评估器版本共用的状态生命周期；
 * DRAFT草稿可编辑，PUBLISHED发布后内容固化不可修改，ARCHIVED归档后不再参与新评估。
 * </p>
 */
public enum EvaluationVersionStatus {
    /** 草稿，可编辑修改，尚未发布 */
    DRAFT,
    /** 已发布，内容固化，不可变更 */
    PUBLISHED,
    /** 已归档，不再用于新建评估运行 */
    ARCHIVED
}
