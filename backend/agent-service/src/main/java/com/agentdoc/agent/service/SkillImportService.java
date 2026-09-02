package com.agentdoc.agent.service;

import com.agentdoc.agent.pojo.dto.SkillCreateDTO;
import com.agentdoc.agent.pojo.entity.SkillEntity;
import com.agentdoc.agent.pojo.vo.SkillImportVO;
import com.agentdoc.agent.pojo.vo.SkillVersionVO;
import com.agentdoc.agent.skill.archive.ParsedSkillPackage;
import com.agentdoc.agent.skill.archive.SkillPackageValidationException;
import com.agentdoc.agent.skill.archive.SkillPackageValidator;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

import static com.agentdoc.agent.constant.SkillConstant.MAX_DESCRIPTION_LENGTH;
import static com.agentdoc.agent.constant.SkillConstant.MAX_DISPLAY_NAME_LENGTH;

/**
 * Skill ZIP 一键导入服务。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillImportService {

    private final SkillService skillService;
    private final SkillVersionService versionService;
    private final SkillPackageValidator validator;

    /**
     * 解析 ZIP 中的 SKILL.md，自动创建 Skill 和首个草稿版本。
     *
     * @param spaceId 空间ID
     * @param displayName 展示名称，为空时使用包内技术标识
     * @param description 管理描述，为空时使用包内激活描述
     * @param file Skill ZIP包
     * @return Skill与首个草稿版本
     */
    @Transactional(rollbackFor = Exception.class)
    public SkillImportVO importPackage(Long spaceId, String displayName, String description, MultipartFile file) {
        if (spaceId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "spaceId 不能为空");
        }
        skillService.requireManage(spaceId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "ZIP 文件不能为空");
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("agent-doc-skill-import-");
            Path zip = tempDir.resolve("import.zip");
            try (var input = file.getInputStream()) {
                Files.copy(input, zip, StandardCopyOption.REPLACE_EXISTING);
            }
            ParsedSkillPackage parsed = validator.validate(zip);
            SkillEntity skill = skillService.create(new SkillCreateDTO(
                    spaceId,
                    parsed.name(),
                    valueOrDefault(displayName, parsed.name(), MAX_DISPLAY_NAME_LENGTH, "展示名称"),
                    valueOrDefault(description, parsed.description(), MAX_DESCRIPTION_LENGTH, "管理描述")));
            SkillVersionVO version = versionService.upload(skill.getId(), file);
            return new SkillImportVO(skillService.toVO(skill), version);
        } catch (SkillPackageValidationException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, exception.getMessage());
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Skill ZIP 文件读取失败");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private String valueOrDefault(String value, String fallback, int maxLength, String fieldName) {
        String resolved = StringUtils.hasText(value) ? value.trim() : fallback;
        if (resolved.length() > maxLength) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return resolved;
    }

    private void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    log.warn("清理 Skill 导入临时文件失败: path={}", path, exception);
                }
            });
        } catch (IOException exception) {
            log.warn("遍历 Skill 导入临时目录失败: path={}", root, exception);
        }
    }
}
