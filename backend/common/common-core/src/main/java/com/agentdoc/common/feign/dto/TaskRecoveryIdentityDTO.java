package com.agentdoc.common.feign.dto;

import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.common.utils.OnlineIdentityUtils;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 恢复时由 Task 数据库重新读取的冻结身份。
 * @param taskId 既有任务 ID
 * @param agentId 冻结 Agent ID
 * @param spaceId 空间 ID
 * @param documentId 冻结文档 ID
 * @param executionMode 执行模式
 * @param documentVersionSnapshot 冻结文档版本
 * @param documentContentSha256 冻结正文哈希
 * @param inputSnapshotSchemaVersion 输入协议版本
 * @param inputSnapshotHash 冻结输入哈希
 * @param derivationRequestHash 可空的派生请求哈希
 */
public record TaskRecoveryIdentityDTO(Long taskId, Long agentId, Long spaceId, Long documentId,
                                      String executionMode, Long documentVersionSnapshot,
                                      String documentContentSha256, Integer inputSnapshotSchemaVersion,
                                      String inputSnapshotHash, String derivationRequestHash,
                                      @JsonInclude(JsonInclude.Include.NON_NULL) OnlineDispatchIdentityDTO onlineIdentity) {

    /** 固定声明映射，用于签发与两端逐字段核验；不包含正文或秘密。 */
    public Map<String, Object> toClaims() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(JwtConstant.CLAIM_TASK_ID, taskId);
        claims.put(JwtConstant.CLAIM_AGENT_ID, agentId);
        claims.put(JwtConstant.CLAIM_SPACE_ID, spaceId);
        claims.put(JwtConstant.CLAIM_DOCUMENT_ID, documentId);
        claims.put(JwtConstant.CLAIM_EXECUTION_MODE, executionMode);
        claims.put(JwtConstant.CLAIM_DOCUMENT_VERSION_SNAPSHOT, documentVersionSnapshot);
        claims.put(JwtConstant.CLAIM_DOCUMENT_CONTENT_SHA256, documentContentSha256);
        claims.put(JwtConstant.CLAIM_INPUT_SNAPSHOT_SCHEMA_VERSION, inputSnapshotSchemaVersion);
        claims.put(JwtConstant.CLAIM_INPUT_SNAPSHOT_HASH, inputSnapshotHash);
        claims.put(JwtConstant.CLAIM_DERIVATION_REQUEST_HASH, derivationRequestHash);
        if (onlineIdentity != null) {
            OnlineIdentityUtils.requireComplete(onlineIdentity);
            claims.put(OnlineCapabilityConstant.EXPERIMENT_ID, onlineIdentity.experimentId());
            claims.put(OnlineCapabilityConstant.ASSIGNMENT_ID, onlineIdentity.assignmentId());
            claims.put(OnlineCapabilityConstant.BINDING_SCHEMA, onlineIdentity.bindingSchemaVersion());
            claims.put(OnlineCapabilityConstant.BINDING_HASH, onlineIdentity.bindingHash());
            claims.put(OnlineCapabilityConstant.SLOT_GENERATION, onlineIdentity.generation());
            claims.put(OnlineCapabilityConstant.SLOT_PERMIT_HASH, onlineIdentity.permitHash());
        }
        return claims;
    }
}
