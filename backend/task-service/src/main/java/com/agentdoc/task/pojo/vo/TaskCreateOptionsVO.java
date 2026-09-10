package com.agentdoc.task.pojo.vo;

import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.feign.vo.AgentTaskOptionVO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 新建任务页在选定文档后所需的服务端裁决信息。
 */
@Schema(description = "任务创建选项")
public record TaskCreateOptionsVO(
        @Schema(description = "所属空间 ID") Long spaceId,
        @Schema(description = "目标文档 ID") Long documentId,
        @Schema(description = "文档类型") DocType documentType,
        @Schema(description = "文档当前版本") Long documentVersion,
        @Schema(description = "文档字符数") Long documentLength,
        @Schema(description = "空间 Token 预算上限") Long spaceTokenBudget,
        @Schema(description = "可用于该文档的已启用 Agent") List<AgentTaskOptionVO> agents) {
}
