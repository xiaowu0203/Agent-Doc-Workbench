package com.agentdoc.task.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_creation_intent")
@Schema(description = "Task创建幂等意图，输入冻结后不可重解析")
public class TaskCreationIntentEntity extends BaseEntity {
    @Schema(description = "唯一预分配Task身份") private Long taskId;
    @Schema(description = "空间") private Long spaceId;
    @Schema(description = "实际登录发起者") private Long actorId;
    @Schema(description = "操作者范围内的幂等键") private String requestKey;
    @Schema(description = "规范原请求摘要") private String requestHash;
    @Schema(description = "首次解析有效预算；无有限预算为空") private Long effectiveBudget;
    @Schema(description = "不含令牌/正文的冻结Task输入") private String inputJson;
    @Schema(description = "线上绑定；后续签名准入接入前为空") private String bindingJson;
    @Schema(description = "创建补偿状态") private String status;
    @Schema(description = "最近补偿失败的稳定原因，不保存外部异常消息") private String reasonCode;
    @Schema(description = "更新时间") private LocalDateTime updatedAt;
}
