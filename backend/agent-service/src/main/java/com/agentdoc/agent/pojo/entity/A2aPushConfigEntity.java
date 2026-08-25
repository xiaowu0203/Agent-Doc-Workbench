package com.agentdoc.agent.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("a2a_push_config")
@Schema(description = "A2A 推送配置持久化实体")
public class A2aPushConfigEntity {

    @TableId
    @Schema(description = "推送配置 ID")
    private String configId;
    @Schema(description = "关联任务 ID")
    private String taskId;
    @Schema(description = "A2A 协议版本")
    private String protocolVersion;
    @Schema(description = "加密后的推送配置载荷")
    private String encryptedPayload;
    @Schema(description = "最后更新时间")
    private LocalDateTime updatedAt;
}
