package com.agentdoc.evaluation.pojo.entity;
import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@Data
@EqualsAndHashCode(callSuper=true)
@TableName("online_execution_slot")
public class OnlineExecutionSlotEntity extends BaseEntity {
    @Schema(description="实验") private Long experimentId;
    @Schema(description="组") private String variant;
    @Schema(description="固定槽号") private Integer slotNo;
    @Schema(description="槽代次") private Long generation;
    @Schema(description="占用分配") private Long assignmentId;
    @Schema(description="占用任务") private Long taskId;
    @Schema(description="占用绑定") private String bindingHash;
    @Schema(description="取得时间") private LocalDateTime acquiredAt;
    @Schema(description="开始线性化时间") private LocalDateTime startedAt;
    @Schema(description="更新时间") private LocalDateTime updatedAt;
}
