package com.agentdoc.agent.service;

import com.agentdoc.agent.convertor.SkillVersionConvertor;
import com.agentdoc.agent.enums.SkillStatus;
import com.agentdoc.agent.enums.SkillVersionStatus;
import com.agentdoc.agent.mapper.SkillVersionMapper;
import com.agentdoc.agent.pojo.entity.SkillEntity;
import com.agentdoc.agent.pojo.entity.SkillVersionEntity;
import com.agentdoc.agent.pojo.vo.SkillVersionVO;
import com.agentdoc.agent.skill.archive.ParsedSkillPackage;
import com.agentdoc.agent.skill.archive.SkillPackageEntry;
import com.agentdoc.agent.skill.archive.SkillPackageValidationException;
import com.agentdoc.agent.skill.archive.SkillPackageValidator;
import com.agentdoc.agent.skill.storage.SkillPackageStorage;
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
     * <p>普通空间技能上传入口，会校验空间管理权限</p>
     *
     * @param skillId 所属技能ID
     * @param multipartFile 上传的zip压缩包
     * @return 草稿版本VO
     */
    public SkillVersionVO upload(Long skillId, MultipartFile multipartFile) {
        // 校验Skill存在 + 获取空间信息
        SkillEntity skill = skillService.requireSpace(skillId);
        // 校验当前用户拥有该空间的Skill管理权限
        skillService.requireManage(skill.getSpaceId());
        return upload(skill, multipartFile);
    }

    /**
     * 平台超级管理员为系统 Skill 上传草稿版本。
     * <p>系统级Skill不属于租户空间，单独权限校验</p>
     *
     * @param skillId 系统技能ID
     * @param multipartFile 上传zip包
     * @return 草稿版本VO
     */
    public SkillVersionVO uploadSystem(Long skillId, MultipartFile multipartFile) {
        // 获取系统Skill信息，不存在抛异常
        SkillEntity skill = skillService.requireSystem(skillId);
        // 校验平台超级管理员角色
        skillService.requireSystemManage();
        return upload(skill, multipartFile);
    }

    /**
     * 【内部私有上传主逻辑】
     * 统一处理ZIP临时落地、包校验、版本号预分配、对象存储写入、数据库草稿记录写入
     * <p>重要特性：版本号预分配一旦执行，就算后续流程失败，版本号也不会回滚，会产生版本空洞，靠审计日志监控</p>
     *
     * @param skill 技能主表实体（区分普通空间Skill / 系统Skill）
     * @param multipartFile 前端上传文件
     * @return 草稿版本VO
     */
    private SkillVersionVO upload(SkillEntity skill, MultipartFile multipartFile) {
        Long skillId = skill.getId();

        // Skill必须处于启用状态才能上传新版本
        if (!SkillStatus.ACTIVE.matches(skill.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Skill 已停用");
        }
        // 校验上传文件非空
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "ZIP 文件不能为空");
        }
        Path tempDir = null;
        String storageKey = null;
        Integer reservedVersionNo = null;
        try {
            // 创建本地临时目录接收上传文件（JVM临时目录，finally统一清理）
            tempDir = Files.createTempDirectory("agent-doc-skill-");
            Path zip = tempDir.resolve("upload.zip");
            // 将MultipartFile写入本地临时zip文件
            multipartFile.transferTo(zip);

            // 校验zip包结构、manifest清单、资源文件，返回解析后的包元信息
            ParsedSkillPackage parsed = validator.validate(zip);
            // 强约束：zip包内manifest定义的skill名称，必须和数据库Skill主表名称一致，防止包窜项
            if (!skill.getName().equals(parsed.name())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Skill 包名称与元数据不一致");
            }

            // 行锁预分配版本号，版本号消耗后不回滚，失败会产生版本空洞
            int versionNo = skillService.reserveVersionNo(skillId);
            reservedVersionNo = versionNo;

            // 区分系统Skill / 租户空间Skill，生成不同的对象存储key
            storageKey = skill.getSpaceId() == null
                    ? storage.systemKey(skillId, versionNo, parsed.sha256())
                    : storage.key(skill.getSpaceId(), skillId, versionNo, parsed.sha256());
            // 将临时zip包上传到对象存储
            storage.put(storageKey, zip);

            // 组装版本数据库实体，初始状态为草稿DRAFT
            SkillVersionEntity entity = new SkillVersionEntity();
            entity.setSkillId(skillId);
            entity.setVersionNo(versionNo);
            entity.setStatus(SkillVersionStatus.DRAFT.getCode());
            entity.setActivationDescription(parsed.description());
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
            // ZIP包结构/清单校验失败：记录版本号预留失败审计，抛出业务异常
            recordReservationFailure(skill, reservedVersionNo, "validation", exception);
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, exception.getMessage());
        } catch (IOException exception) {
            // 文件IO异常（读写临时文件失败）：记录版本号预留失败审计，抛出业务异常
            recordReservationFailure(skill, reservedVersionNo, "io", exception);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Skill ZIP 文件读取失败");
        } catch (RuntimeException exception) {
            // 通用运行时异常：记录预留失败；尝试清理已经上传到对象存储的文件；原始异常优先抛出
            recordReservationFailure(skill, reservedVersionNo, "runtime", exception);
            if (storageKey != null) {
                try {
                    storage.delete(storageKey);
                } catch (RuntimeException cleanupException) {
                    // 清理对象存储失败仅告警，不覆盖原始异常；孤儿对象由后台补偿清理任务处理
                    log.warn("Skill 上传失败后的对象清理失败: storageKey={}",
                            storageKey, cleanupException);
                }
            }
            throw exception;
        } finally {
            // 无论成功失败，都递归删除本地临时目录，避免磁盘堆积
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
        // 版本号未分配成功，无需记录
        if (versionNo == null) {
            return;
        }
        try {
            // 记录上传失败审计日志
            auditLogService.record(skill.getSpaceId(), "SKILL_VERSION_RESERVATION_FAILED", "skill", skill.getId(),
                    Map.of("versionNo", versionNo, "phase", phase,
                            "errorType", exception.getClass().getSimpleName()));
        } catch (RuntimeException auditException) {
            // 审计日志记录本身失败，仅打警告，不影响主异常抛出
            log.warn("记录 Skill 版本号预留失败日志失败: skillId={}, versionNo={}",
                    skill.getId(), versionNo, auditException);
        }
        log.warn("Skill 上传失败后版本号已消耗: skillId={}, versionNo={}, phase={}",
                skill.getId(), versionNo, phase, exception);
    }

    /**
     * 查询指定Skill的全部版本列表，按版本号倒序
     * <p>普通租户空间技能：拥有读权限即可查看全部版本（草稿+已发布）</p>
     *
     * @param skillId 技能ID
     * @return 版本VO列表
     */
    public List<SkillVersionVO> list(Long skillId) {
        SkillEntity skill = skillService.requireSpace(skillId);
        // 校验空间查看权限
        skillService.requireRead(skill.getSpaceId());
        return listVersions(skillId);
    }

    /**
     * 查询系统 Skill 的版本列表。
     * <p>普通只读用户仅能看到已发布版本；系统管理员可查看全部草稿+正式版本</p>
     *
     * @param skillId 系统技能ID
     * @return 版本VO列表
     */
    public List<SkillVersionVO> listSystem(Long skillId) {
        SkillEntity skill = skillService.requireSystem(skillId);
        skillService.requireSystemRead(skill);
        // 平台系统管理员，可查询全部版本
        if (skillService.isSystemManager()) {
            return listVersions(skillId);
        }
        // 普通只读用户：仅返回PUBLISHED已发布版本
        return versionMapper.selectList(new LambdaQueryWrapper<SkillVersionEntity>()
                        .eq(SkillVersionEntity::getSkillId, skillId)
                        .eq(SkillVersionEntity::getStatus, SkillVersionStatus.PUBLISHED.getCode())
                        .orderByDesc(SkillVersionEntity::getVersionNo))
                .stream().map(this::toVO).toList();
    }

    /**
     * 【内部公共查询方法】查询指定skill所有版本，版本号倒序，不做权限校验，由上层控制权限
     *
     * @param skillId 技能ID
     * @return 全部版本VO
     */
    private List<SkillVersionVO> listVersions(Long skillId) {
        return versionMapper.selectList(new LambdaQueryWrapper<SkillVersionEntity>()
                        .eq(SkillVersionEntity::getSkillId, skillId)
                        .orderByDesc(SkillVersionEntity::getVersionNo))
                .stream().map(this::toVO).toList();
    }

    /**
     * 查询版本详情实体，做越权校验：versionId必须属于该skillId
     * <p>租户空间版本详情入口，校验空间读权限</p>
     *
     * @param skillId 技能ID
     * @param versionId 版本ID
     * @return SkillVersionEntity
     */
    public SkillVersionEntity detail(Long skillId, Long versionId) {
        SkillEntity skill = skillService.requireSpace(skillId);
        // 校验空间查看权限
        skillService.requireRead(skill.getSpaceId());
        return requireVersion(skillId, versionId);
    }

    /**
     * 查询系统 Skill 版本详情。
     * <p>普通只读用户不能查看草稿版本；系统管理员可以查看草稿与正式版本</p>
     *
     * @param skillId 系统技能ID
     * @param versionId 版本ID
     * @return SkillVersionEntity
     */
    public SkillVersionEntity detailSystem(Long skillId, Long versionId) {
        SkillEntity skill = skillService.requireSystem(skillId);
        skillService.requireSystemRead(skill);
        SkillVersionEntity version = requireVersion(skillId, versionId);
        // 非系统管理员，不能读取系统Skill草稿版本，直接返回不存在
        if (!SkillVersionStatus.PUBLISHED.matches(version.getStatus())
                && !skillService.isSystemManager()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "系统 Skill 版本不存在");
        }
        return version;
    }

    /**
     * 【内部校验方法】根据skillId+versionId查询版本并做归属校验
     * <p>校验versionId存在，并且该版本归属skillId，防止跨技能越权查询版本</p>
     *
     * @param skillId 技能ID
     * @param versionId 版本主键ID
     * @return 版本实体
     */
    private SkillVersionEntity requireVersion(Long skillId, Long versionId) {
        SkillVersionEntity entity = versionMapper.selectById(versionId);
        // 不存在 或者 版本不属于当前skill，统一抛NOT_FOUND，防止枚举id遍历探测
        if (entity == null || !skillId.equals(entity.getSkillId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Skill 版本不存在");
        }
        return entity;
    }

    /**
     * Controller层对外接口：获取版本VO
     * <p>租户空间版本详情对外入口，封装权限校验+实体转VO</p>
     *
     * @param skillId 技能ID
     * @param versionId 版本ID
     * @return SkillVersionVO
     */
    public SkillVersionVO toVOForController(Long skillId, Long versionId) {
        return toVO(detail(skillId, versionId));
    }

    /**
     * 系统 Skill 版本详情 VO。
     * <p>系统技能对外详情接口，封装权限校验+实体转VO</p>
     *
     * @param skillId 系统技能ID
     * @param versionId 版本ID
     * @return SkillVersionVO
     */
    public SkillVersionVO toSystemVOForController(Long skillId, Long versionId) {
        return toVO(detailSystem(skillId, versionId));
    }

    /**
     * 下载Skill版本ZIP包，返回对象存储输入流
     * <p>下载包含 Skill 完整实现，只允许具备 Skill 管理权限的成员执行。</p>
     *
     * @param skillId 技能ID
     * @param versionId 版本ID
     * @return ZIP输入流
     */
    public InputStream download(Long skillId, Long versionId) {
        SkillEntity skill = skillService.requireSpace(skillId);
        // 下载包是完整源码，要求管理权限，只读用户不能下载
        skillService.requireManage(skill.getSpaceId());
        SkillVersionEntity version = requireVersion(skillId, versionId);
        return storage.get(version.getStorageKey());
    }

    /**
     * 下载系统 Skill 版本包；仅平台超级管理员可读取完整实现。
     *
     * @param skillId 系统技能ID
     * @param versionId 版本ID
     * @return ZIP输入流
     */
    public InputStream downloadSystem(Long skillId, Long versionId) {
        skillService.requireSystem(skillId);
        skillService.requireSystemManage();
        SkillVersionEntity version = requireVersion(skillId, versionId);
        return storage.get(version.getStorageKey());
    }

    /**
     * 发布正式版本：DRAFT(草稿) → PUBLISHED(正式)
     * <p>乐观锁条件更新，仅草稿允许发布；发布后版本只读不可修改</p>
     * <p>事务：全部更新操作在同一个事务，异常回滚</p>
     *
     * @param skillId 技能ID
     * @param versionId 版本ID
     * @return 发布后版本VO
     */
    @Transactional(rollbackFor = Exception.class)
    public SkillVersionVO publish(Long skillId, Long versionId) {
        // 查询并校验Skill存在
        SkillEntity skill = skillService.requireSpace(skillId);
        // 校验空间所有者/管理员权限
        skillService.requireManage(skill.getSpaceId());
        return publish(skill, versionId);
    }

    /**
     * 发布系统 Skill 版本。
     * <p>仅平台超级管理员可执行系统技能版本发布</p>
     *
     * @param versionId 版本主键ID
     * @return 发布后版本VO
     */
    @Transactional(rollbackFor = Exception.class)
    public SkillVersionVO publishSystem(Long versionId) {
        SkillVersionEntity version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Skill 版本不存在");
        }
        SkillEntity skill = skillService.requireSystem(version.getSkillId());
        skillService.requireSystemManage();
        return publish(skill, versionId);
    }

    /**
     * 【内部发布主逻辑】草稿版本转为已发布版本
     * <p>乐观锁更新：where条件带上status=DRAFT，防止并发重复发布</p>
     *
     * @param skill 技能实体
     * @param versionId 版本主键ID
     * @return 发布后版本VO
     */
    private SkillVersionVO publish(SkillEntity skill, Long versionId) {
        Long skillId = skill.getId();

        // 校验Skill主表状态：必须启用才能发布新版本
        if (!SkillStatus.ACTIVE.matches(skill.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Skill 已停用");
        }
        // 校验版本记录存在，归属当前skillId
        SkillVersionEntity entity = versionMapper.selectById(versionId);
        if (entity == null || !skillId.equals(entity.getSkillId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Skill 版本不存在");
        }

        // 只允许草稿状态执行发布
        if (!SkillVersionStatus.DRAFT.matches(entity.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Skill 版本已经发布或状态非法");
        }

        // 前置校验：对象存储中的zip包实体必须存在，防止数据库记录存在但是包丢失
        if (!storage.exists(entity.getStorageKey())) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Skill 包对象不存在");
        }

        // 更新状态为PUBLISHED，填充发布人、发布时间
        entity.setStatus(SkillVersionStatus.PUBLISHED.getCode());
        entity.setPublishedBy(AuthUtils.getUserIdOrException());
        entity.setPublishedAt(LocalDateTime.now());
        // 乐观锁条件更新：必须同时匹配id、skillId、status=草稿；并发场景下重复发布直接更新行数=0抛异常
        int updated = versionMapper.update(entity, new LambdaUpdateWrapper<SkillVersionEntity>()
                .eq(SkillVersionEntity::getId, versionId)
                .eq(SkillVersionEntity::getSkillId, skillId)
                .eq(SkillVersionEntity::getStatus, SkillVersionStatus.DRAFT.getCode()));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "Skill 版本状态冲突，仅草稿可发布");
        }
        // 更新Skill主表更新时间戳
        skillService.markUpdated(skill);
        // 记录SkillVersion变更日志
        auditLogService.record(skill.getSpaceId(), "SKILL_VERSION_PUBLISHED", "skill_version", entity.getId(),
                Map.of("versionNo", entity.getVersionNo(), "sha256", entity.getSha256()));
        return toVO(entity);
    }

    /**
     * 实体转VO
     *
     * @param entity 数据库版本实体
     * @return 对外展示VO
     */
    private SkillVersionVO toVO(SkillVersionEntity entity) {
        return SkillVersionConvertor.toVO(entity);
    }

    /**
     * 递归删除临时目录及其内部所有文件；删除失败仅打warn，不阻断主流程
     * <p>上传结束后清理本地临时解压目录，避免服务器磁盘占用堆积</p>
     *
     * @param path 临时目录路径
     */
    private void deleteRecursively(Path path) {
        try (var stream = Files.walk(path)) {
            // 反向排序，先删子文件再删目录，否则目录非空无法删除
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
