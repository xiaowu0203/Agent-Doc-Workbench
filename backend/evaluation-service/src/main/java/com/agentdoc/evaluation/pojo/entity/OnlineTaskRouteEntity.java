package com.agentdoc.evaluation.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("online_task_route")
public class OnlineTaskRouteEntity extends BaseEntity {
    @Schema(description = "空间") private Long spaceId;
    @Schema(description = "实际 Task 发起者") private Long actorId;
    @Schema(description = "Task 请求键") private String requestKey;
    @Schema(description = "Task 原请求摘要") private String requestHash;
    @Schema(description = "不可变路由 envelope；预留时为空") private String routeJson;
}
