package com.agentdoc.task.pojo.vo;

import com.agentdoc.task.pojo.entity.AuditLogEntity;
import com.agentdoc.task.enums.AuditAction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 空间审计日志脱敏展示投影。
 */
@Schema(description = "空间审计日志")
public record AuditLogVO(
        Long id,
        Long spaceId,
        Long taskId,
        Integer actorType,
        Long actorId,
        String actorName,
        String action,
        String actionName,
        String targetType,
        String targetTypeName,
        Long targetId,
        String detail,
        String traceId,
        LocalDateTime createdAt) {

    public static AuditLogVO from(AuditLogEntity entity, String actorName) {
        String action = entity.getAction();
        String targetType = entity.getTargetType();
        return new AuditLogVO(entity.getId(), entity.getSpaceId(), entity.getTaskId(),
                entity.getActorType(), entity.getActorId(), actorName, action, actionName(action), targetType,
                targetTypeName(targetType), entity.getTargetId(), entity.getDetail(), entity.getTraceId(),
                entity.getCreatedAt());
    }

    private static String actionName(String code) {
        try {
            return switch (AuditAction.valueOf(code)) {
                case AGENT_CREATED -> "创建 Agent";
                case AGENT_UPDATED -> "更新 Agent";
                case AGENT_DELETED -> "删除 Agent";
                case TASK_CREATED -> "创建任务";
                case TASK_STARTED -> "启动任务";
                case TASK_COMPLETED -> "任务完成";
                case TASK_TERMINATED -> "终止任务";
                case TASK_BUDGET_TERMINATED -> "任务预算终止";
                case TASK_RETRY -> "重试任务";
                case TASK_FAILED -> "任务失败";
                case CHANGE_REQUEST_SUBMITTED -> "提交变更请求";
                case CHANGE_REQUEST_CLAIMED -> "领取变更请求";
                case CHANGE_REQUEST_UNCLAIMED -> "取消领取变更请求";
                case CHANGE_REQUEST_APPROVED -> "批准变更请求";
                case CHANGE_REQUEST_REJECTED -> "拒绝变更请求";
                case CHANGE_REQUEST_RETURNED -> "退回变更请求";
                case CHANGE_REQUEST_REWORK_CREATED -> "创建变更重改任务";
                case CHANGE_REQUEST_MERGED -> "合并变更请求";
                case CHANGE_REQUEST_COMMENTED -> "评论变更请求";
                case DOCUMENT_VERSION_ROLLED_BACK -> "回滚文档版本";
                case SPACE_ROLE_CREATED -> "创建空间角色";
                case SPACE_ROLE_UPDATED -> "修改空间角色";
                case SPACE_ROLE_PERMISSIONS_REPLACED -> "调整角色权限";
                case SPACE_ROLE_DELETED -> "删除空间角色";
            };
        } catch (RuntimeException exception) {
            return code;
        }
    }

    private static String targetTypeName(String code) {
        return switch (code) {
            case "agent" -> "Agent";
            case "task" -> "任务";
            case "change_request" -> "变更请求";
            case "document_version" -> "文档版本";
            case "space_role" -> "空间角色";
            default -> code;
        };
    }
}
