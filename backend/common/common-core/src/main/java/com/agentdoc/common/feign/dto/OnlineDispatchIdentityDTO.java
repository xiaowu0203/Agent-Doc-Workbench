package com.agentdoc.common.feign.dto;

/** 派发时必须完整的线上身份；非参与任务整个对象为空。
 * @param experimentId 实验
 * @param assignmentId 分配
 * @param bindingSchemaVersion 绑定版本
 * @param bindingHash 不可变绑定摘要
 * @param generation 权威槽代次；WAIT 阶段为空
 * @param permitHash 权威槽证明；WAIT 阶段为空 */
public record OnlineDispatchIdentityDTO(String experimentId, String assignmentId, Integer bindingSchemaVersion,
        String bindingHash, Long generation, String permitHash) { }
