package com.agentdoc.evaluation.pojo.vo;
import io.swagger.v3.oas.annotations.media.Schema;

/** 一次批量聚合，不逐实验查 Task。 */
public record OnlineParticipationVO(
        @Schema(description = "实验身份") Long experimentId,
        @Schema(description = "独立参与文档数") Long documentCount) { }
