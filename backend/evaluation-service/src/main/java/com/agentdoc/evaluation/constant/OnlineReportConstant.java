package com.agentdoc.evaluation.constant;

/** 线上报告逻辑协议及物理输入存储边界，不随部署环境变化。 */
public final class OnlineReportConstant {
    public static final String TYPE = "ONLINE_V1";
    public static final int SCHEMA_VERSION = 2;
    public static final int INPUT_CHUNK_CHARACTERS = 262144;
    public static final int MAX_INPUT_CHUNKS = 256;
    private OnlineReportConstant() { }
}
