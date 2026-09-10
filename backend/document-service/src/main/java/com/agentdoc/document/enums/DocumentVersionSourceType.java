package com.agentdoc.document.enums;

/** 文档版本来源。 */
public enum DocumentVersionSourceType {
    UNKNOWN,
    CREATE,
    HUMAN_EDIT,
    AGENT_DRAFT,
    APPROVAL_MERGE,
    ROLLBACK
}

