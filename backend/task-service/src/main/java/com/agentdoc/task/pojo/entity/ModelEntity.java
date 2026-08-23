package com.agentdoc.task.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 模型元数据实体（仅元数据，不存密钥；model_key 为透传给 MCP 服务的模型调用 key）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("model")
@Schema(description = "模型元数据实体")
public class ModelEntity extends BaseLogicDeleteEntity {

    @Schema(description = "厂商名称：openai / dashscope / ollama / anthropic")
    private String provider;

    @Schema(description = "传给 MCP 服务的模型真实调用 key")
    private String modelKey;

    @Schema(description = "前端展示友好名称")
    private String displayName;

    @Schema(description = "模型官网链接")
    private String officialUrl;

    @Schema(description = "上下文窗口大小")
    private Long contextWindow;

    @Schema(description = "最大输出 token")
    private Long maxOutputTokens;

    @Schema(description = "输入单价，元/百万 token，仅预估")
    private BigDecimal inputPricePerMillion;

    @Schema(description = "输出单价，元/百万 token，仅预估")
    private BigDecimal outputPricePerMillion;

    @Schema(description = "状态：1 启用 / 0 禁用")
    private Integer status;

    @Schema(description = "备注说明")
    private String description;
}
