package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_execution")
@Schema(description = "Agent 执行记录实体")
public class AgentExecutionEntity extends BaseEntity {

    @Schema(description = "A2A 任务 ID")
    private String a2aTaskId;
    @Schema(description = "A2A 上下文 ID")
    private String a2aContextId;
    @Schema(description = "工作台任务 ID")
    private Long workbenchTaskId;
    @Schema(description = "Agent ID")
    private Long agentId;
    @Schema(description = "Agent 配置版本号")
    private Long agentConfigVersion;
    @Schema(description = "系统提示词快照")
    private String systemPromptSnapshot;
    @Schema(description = "初始用户指令快照")
    private String userInstructionSnapshot;
    @Schema(description = "模型配置快照")
    private String modelSnapshot;
    @Schema(description = "Skill 版本快照 JSON")
    private String skillSnapshotJson;
    @Schema(description = "Skill 指令哈希")
    private String skillInstructionHash;
    @Schema(description = "Agent 配置的 Skill 选择模式")
    private String skillSelectionMode;
    @Schema(description = "本次实际 Skill 选择模式")
    private String skillSelectionEffectiveMode;
    @Schema(description = "本次实际 Skill Router 模型 ID")
    private Long skillRouterModelId;
    @Schema(description = "本次选中的 SkillVersion ID JSON")
    private String selectedSkillVersionIdsJson;
    @Schema(description = "Skill Router 脱敏快照 JSON")
    private String skillRouterSnapshotJson;
    @Schema(description = "执行时工具白名单快照 JSON")
    private String toolWhitelistSnapshot;
    @Schema(description = "实际暴露给模型的工具定义快照 JSON")
    private String toolDefinitionSnapshotJson;
    @Schema(description = "外部 MCP 脱敏配置与绑定快照 JSON")
    private String externalMcpSnapshotJson;
    @Schema(description = "提示词哈希")
    private String promptHash;
    @Schema(description = "执行状态")
    private String status;
    @Schema(description = "是否请求取消")
    private Boolean cancelRequested;
    @Schema(description = "输入 Token 数")
    private Long inputTokens;
    @Schema(description = "输入 Token 是否为估算值")
    private Boolean inputTokensEstimated;
    @Schema(description = "缓存输入 Token 数")
    private Long cachedInputTokens;
    @Schema(description = "缓存输入 Token 是否为估算值")
    private Boolean cachedInputTokensEstimated;
    @Schema(description = "输出 Token 数")
    private Long outputTokens;
    @Schema(description = "输出 Token 是否为估算值")
    private Boolean outputTokensEstimated;
    @Schema(description = "执行结果摘要")
    private String resultSummary;
    @Schema(description = "错误信息")
    private String errorMessage;
    @Schema(description = "开始时间")
    private LocalDateTime startedAt;
    @Schema(description = "结束时间")
    private LocalDateTime finishedAt;
}
