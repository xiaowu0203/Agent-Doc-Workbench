package com.agentdoc.task.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Token 当日统计快照实体（今日消耗卡片展示，不做业务熔断）。
 * 流水表：无 deleted / updated_at，继承 {@link BaseEntity}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("token_daily_snapshot")
@Schema(description = "Token 当日快照实体")
public class TokenDailySnapshotEntity extends BaseEntity {

    @Schema(description = "空间 ID")
    private Long spaceId;

    @Schema(description = "快照对应的业务日期")
    private LocalDate snapshotDate;

    @Schema(description = "快照时刻总输入 token")
    private Long totalInput;

    @Schema(description = "快照时刻总输出 token")
    private Long totalOutput;

    @Schema(description = "快照时刻预估总费用")
    private BigDecimal totalEstimatedCost;

    @Schema(description = "快照类型：1 系统自动 / 2 用户手动触发")
    private Integer snapshotType;
}
