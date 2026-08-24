package com.agentdoc.task.convertor;

import com.agentdoc.common.feign.dto.ChangeItemDTO;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.task.enums.ActorType;
import com.agentdoc.task.enums.ChangeRequestStatus;
import com.agentdoc.task.enums.ChangeRequestType;
import com.agentdoc.task.pojo.dto.ChangeRequestSubmitDTO;
import com.agentdoc.task.pojo.entity.ChangeRequestEntity;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.vo.ChangeRequestVO;
import com.agentdoc.task.runtime.AgentExecutionResult;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

/**
 * 变更请求转换器：实体 ↔ 视图 / JSON 的复杂转换统一入口（Service 经本类转换，不直接持有转换逻辑）。
 * <p>涉及 changes JSON 解析/序列化、文档标题回填、批量映射，归属转换器而非实体。</p>
 */
public final class ChangeRequestConvertor {

    private ChangeRequestConvertor() {
    }

    /**
     * 将用户提交参数转换为待审批变更请求。
     */
    public static ChangeRequestEntity fromHumanSubmission(ChangeRequestSubmitDTO dto, Long userId) {
        ChangeRequestEntity entity = new ChangeRequestEntity();
        entity.setDocumentId(dto.documentId());
        entity.setRequestType(dto.requestType().getCode());
        entity.setChanges(serializeChanges(dto.changes()));
        entity.setBaseVersion(dto.baseVersion());
        entity.setStatus(ChangeRequestStatus.PENDING.getCode());
        entity.setProposedBy(userId);
        entity.setProposedActorType(ActorType.HUMAN.getCode());
        return entity;
    }

    /**
     * 将 Agent 执行结果转换为待审批正式文档变更请求。
     */
    public static ChangeRequestEntity fromAgentSubmission(
            TaskEntity task, AgentExecutionResult result, Long baseVersion) {
        ChangeRequestEntity entity = new ChangeRequestEntity();
        entity.setDocumentId(task.getDocumentId());
        entity.setRequestType(ChangeRequestType.FORMAL.getCode());
        entity.setChanges(serializeChanges(result.changes()));
        entity.setBaseVersion(baseVersion);
        entity.setStatus(ChangeRequestStatus.PENDING.getCode());
        entity.setSourceTaskId(task.getId());
        entity.setProposedBy(task.getAgentId());
        entity.setProposedActorType(ActorType.AGENT.getCode());
        return entity;
    }

    /**
     * 实体转视图（单条；文档标题由调用方经 Feign 查询后传入）。
     * @param entity 变更请求实体
     * @param documentTitle 文档标题（可为 null）
     * @return 变更请求视图
     */
    public static ChangeRequestVO toVO(ChangeRequestEntity entity, String documentTitle) {
        return new ChangeRequestVO(
                entity.getId(), entity.getDocumentId(), documentTitle,
                ChangeRequestType.fromCode(entity.getRequestType()),
                parseChanges(entity.getChanges()),
                ChangeRequestStatus.fromCode(entity.getStatus()),
                entity.getSourceTaskId(), entity.getProposedBy(), entity.getReviewComment(),
                entity.getCreatedAt());
    }

    /**
     * 实体列表转视图（批量：一次加载的引用投影回填标题后统一映射，避免逐条转换）。
     * @param entities 变更请求实体列表
     * @param refs 文档 ID → 引用投影（调用方经 Feign 批量查询）
     * @return 变更请求视图列表
     */
    public static List<ChangeRequestVO> toVOList(List<ChangeRequestEntity> entities, Map<Long, DocumentRefVO> refs) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(e -> {
                    DocumentRefVO ref = refs.get(e.getDocumentId());
                    return toVO(e, ref == null ? null : ref.title());
                })
                .toList();
    }

    /**
     * 解析 changes JSON 为结构化变更列表（历史脏数据返回空列表）。
     * @param json changes JSON
     * @return 变更列表
     */
    public static List<ChangeItemDTO> parseChanges(String json) {
        List<ChangeItemDTO> result = JsonUtils.parse(json, new TypeReference<List<ChangeItemDTO>>() {
        });
        return result == null ? List.of() : result;
    }

    /**
     * 序列化结构化变更列表为 JSON（存入 changes 字段）。
     * @param items 变更列表
     * @return JSON 字符串
     */
    public static String serializeChanges(List<ChangeItemDTO> items) {
        return JsonUtils.toJson(items);
    }
}
