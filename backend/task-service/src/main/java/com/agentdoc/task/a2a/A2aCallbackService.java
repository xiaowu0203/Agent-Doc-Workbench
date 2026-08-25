package com.agentdoc.task.a2a;

import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.service.TaskService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.a2aproject.sdk.spec.Task;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * A2A Agent‑to‑Agent 回调接收服务
 * <p>
 * 负责接收远端Agent Server推送的任务回调事件，完成JWT令牌校验、权限范围校验、
 * 拉取远端任务数据，同步远端任务状态至本地工作台任务实体。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class A2aCallbackService {

    private final TaskCapabilityVerifier capabilityVerifier;
    private final TaskService taskService;
    private final A2aTaskClient a2aTaskClient;
    private final A2aTaskSynchronizationService synchronizationService;

    /**
     * 接收A2A远端回调通知入口
     * <p>流程：校验回调令牌 → 解析本地任务ID → 获取本地任务 → 校验令牌权限范围
     * → 本地任务已终态直接返回 → 解析事件获取远端taskId → 拉取远端A2A任务
     * → 执行本地‑远端任务状态同步</p>
     *
     * @param event           A2A回调事件JSON报文
     * @param notificationToken A2A回调鉴权JWT令牌
     * @throws BusinessException 令牌非法、权限不匹配、任务不存在、远端无任务等业务异常
     */
    public void receive(JsonNode event, String notificationToken) {
        // 校验回调通知令牌合法性
        Jwt jwt = capabilityVerifier.verify(notificationToken);
        // 从JWT载荷取出工作台本地任务ID
        Long workbenchTaskId = longClaim(jwt, JwtConstant.CLAIM_TASK_ID);
        // 获取本地工作台任务，不存在抛出业务异常
        TaskEntity task = taskService.require(workbenchTaskId);
        // 校验JWT令牌的Agent/Space/Document作用域与本地任务一致，防止越权回调
        requireScope(task, jwt);
        // 本地任务已是终态(完成/终止/失败)，不再处理回调
        if (isFinal(task.getStatus())) {
            return;
        }
        // 从回调事件中解析远端A2A任务ID
        String a2aTaskId = taskId(event);
        // 使用回调令牌调用Agent‑Server接口拉取远端最新任务数据
        Task remoteTask = a2aTaskClient.get(a2aTaskId, notificationToken);
        if (remoteTask == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Agent Server 未返回 A2A Task");
        }
        // 将远端任务状态、结果同步更新至本地工作台任务
        synchronizationService.synchronize(task, remoteTask);
    }

    /**
     * 从回调事件JsonNode提取远端A2A taskId
     * <p>兼容两种字段：优先取taskId，取不到则降级取id字段</p>
     *
     * @param event A2A回调事件报文节点
     * @return 远端A2A任务ID，非空字符串
     * @throws BusinessException 事件为空或缺少任务ID时抛出BAD_REQUEST
     */
    private String taskId(JsonNode event) {
        if (event == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "A2A 回调事件不能为空");
        }
        // 提取taskId
        String taskId = event.path("taskId").asText(null);
        if (taskId == null) {
            // 提取id
            taskId = event.path("id").asText(null);
        }
        if (StringUtils.isBlank(taskId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "A2A 回调事件缺少 taskId");
        }
        return taskId;
    }

    /**
     * JWT作用域权限校验
     * <p>校验令牌中的 agentId / spaceId / documentId 与本地任务完全匹配，
     * 防止使用合法JWT回调篡改其它空间/文档下的任务</p>
     *
     * @param task 本地工作台任务实体
     * @param jwt  回调通知JWT令牌
     * @throws BusinessException FORBIDDEN 范围不匹配抛出禁止访问
     */
    private void requireScope(TaskEntity task, Jwt jwt) {
        if (!String.valueOf(task.getAgentId()).equals(jwt.getClaimAsString(JwtConstant.CLAIM_AGENT_ID))
                || !String.valueOf(task.getSpaceId()).equals(jwt.getClaimAsString(JwtConstant.CLAIM_SPACE_ID))
                || !String.valueOf(task.getDocumentId()).equals(
                jwt.getClaimAsString(JwtConstant.CLAIM_DOCUMENT_ID))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "A2A 回调令牌范围不匹配");
        }
    }

    /**
     * 从JWT中取出指定Claim并转换为Long类型
     *
     * @param jwt    jwt对象
     * @param claim  claim键名
     * @return 转换后的Long值
     * @throws BusinessException claim缺失或格式非法抛出FORBIDDEN
     */
    private Long longClaim(Jwt jwt, String claim) {
        Object value = jwt.getClaim(claim);
        if (value == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "A2A 回调令牌缺少任务范围");
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "A2A 回调令牌任务范围非法");
        }
    }

    /**
     * 判断任务状态是否为终态：完成 / 终止 / 失败，终态任务不再接受回调更新
     *
     * @param statusCode 任务状态编码
     * @return true=已终态，false=运行中可继续接收回调
     */
    private boolean isFinal(Integer statusCode) {
        TaskStatus status = TaskStatus.fromCode(statusCode);
        return status == TaskStatus.COMPLETED || status == TaskStatus.TERMINATED || status == TaskStatus.FAILED;
    }

}
