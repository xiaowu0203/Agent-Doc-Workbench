package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseLogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("model")
@Schema(description = "模型配置实体")
public class ModelEntity extends BaseLogicDeleteEntity {

    @Schema(description = "模型提供商")
    private String provider;
    @Schema(description = "模型适配器类型")
    private String adapterType;
    @Schema(description = "模型标识")
    private String modelKey;
    @Schema(description = "模型展示名称")
    private String displayName;
    @Schema(description = "官方文档地址")
    private String officialUrl;
    @Schema(description = "模型服务基础地址")
    private String baseUrl;
    @Schema(description = "加密后的模型 API Key")
    @ToString.Exclude
    private String encryptedApiKey;
    @Schema(description = "适配器扩展配置 JSON")
    private String optionsJson;
    @Schema(description = "模型配置版本，每次影响模型调用配置的修改递增")
    private Long configVersion;
    @Schema(description = "上下文窗口大小")
    private Long contextWindow;
    @Schema(description = "最大输出 Token 数")
    private Long maxOutputTokens;
    @Schema(description = "输入价格（每百万 Token）")
    private BigDecimal inputPricePerMillion;
    @Schema(description = "输出价格（每百万 Token）")
    private BigDecimal outputPricePerMillion;
    @Schema(description = "状态：0 禁用 / 1 启用")
    private Integer status;
    @Schema(description = "模型描述")
    private String description;
}
