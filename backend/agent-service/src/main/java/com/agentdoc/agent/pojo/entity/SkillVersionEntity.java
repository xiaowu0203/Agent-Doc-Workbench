package com.agentdoc.agent.pojo.entity;

import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("skill_version")
@Schema(description = "Skill 不可变版本")
public class SkillVersionEntity extends BaseEntity {

    @Schema(description = "Skill ID")
    private Long skillId;
    @Schema(description = "版本号")
    private Integer versionNo;
    @Schema(description = "状态：0 草稿 / 1 已发布")
    private Integer status;
    @Schema(description = "对象存储键")
    private String storageKey;
    @Schema(description = "ZIP SHA-256")
    private String sha256;
    @Schema(description = "ZIP 大小")
    private Long packageSize;
    @Schema(description = "解压后总大小")
    private Long uncompressedSize;
    @Schema(description = "文件数量")
    private Integer fileCount;
    @Schema(description = "可读资源数量")
    private Integer readableResourceCount;
    @Schema(description = "可读资源大小")
    private Long readableResourceSize;
    @Schema(description = "Skill 指令正文")
    private String instructionText;
    @Schema(description = "文件清单 JSON")
    private String manifestJson;
    @Schema(description = "允许工具 JSON 数组")
    private String allowedToolsJson;
    @Schema(description = "上传人用户 ID")
    private Long createdBy;
    @Schema(description = "发布人用户 ID")
    private Long publishedBy;
    @Schema(description = "发布时间")
    private LocalDateTime publishedAt;
}
