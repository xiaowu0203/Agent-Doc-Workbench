package com.agentdoc.common.constant;

import java.util.Set;

/** 线上保护窄凭证协议；不包含模型或文档写动作。 */
public final class OnlineCapabilityConstant {
    public static final String HEADER = "X-ONLINE-CAPABILITY";
    public static final String PURPOSE = "onlinePurpose";
    public static final String SCHEMA = "onlineSchemaVersion";
    public static final String EXPERIMENT_ID = "onlineExperimentId";
    public static final String MANIFEST_HASH = "onlineManifestHash";
    public static final String AUTHORIZED_BY = "onlineAuthorizedBy";
    public static final String TASK_SET_HASH = "onlineTaskIdsHash";
    public static final String WAIT_AUDIENCE = "online-assignment-wait";
    public static final String WAIT_PURPOSE = "ONLINE_WAIT";
    public static final String WAIT_SCOPE = "online-wait";
    public static final String TASK_SERVICE = "task-service";
    public static final String ASSIGNMENT_ID = "onlineAssignmentId";
    public static final String BINDING_SCHEMA = "onlineBindingSchemaVersion";
    public static final String BINDING_HASH = "onlineBindingHash";
    public static final String SLOT_GENERATION = "onlineSlotGeneration";
    public static final String SLOT_PERMIT_HASH = "onlineSlotPermitHash";
    public static final long TTL_SECONDS = 300;
    public static final long RENEW_BEFORE_SECONDS = 60;
    public static final int MAX_TASK_COUNT = 100;
    public static final int MAX_TOKEN_LENGTH = 16384;
    /** 保护授权只覆盖尚未完全收敛的实验。 */
    public static final Set<String> AUTHORIZABLE_STATUSES = Set.of("CREATED", "ACTIVE", "PAUSED", "STOPPING");
    /** 首版只覆盖新 ORIGINAL 线上任务，不扩张离线/派生身份。 */
    public static final String ORIGINAL_LINEAGE = "ORIGINAL";
    public static final Set<String> INTERNAL_PREFIXES = Set.of("/api/auth/internal/online-",
            "/api/document/internal/online-", "/api/evaluation/internal/online-",
            "/api/task/internal/online-", "/api/agent/internal/online-");
    private OnlineCapabilityConstant() { }
}
