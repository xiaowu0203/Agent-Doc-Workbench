package com.agentdoc.evaluation.enums;

/** 幂等动作不能由客户端自由指定。 */
public enum OnlineExperimentAction {
    START, PAUSE, RESUME, STOP, EMERGENCY_STOP, REAUTHORIZE
}
