package com.agentdoc.common.feign.dto;
import com.agentdoc.common.utils.ProtocolStringDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/** @param experimentId 预分配实验身份
 * @param spaceId 所属空间
 * @param agentId 当前 Agent
 * @param requestHash Evaluation 创建意图摘要
 * @param candidateAgentPrompt 候选系统提示词 */
public record AgentOnlineConfigPrepareDTO(
                                         @JsonDeserialize(using = ProtocolStringDeserializer.class) String experimentId,
                                         @JsonDeserialize(using = ProtocolStringDeserializer.class) String spaceId,
                                         @JsonDeserialize(using = ProtocolStringDeserializer.class) String agentId,
                                         String requestHash, String candidateAgentPrompt) { }
