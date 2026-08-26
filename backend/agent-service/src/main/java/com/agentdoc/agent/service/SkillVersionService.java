package com.agentdoc.agent.service;

import com.agentdoc.agent.convertor.SkillVersionConvertor;
import com.agentdoc.agent.enums.SkillStatus;
import com.agentdoc.agent.enums.SkillVersionStatus;
import com.agentdoc.agent.mapper.SkillVersionMapper;
import com.agentdoc.agent.pojo.entity.SkillEntity;
import com.agentdoc.agent.pojo.entity.SkillVersionEntity;
import com.agentdoc.agent.pojo.vo.SkillVersionVO;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.AuthUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Skill版本服务
 * <p>
 * 负责Skill版本ZIP包上传、校验、对象存储、草稿管理、版本发布、下载；
 * 只操作 {@link SkillVersionEntity}；Skill元数据校验委托 {@link SkillService}。
 * 业务流程：
 * <ol>
 *     <li>上传ZIP：校验包合法性 → 预分配版本号 → 写入对象存储 → 创建DRAFT草稿版本记录</li>
 *     <li>发布：DRAFT状态版本变更为PUBLISHED；已发布版本只读，不可原地修改</li>
 *     <li>下载：根据versionId读取对象存储ZIP流返回</li>
 * </ol>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillVersionService {

    private final SkillService skillService;
    private final SkillVersionMapper versionMapper;
    private final SkillPackageValidator validator;
    private final SkillPackageStorage storage;
    private final SkillAuditLogService auditLogService;

    /**
     * 上传Skill ZIP技能包，生成草稿版本(DRAFT)
     *
     * @param skillId 所属技能ID
     * @param multipartFile 上传的zip压缩包
     * @return 草稿版本VO
     */
    public SkillVersionVO upload(Long skillId, MultipartFile multipartFile) {
        SkillEntity skill = skillService.require(skillId);
        // 校验空间所有者权限
        skillService.requireOwner(skill.getSpaceId());

        // Skill必须处于启用状态才能上传新版本
        if (!SkillStatus.ACTIVE.matches(skill.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Skill 已停用");
        }
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "ZIP 文件不能为空");
        }
        Path tempDir = null;
        String storageKey = null;
        Integer reservedVersionNo = null;
        try {
            // 创建本地临时目录接收上传文件
            tempDir = Files.createTempDirectory("agent-doc-skill-");
            Path zip = tempDir.resolve("upload.zip");
            multipartFile.transferTo(zip);

            // 校验zip包结构、manifest、资源，返回解析后的包元信息
            ParsedSkillPackage parsed = validator.validate(zip);
            // 校验包内name与Skill主表name必须保持一致
            if (!skill.getName().equals(parsed.name())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Skill 包名称与元数据不一致");
            }

            // 行锁预分配版本号，版本号消耗后不回滚，失败会产生空洞
            int versionNo = skillService.reserveVersionNo(skillId);
            reservedVersionNo = versionNo;

            // 生成对象存储key并上传zip包
            storageKey = storage.key(skill.getSpaceId(), skillId, versionNo, parsed.sha256());
            storage.put(storageKey, zip);

            // 组装版本实体，状态为草稿DRAFT
            SkillVersionEntity entity = new SkillVersionEntity();
            entity.setSkillId(skillId);
            entity.setVersionNo(versionNo);
            entity.setStatus(SkillVersionStatus.DRAFT.getCode());
            entity.setStorageKey(storageKey);
            entity.setSha256(parsed.sha256());
            entity.setPackageSize(parsed.packageSize());
            entity.setUncompressedSize(parsed.uncompressedSize());
            entity.setFileCount(parsed.fileCount());
            entity.setReadableResourceCount((int) parsed.entries().stream()
                    .filter(SkillPackageEntry::runtimeReadable).count());
            entity.setReadableResourceSize(parsed.readableResourcesSize());
            entity.setInstructionText(parsed.instructionText());
            entity.setManifestJson(SkillVersionConvertor.toJson(parsed.entries()));
            entity.setAllowedToolsJson(SkillVersionConvertor.toJson(parsed.allowedTools()));
            entity.setCreatedBy(AuthUtils.getUserIdOrException());
            versionMapper.insert(entity);

            // 记录上传成功审计日志
            auditLogService.record(skill.getSpaceId(), "SKILL_VERSION_UPLOADED", "skill_version", entity.getId(),
                    Map.of("skillId", skillId, "versionNo", versionNo, "sha256", parsed.sha256()));
            return toVO(entity);
        } catch (SkillPackageValidationException exception) {
            // ZIP包校验失败：记录版本号预留失败审计，抛出业务异常
            recordReservationFailure(skill, reservedVersionNo, "validation", exception);
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, exception.getMessage());
        } catch (IOException exception) {
            // 文件IO异常：记录版本号预留失败审计，抛出业务异常
            recordReservationFailure(skill, reservedVersionNo, "io", exception);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Skill ZIP 文件读取失败");
        } catch (RuntimeException exception) {
            // 运行时异常：记录预留失败；尝试清理已写入对象存储；原始异常优先抛出
            recordReservationFailure(skill, reservedVersionNo, "runtime", exception);
            if (storageKey != null) {
                try {
                    storage.delete(storageKey);
                } catch (RuntimeException cleanupException) {
                    // 原始异常优先返回，孤儿对象由补偿任务处理。
                    log.warn("Skill 上传失败后的对象清理失败: storageKey={}",
                            storageKey, cleanupException);
                }
            }
            throw exception;
        } finally {
            // 运行时异常：记录预留失败；尝试清理已写入对象存储；原始异常优先抛出
            if (tempDir != null) {
                deleteRecursively(tempDir);
            }
        }
    }

    /**
     * 记录版本号预分配后上传失败事件审计
     * <p>reserveVersionNo已提交事务，版本号已消耗无法回滚，仅留审计用于事后监控版本空洞</p>
     *
     * @param skill 所属skill实体
     * @param versionNo 已预占用的版本号，null则不记录
     * @param phase 失败阶段：validation/io/runtime
     * @param exception 原始异常
     */
    private void recordReservationFailure(SkillEntity skill, Integer versionNo, String phase,
                                          Exception exception) {
        if (versionNo == null) {
            return;
        }
        try {
            // 记录上传失败审计日志
            auditLogService.record(skill.getSpaceId(), "SKILL_VERSION_RESERVATION_FAILED", "skill", skill.getId(),
                    Map.of("versionNo", versionNo, "phase", phase,
                            "errorType", exception.getClass().getSimpleName()));
        } catch (RuntimeException auditException) {
            log.warn("记录 Skill 版本号预留失败日志失败: skillId={}, versionNo={}",
                    skill.getId(), versionNo, auditException);
        }
        log.warn("Skill 上传失败后版本号已消耗: skillId={}, versionNo={}, phase={}",
                skill.getId(), versionNo, phase, exception);
    }

    /**
     * 查询指定Skill的全部版本列表，按版本号倒序
     *
     * @param skillId 技能ID
     * @return 版本VO列表
     */
    public List<SkillVersionVO> list(Long skillId) {
        SkillEntity skill = skillService.require(skillId);
        // 校验空间查看权限
        skillService.requireViewer(skill.getSpaceId());
        return versionMapper.selectList(new LambdaQueryWrapper<SkillVersionEntity>()
                        .eq(SkillVersionEntity::getSkillId, skillId)
                        .orderByDesc(SkillVersionEntity::getVersionNo))
                .stream().map(this::toVO).toList();
    }

    /**
     * 查询版本详情实体，做越权校验：versionId必须属于该skillId
     *
     * @param skillId 技能ID
     * @param versionId 版本ID
     * @return SkillVersionEntity
     */
    public SkillVersionEntity detail(Long skillId, Long versionId) {
        SkillEntity skill = skillService.require(skillId);
        // 校验空间查看权限
        skillService.requireViewer(skill.getSpaceId());
        SkillVersionEntity entity = versionMapper.selectById(versionId);
        if (entity == null || !skillId.equals(entity.getSkillId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Skill 版本不存在");
        }
        return entity;
    }

    /**
     * Controller层对外接口：获取版本VO
     *
     * @param skillId 技能ID
     * @param versionId 版本ID
     * @return SkillVersionVO
     */
    public SkillVersionVO toVOForController(Long skillId, Long versionId) {
        return toVO(detail(skillId, versionId));
    }

    /**
     * 下载Skill版本ZIP包，返回对象存储输入流
     *
     * @param skillId 技能ID
     * @param versionId 版本ID
     * @return ZIP输入流
     */
    public InputStream download(Long skillId, Long versionId) {
        SkillVersionEntity version = detail(skillId, versionId);
        return storage.get(version.getStorageKey());
    }

    /**
     * 发布正式版本：DRAFT(草稿) → PUBLISHED(正式)
     * <p>乐观锁条件更新，仅草稿允许发布；发布后版本只读不可修改</p>
     *
     * @param skillId 技能ID
     * @param versionId 版本ID
     * @return 发布后版本VO
     */
    @Transactional(rollbackFor = Exception.class)
    public SkillVersionVO publish(Long skillId, Long versionId) {
        // 查询并校验Skill存在
        SkillEntity skill = skillService.require(skillId);
        // 校验空间所有者权限
        skillService.requireOwner(skill.getSpaceId());

        // 校验Skill信息
        if (!SkillStatus.ACTIVE.matches(skill.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Skill 已停用");
        }
        // 校验SkillVersion信息
        SkillVersionEntity entity = versionMapper.selectById(versionId);
        if (entity == null || !skillId.equals(entity.getSkillId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Skill 版本不存在");
        }

        // 只允许草稿状态执行发布
        if (!SkillVersionStatus.DRAFT.matches(entity.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Skill 版本已经发布或状态非法");
        }

        // 校验对象存储资源真实存在
        if (!storage.exists(entity.getStorageKey())) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Skill 包对象不存在");
        }

        // 更新状态为PUBLISHED
        entity.setStatus(SkillVersionStatus.PUBLISHED.getCode());
        entity.setPublishedBy(AuthUtils.getUserIdOrException());
        entity.setPublishedAt(LocalDateTime.now());
        // 执行更新
        int updated = versionMapper.update(entity, new LambdaUpdateWrapper<SkillVersionEntity>()
                .eq(SkillVersionEntity::getId, versionId)
                .eq(SkillVersionEntity::getSkillId, skillId)
                .eq(SkillVersionEntity::getStatus, SkillVersionStatus.DRAFT.getCode()));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "Skill 版本状态冲突，仅草稿可发布");
        }
        // 记录SkillVersion变更日志
        auditLogService.record(skill.getSpaceId(), "SKILL_VERSION_PUBLISHED", "skill_version", entity.getId(),
                Map.of("versionNo", entity.getVersionNo(), "sha256", entity.getSha256()));
        return toVO(entity);
    }

    /**
     * 实体转VO
     */
    private SkillVersionVO toVO(SkillVersionEntity entity) {
        return SkillVersionConvertor.toVO(entity);
    }

    /**
     * 递归删除临时目录及其内部所有文件；删除失败仅打warn，不阻断主流程
     *
     * @param path 临时目录路径
     */
    private void deleteRecursively(Path path) {
        try (var stream = Files.walk(path)) {
            // 反向排序，先删子文件再删目录
            stream.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException | RuntimeException exception) {
                    log.warn("Skill 上传临时路径删除失败: {}", item, exception);
                }
            });
        } catch (IOException | RuntimeException exception) {
            log.warn("Skill 上传临时目录清理失败: {}", path, exception);
        }
    }
}
