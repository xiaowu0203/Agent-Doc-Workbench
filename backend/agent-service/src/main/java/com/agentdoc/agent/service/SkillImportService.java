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
 * Skill包导入服务
 * 接收ZIP格式技能包，校验包内SKILL.md，完成Skill主体创建并生成首个草稿版本
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillImportService {

    private final SkillService skillService;
    private final SkillVersionService versionService;
    private final SkillPackageValidator validator;

    /**
     * 导入Skill ZIP安装包
     * 流程：校验ZIP包 → 解析SKILL.md → 创建Skill主记录 → 上传生成首个草稿版本
     * 整个流程事务包裹，异常全部回滚；导入完成自动清理临时文件
     *
     * @param spaceId 目标空间ID
     * @param displayName 前端传入展示名称；为空则使用包内技术名称
     * @param description 前端传入管理描述；为空则使用包内激活描述
     * @param file 技能ZIP压缩包文件
     * @return 导入结果：Skill基础信息 + 首个草稿版本VO
     */
    @Transactional(rollbackFor = Exception.class)
    public SkillImportVO importPackage(Long spaceId, String displayName, String description, MultipartFile file) {
        // 校验空间ID不能为空
        if (spaceId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "spaceId 不能为空");
        }
        // 校验用户拥有该空间Skill管理权限
        skillService.requireManage(spaceId);

        // 校验上传文件不为空
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "ZIP 文件不能为空");
        }

        Path tempDir = null;
        try {
            // 创建临时目录用于解压处理zip包
            tempDir = Files.createTempDirectory("agent-doc-skill-import-");
            Path zip = tempDir.resolve("import.zip");

            // 将上传的MultipartFile写入临时zip文件
            try (var input = file.getInputStream()) {
                Files.copy(input, zip, StandardCopyOption.REPLACE_EXISTING);
            }

            // 校验ZIP包合法性，解析SKILL.md元信息
            ParsedSkillPackage parsed = validator.validate(zip);

            // 创建Skill实体，字段做兜底：前端传参为空则使用包内解析出来的值
            SkillEntity skill = skillService.create(new SkillCreateDTO(
                    spaceId,
                    parsed.name(),
                    valueOrDefault(displayName, parsed.name(), MAX_DISPLAY_NAME_LENGTH, "展示名称"),
                    valueOrDefault(description, parsed.description(), MAX_DESCRIPTION_LENGTH, "管理描述")));

            // 上传zip包，生成Skill的第一个草稿版本
            SkillVersionVO version = versionService.upload(skill.getId(), file);
            return new SkillImportVO(skillService.toVO(skill), version);
        } catch (SkillPackageValidationException exception) {
            // 技能包校验异常，转换为业务异常抛出
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, exception.getMessage());
        } catch (IOException exception) {
            // 文件IO异常
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Skill ZIP 文件读取失败");
        } finally {
            // 无论成功失败，都递归删除临时目录
            deleteRecursively(tempDir);
        }
    }

    /**
     * 参数兜底处理工具方法
     * 优先使用传入value，为空则使用fallback；同时校验字符长度上限，超出抛出校验异常
     *
     * @param value 前端传入的值
     * @param fallback 兜底默认值（来自包解析）
     * @param maxLength 最大允许字符长度
     * @param fieldName 字段名称，用于错误提示
     * @return 处理后的字符串
     */
    private String valueOrDefault(String value, String fallback, int maxLength, String fieldName) {
        String resolved = StringUtils.hasText(value) ? value.trim() : fallback;
        if (resolved.length() > maxLength) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return resolved;
    }

    /**
     * 递归删除临时目录及其全部子文件
     * 删除失败只打warn日志，不阻断主流程
     *
     * @param root 待删除的目录路径
     */
    private void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            // 逆序遍历：先删文件，后删文件夹
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
