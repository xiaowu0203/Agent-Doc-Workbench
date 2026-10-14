package com.agentdoc.evaluation.constant;

/** 首版线上领域不变量；非部署配置。 */
public final class OnlineExperimentConstant {
    public static final int MAX_DOCUMENTS = 1000;
    public static final int MAX_TASKS = 10000;
    public static final int MAX_MANIFEST_BYTES = 1048576;
    public static final int DEFAULT_ASSIGNMENT_SECONDS = 604800;
    public static final int MAX_ASSIGNMENT_SECONDS = 2592000;
    public static final int DEFAULT_OBSERVATION_SECONDS = 172800;
    public static final int MAX_OBSERVATION_SECONDS = 604800;
    public static final int PREFLIGHT_SECONDS = 60;
    public static final int MIN_DOCUMENTS_PER_GROUP = 30;
    public static final int MIN_COVERAGE_BPS = 8000;
    public static final int HEALTH_WINDOW_COUNT = 20;
    public static final int HEALTH_FAILURE_BPS = 3000;
    public static final int UNRESOLVED_SECONDS = 300;
    public static final int SRM_MIN_DOCUMENTS = 100;
    public static final int SRM_MIN_EXPECTED_COUNT = 10;
    public static final String SRM_THRESHOLD = "0.001";
    private OnlineExperimentConstant() { }
}
