package com.agentdoc.agent.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("a2a_task_store")
@Schema(description = "A2A 任务持久化实体")
public class A2aTaskStoreEntity {

    @TableId
    @Schema(description = "任务 ID")
    private String taskId;
    @Schema(description = "A2A 上下文 ID")
    private String contextId;
    @Schema(description = "任务状态")
    private String state;
    @Schema(description = "任务状态时间戳")
    private LocalDateTime statusTimestamp;
    @Schema(description = "加密后的任务载荷")
    private String encryptedPayload;
    @Schema(description = "最后更新时间")
    private LocalDateTime updatedAt;
}
