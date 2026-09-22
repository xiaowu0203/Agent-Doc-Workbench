package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.vo.ReplaySourceVO;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.enums.EvaluationVersionStatus;
import com.agentdoc.evaluation.evaluator.EvaluatorContractValidator;
import com.agentdoc.evaluation.mapper.EvaluationDatasetCaseMapper;
import com.agentdoc.evaluation.mapper.EvaluationDatasetMapper;
import com.agentdoc.evaluation.mapper.EvaluationDatasetVersionMapper;
import com.agentdoc.evaluation.mapper.EvaluatorMapper;
import com.agentdoc.evaluation.mapper.EvaluatorVersionMapper;
import com.agentdoc.evaluation.mapper.EvaluationTestCaseVersionMapper;
import com.agentdoc.evaluation.mapper.EvaluationTestCaseMapper;
import com.agentdoc.evaluation.mapper.TestCaseEvaluatorMapper;
import com.agentdoc.evaluation.pojo.dto.DatasetCaseBindingDTO;
import com.agentdoc.evaluation.pojo.dto.DatasetCaseBindingsDTO;
import com.agentdoc.evaluation.pojo.dto.DatasetVersionCreateDTO;
import com.agentdoc.evaluation.pojo.dto.EvaluationDatasetCreateDTO;
import com.agentdoc.evaluation.pojo.dto.EvaluatorCreateDTO;
import com.agentdoc.evaluation.pojo.dto.EvaluatorVersionCreateDTO;
import com.agentdoc.evaluation.pojo.dto.EvaluationTestCaseCreateDTO;
import com.agentdoc.evaluation.pojo.dto.TestCaseEvaluatorBindingDTO;
import com.agentdoc.evaluation.pojo.dto.TestCaseEvaluatorBindingsDTO;
import com.agentdoc.evaluation.pojo.dto.TestCaseVersionCreateDTO;
import com.agentdoc.evaluation.pojo.param.EvaluationResourceSearchParam;
import com.agentdoc.evaluation.pojo.param.EvaluationVersionSearchParam;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetCaseEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetVersionEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseVersionEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluatorEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluatorVersionEntity;
import com.agentdoc.evaluation.pojo.entity.TestCaseEvaluatorEntity;
import com.agentdoc.evaluation.pojo.vo.DatasetVersionVO;
import com.agentdoc.evaluation.pojo.vo.EvaluationDatasetVO;
import com.agentdoc.evaluation.pojo.vo.EvaluatorVO;
import com.agentdoc.evaluation.pojo.vo.EvaluatorVersionVO;
import com.agentdoc.evaluation.pojo.vo.EvaluationTestCaseVO;
import com.agentdoc.evaluation.pojo.vo.TestCaseVersionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.agentdoc.common.enums.TaskExecutionMode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_MANAGE;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_READ;
import static com.agentdoc.evaluation.constant.EvaluationConstant.BUILT_IN_EVALUATORS;
import static com.agentdoc.evaluation.constant.EvaluationConstant.CONTENT_HASH_SCHEMA_VERSION;

/**
 * Evaluation 目录服务
 * <p>
 * 职责：评估数据集、测试用例、评估器的生命周期管理：创建、版本新增、绑定关系修改、版本发布；
 * 统一权限校验、数据合法性校验、版本号自增、内容快照哈希生成；
 * 所有写操作事务化，发布时固化内容Hash，保证版本不可篡改。
 * </p>
 * <p>
 * 领域约束：
 * 1. 主资源（Dataset/TestCase/Evaluator）支持归档；归档后主资源不能新建版本
 * 2. 版本分 DRAFT(草稿) / PUBLISHED(已发布) / ARCHIVED(版本归档)
 * 3. 只有 DRAFT 版本允许修改绑定关系；发布后不可变更，生成稳定 contentHash
 * 4. 跨资源引用强校验：必须同空间、已发布、父资源未归档
 * 5. 发布动作是“冻结快照”的唯一入口，hash 基于业务语义快照而非数据库 row md5
 * </p>
 */
@Service
@RequiredArgsConstructor
public class EvaluationCatalogService {

    private final EvaluationDatasetMapper datasetMapper;
    private final EvaluationDatasetVersionMapper datasetVersionMapper;
    private final EvaluationDatasetCaseMapper datasetCaseMapper;
    private final EvaluationTestCaseMapper testCaseMapper;
    private final EvaluationTestCaseVersionMapper testCaseVersionMapper;
    private final TestCaseEvaluatorMapper testCaseEvaluatorMapper;
    private final EvaluatorMapper evaluatorMapper;
    private final EvaluatorVersionMapper evaluatorVersionMapper;
    private final SpaceAccessService spaceAccessService;
    private final TaskFeign taskFeign;
    private final EvaluatorContractValidator evaluatorContractValidator;

    /**
     * 支持的任务溯源类型：原始任务、重跑任务、评审返工任务
     * 只有这三类任务可以作为测试用例基线来源，保证来源是人工核验过的有效样本
     */
    private static final Set<String> SUPPORTED_LIVE_LINEAGES = Set.of("ORIGINAL", "RERUN", "REVIEW_REWORK");

    // ===================== 数据集查询 =====================

    /**
     * 分页查询数据集主资源
     * @param param 查询参数（空间、关键词、归档过滤、分页）
     * @return 分页VO
     */
    public PageVO<EvaluationDatasetVO> searchDatasets(EvaluationResourceSearchParam param) {
        // 参数自校验（pageNum/pageSize、keyword长度）
        param.validate();
        // 空间维度读权限校验
        spaceAccessService.requirePermission(param.getSpaceId(), EVALUATION_READ);
        Page<EvaluationDatasetEntity> result = datasetMapper.selectPage(page(param),
                new LambdaQueryWrapper<EvaluationDatasetEntity>()
                        .eq(EvaluationDatasetEntity::getSpaceId, param.getSpaceId())
                        .eq(param.getArchived() != null, EvaluationDatasetEntity::getArchived, param.getArchived())
                        .like(hasText(param.getKeyword()), EvaluationDatasetEntity::getName, trim(param.getKeyword()))
                        .orderByDesc(EvaluationDatasetEntity::getId));
        return PageVO.of(result.getRecords().stream().map(EvaluationDatasetVO::from).toList(),
                result.getTotal(), param);
    }

    /**
     * 分页查询测试用例主资源
     */
    public PageVO<EvaluationTestCaseVO> searchTestCases(EvaluationResourceSearchParam param) {
        param.validate();
        spaceAccessService.requirePermission(param.getSpaceId(), EVALUATION_READ);
        Page<EvaluationTestCaseEntity> result = testCaseMapper.selectPage(page(param),
                new LambdaQueryWrapper<EvaluationTestCaseEntity>()
                        .eq(EvaluationTestCaseEntity::getSpaceId, param.getSpaceId())
                        .eq(param.getArchived() != null, EvaluationTestCaseEntity::getArchived, param.getArchived())
                        .like(hasText(param.getKeyword()), EvaluationTestCaseEntity::getName, trim(param.getKeyword()))
                        .orderByDesc(EvaluationTestCaseEntity::getId));
        return PageVO.of(result.getRecords().stream().map(EvaluationTestCaseVO::from).toList(),
                result.getTotal(), param);
    }

    /**
     * 分页查询评估器主资源；支持按名称或 evaluatorKey 模糊搜索
     */
    public PageVO<EvaluatorVO> searchEvaluators(EvaluationResourceSearchParam param) {
        param.validate();
        spaceAccessService.requirePermission(param.getSpaceId(), EVALUATION_READ);
        Page<EvaluatorEntity> result = evaluatorMapper.selectPage(page(param),
                new LambdaQueryWrapper<EvaluatorEntity>()
                        .eq(EvaluatorEntity::getSpaceId, param.getSpaceId())
                        .eq(param.getArchived() != null, EvaluatorEntity::getArchived, param.getArchived())
                        .and(hasText(param.getKeyword()), value -> value
                                .like(EvaluatorEntity::getName, trim(param.getKeyword())).or()
                                .like(EvaluatorEntity::getEvaluatorKey, trim(param.getKeyword())))
                        .orderByDesc(EvaluatorEntity::getId));
        return PageVO.of(result.getRecords().stream().map(EvaluatorVO::from).toList(),
                result.getTotal(), param);
    }

    // ===================== 版本查询 =====================

    /**
     * 分页查询数据集版本
     * parentId = datasetId，用于查某个数据集下所有版本
     */
    public PageVO<DatasetVersionVO> searchDatasetVersions(EvaluationVersionSearchParam param) {
        param.validate();
        spaceAccessService.requirePermission(param.getSpaceId(), EVALUATION_READ);
        Page<EvaluationDatasetVersionEntity> result = datasetVersionMapper.selectPage(page(param),
                new LambdaQueryWrapper<EvaluationDatasetVersionEntity>()
                        .eq(EvaluationDatasetVersionEntity::getSpaceId, param.getSpaceId())
                        .eq(param.getParentId() != null, EvaluationDatasetVersionEntity::getDatasetId,
                                param.getParentId())
                        .eq(param.getStatus() != null, EvaluationDatasetVersionEntity::getStatus,
                                param.getStatus() == null ? null : param.getStatus().name())
                        .orderByDesc(EvaluationDatasetVersionEntity::getId));
        return PageVO.of(result.getRecords().stream().map(DatasetVersionVO::from).toList(),
                result.getTotal(), param);
    }

    /**
     * 分页查询测试用例版本
     * parentId = testCaseId
     */
    public PageVO<TestCaseVersionVO> searchTestCaseVersions(EvaluationVersionSearchParam param) {
        param.validate();
        spaceAccessService.requirePermission(param.getSpaceId(), EVALUATION_READ);
        Page<EvaluationTestCaseVersionEntity> result = testCaseVersionMapper.selectPage(page(param),
                new LambdaQueryWrapper<EvaluationTestCaseVersionEntity>()
                        .eq(EvaluationTestCaseVersionEntity::getSpaceId, param.getSpaceId())
                        .eq(param.getParentId() != null, EvaluationTestCaseVersionEntity::getTestCaseId,
                                param.getParentId())
                        .eq(param.getStatus() != null, EvaluationTestCaseVersionEntity::getStatus,
                                param.getStatus() == null ? null : param.getStatus().name())
                        .orderByDesc(EvaluationTestCaseVersionEntity::getId));
        return PageVO.of(result.getRecords().stream().map(TestCaseVersionVO::from).toList(),
                result.getTotal(), param);
    }

    /**
     * 分页查询评估器版本
     * parentId = evaluatorId
     */
    public PageVO<EvaluatorVersionVO> searchEvaluatorVersions(EvaluationVersionSearchParam param) {
        param.validate();
        spaceAccessService.requirePermission(param.getSpaceId(), EVALUATION_READ);
        Page<EvaluatorVersionEntity> result = evaluatorVersionMapper.selectPage(page(param),
                new LambdaQueryWrapper<EvaluatorVersionEntity>()
                        .eq(EvaluatorVersionEntity::getSpaceId, param.getSpaceId())
                        .eq(param.getParentId() != null, EvaluatorVersionEntity::getEvaluatorId,
                                param.getParentId())
                        .eq(param.getStatus() != null, EvaluatorVersionEntity::getStatus,
                                param.getStatus() == null ? null : param.getStatus().name())
                        .orderByDesc(EvaluatorVersionEntity::getId));
        return PageVO.of(result.getRecords().stream().map(EvaluatorVersionVO::from).toList(),
                result.getTotal(), param);
    }

    // ===================== 单条详情查询 =====================

    public EvaluationDatasetVO getDataset(Long id) {
        EvaluationDatasetEntity entity = datasetMapper.selectById(id);
        requireReadable(entity == null ? null : entity.getSpaceId(), "Dataset");
        return EvaluationDatasetVO.from(entity);
    }

    public DatasetVersionVO getDatasetVersion(Long id) {
        EvaluationDatasetVersionEntity entity = datasetVersionMapper.selectById(id);
        requireReadable(entity == null ? null : entity.getSpaceId(), "DatasetVersion");
        return DatasetVersionVO.from(entity);
    }

    public EvaluationTestCaseVO getTestCase(Long id) {
        EvaluationTestCaseEntity entity = testCaseMapper.selectById(id);
        requireReadable(entity == null ? null : entity.getSpaceId(), "TestCase");
        return EvaluationTestCaseVO.from(entity);
    }

    public TestCaseVersionVO getTestCaseVersion(Long id) {
        EvaluationTestCaseVersionEntity entity = testCaseVersionMapper.selectById(id);
        requireReadable(entity == null ? null : entity.getSpaceId(), "TestCaseVersion");
        return TestCaseVersionVO.from(entity);
    }

    public EvaluatorVO getEvaluator(Long id) {
        EvaluatorEntity entity = evaluatorMapper.selectById(id);
        requireReadable(entity == null ? null : entity.getSpaceId(), "Evaluator");
        return EvaluatorVO.from(entity);
    }

    public EvaluatorVersionVO getEvaluatorVersion(Long id) {
        EvaluatorVersionEntity entity = evaluatorVersionMapper.selectById(id);
        requireReadable(entity == null ? null : entity.getSpaceId(), "EvaluatorVersion");
        return EvaluatorVersionVO.from(entity);
    }

    // ===================== 归档（主资源/版本） =====================

    /**
     * 归档数据集主资源（软删除语义，标记 archived=true）
     * 归档后不能新建版本，但历史已发布版本仍然可以被评估运行引用
     */
    @Transactional
    public EvaluationDatasetVO archiveDataset(Long id) {
        EvaluationDatasetEntity entity = datasetMapper.selectById(id);
        requireManage(entity == null ? null : entity.getSpaceId(), "Dataset");
        entity.setArchived(true);
        datasetMapper.updateById(entity);
        return EvaluationDatasetVO.from(entity);
    }

    @Transactional
    public EvaluationTestCaseVO archiveTestCase(Long id) {
        EvaluationTestCaseEntity entity = testCaseMapper.selectById(id);
        requireManage(entity == null ? null : entity.getSpaceId(), "TestCase");
        entity.setArchived(true);
        testCaseMapper.updateById(entity);
        return EvaluationTestCaseVO.from(entity);
    }

    @Transactional
    public EvaluatorVO archiveEvaluator(Long id) {
        EvaluatorEntity entity = evaluatorMapper.selectById(id);
        requireManage(entity == null ? null : entity.getSpaceId(), "Evaluator");
        entity.setArchived(true);
        evaluatorMapper.updateById(entity);
        return EvaluatorVO.from(entity);
    }

    /**
     * 归档【版本】：版本级别的归档，仅 PUBLISHED 版本可归档
     * 和主资源归档是两套独立开关
     */
    @Transactional
    public DatasetVersionVO archiveDatasetVersion(Long id) {
        EvaluationDatasetVersionEntity entity = datasetVersionMapper.selectById(id);
        requireArchivable(entity == null ? null : entity.getSpaceId(), entity == null ? null : entity.getStatus(),
                "DatasetVersion");
        entity.setStatus(EvaluationVersionStatus.ARCHIVED.name());
        datasetVersionMapper.updateById(entity);
        return DatasetVersionVO.from(entity);
    }

    @Transactional
    public TestCaseVersionVO archiveTestCaseVersion(Long id) {
        EvaluationTestCaseVersionEntity entity = testCaseVersionMapper.selectById(id);
        requireArchivable(entity == null ? null : entity.getSpaceId(), entity == null ? null : entity.getStatus(),
                "TestCaseVersion");
        entity.setStatus(EvaluationVersionStatus.ARCHIVED.name());
        testCaseVersionMapper.updateById(entity);
        return TestCaseVersionVO.from(entity);
    }

    @Transactional
    public EvaluatorVersionVO archiveEvaluatorVersion(Long id) {
        EvaluatorVersionEntity entity = evaluatorVersionMapper.selectById(id);
        requireArchivable(entity == null ? null : entity.getSpaceId(), entity == null ? null : entity.getStatus(),
                "EvaluatorVersion");
        entity.setStatus(EvaluationVersionStatus.ARCHIVED.name());
        evaluatorVersionMapper.updateById(entity);
        return EvaluatorVersionVO.from(entity);
    }

    // ===================== 创建主资源 =====================

    /**
     * 创建评估数据集
     *
     * @param dto 数据集创建入参
     * @return 数据集VO
     * @apiNote 校验空间评估管理权限；生成唯一ID，归档标记默认false
     */
    @Transactional
    public EvaluationDatasetVO createDataset(EvaluationDatasetCreateDTO dto) {
        spaceAccessService.requirePermission(dto.spaceId(), EVALUATION_MANAGE);
        EvaluationDatasetEntity entity = new EvaluationDatasetEntity();
        entity.setId(IdWorker.getId());
        entity.setSpaceId(dto.spaceId());
        entity.setName(dto.name().trim());
        entity.setDescription(trim(dto.description()));
        entity.setArchived(false);
        entity.setCreatedBy(AuthUtils.getUserIdOrException());
        datasetMapper.insert(entity);
        return EvaluationDatasetVO.from(entity);
    }

    /**
     * 创建数据集版本
     *
     * @param dto 数据集版本创建入参
     * @return 数据集版本VO
     * @apiNote 版本初始状态为草稿DRAFT；自动递增版本号；刚创建无绑定用例，后续通过 replaceDatasetCases 添加
     */
    @Transactional
    public DatasetVersionVO createDatasetVersion(DatasetVersionCreateDTO dto) {
        EvaluationDatasetEntity dataset = requireDataset(dto.datasetId());
        spaceAccessService.requirePermission(dataset.getSpaceId(), EVALUATION_MANAGE);
        EvaluationDatasetVersionEntity entity = new EvaluationDatasetVersionEntity();
        entity.setId(IdWorker.getId());
        entity.setDatasetId(dataset.getId());
        entity.setSpaceId(dataset.getSpaceId());
        entity.setVersionNo(nextDatasetVersion(dataset.getId()));
        entity.setStatus(EvaluationVersionStatus.DRAFT.name());
        entity.setCreatedBy(AuthUtils.getUserIdOrException());
        datasetVersionMapper.insert(entity);
        return DatasetVersionVO.from(entity);
    }

    /**
     * 替换数据集版本绑定的测试用例
     * <p>删除原有绑定关系，批量新增；仅草稿版本可操作。</p>
     *
     * @param versionId 数据集版本ID
     * @param dto 用例绑定DTO
     * @return 数据集版本VO
     * @apiNote 约束：不可重复绑定同一个测试用例版本；只能绑定同空间已发布的测试用例版本；
     *          发布前这里只是草稿内存状态，contentHash 在 publish 时才计算
     */
    @Transactional
    public DatasetVersionVO replaceDatasetCases(Long versionId, DatasetCaseBindingsDTO dto) {
        EvaluationDatasetVersionEntity version = requireDraftDatasetVersion(versionId);
        spaceAccessService.requirePermission(version.getSpaceId(), EVALUATION_MANAGE);
        // 校验入参无重复用例版本
        if (dto.cases().stream().map(DatasetCaseBindingDTO::testCaseVersionId).distinct().count() != dto.cases().size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "测试用例版本不能重复绑定");
        }
        List<EvaluationTestCaseVersionEntity> cases = testCaseVersionMapper.selectBatchIds(
                dto.cases().stream().map(DatasetCaseBindingDTO::testCaseVersionId).toList());
        // 取出所有父用例主资源，校验主资源未归档
        Set<Long> activeTestCaseIds = testCaseMapper.selectBatchIds(cases.stream()
                        .map(EvaluationTestCaseVersionEntity::getTestCaseId).collect(Collectors.toSet())).stream()
                .filter(value -> !Boolean.TRUE.equals(value.getArchived()))
                .map(EvaluationTestCaseEntity::getId).collect(Collectors.toSet());
        // 校验：全部存在、同空间、版本已发布、父用例未归档
        if (cases.size() != dto.cases().size() || cases.stream().anyMatch(value ->
                !version.getSpaceId().equals(value.getSpaceId())
                        || !EvaluationVersionStatus.PUBLISHED.name().equals(value.getStatus())
                        || !activeTestCaseIds.contains(value.getTestCaseId()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "数据集只能绑定同空间已发布测试用例版本");
        }
        // 清空旧绑定
        datasetCaseMapper.delete(new LambdaQueryWrapper<EvaluationDatasetCaseEntity>()
                .eq(EvaluationDatasetCaseEntity::getDatasetVersionId, versionId));
        // 批量插入新绑定
        List<EvaluationDatasetCaseEntity> datasetCases = dto.cases().stream().map(binding -> {
            EvaluationDatasetCaseEntity entity = new EvaluationDatasetCaseEntity();
            entity.setId(IdWorker.getId());
            entity.setDatasetVersionId(versionId);
            entity.setTestCaseVersionId(binding.testCaseVersionId());
            entity.setSortOrder(binding.sortOrder());
            entity.setEnabled(binding.enabled() == null || binding.enabled());
            return entity;
        }).toList();
        if (!datasetCases.isEmpty()) {
            datasetCaseMapper.insertBatch(datasetCases);
        }
        return DatasetVersionVO.from(version);
    }

    /**
     * 发布数据集草稿版本
     * <p>发布后状态变更为PUBLISHED，固化内容Hash，版本不可再修改。</p>
     *
     * @param id 数据集版本ID
     * @return 数据集版本VO
     * @apiNote 校验：至少存在一条启用状态的绑定用例；基于绑定用例快照生成内容Hash；
     *          发布是不可逆操作；contentHash 是评估运行时用来判断基线是否变更的唯一凭证
     */
    @Transactional
    public DatasetVersionVO publishDatasetVersion(Long id) {
        EvaluationDatasetVersionEntity version = requireDraftDatasetVersion(id);
        spaceAccessService.requirePermission(version.getSpaceId(), EVALUATION_MANAGE);
        EvaluationDatasetEntity dataset = requireDataset(version.getDatasetId());
        if (!version.getSpaceId().equals(dataset.getSpaceId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "DatasetVersion 空间归属不一致");
        }
        // 按 sortOrder 有序取出绑定关系（顺序参与hash计算，顺序不同hash不同）
        List<EvaluationDatasetCaseEntity> bindings = datasetCaseMapper.selectList(
                new LambdaQueryWrapper<EvaluationDatasetCaseEntity>()
                        .eq(EvaluationDatasetCaseEntity::getDatasetVersionId, id)
                        .orderByAsc(EvaluationDatasetCaseEntity::getSortOrder, EvaluationDatasetCaseEntity::getId));
        if (bindings.isEmpty() || bindings.stream().noneMatch(value -> Boolean.TRUE.equals(value.getEnabled()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "数据集版本至少需要一个启用用例");
        }
        List<EvaluationTestCaseVersionEntity> caseVersions = testCaseVersionMapper.selectBatchIds(bindings.stream()
                .map(EvaluationDatasetCaseEntity::getTestCaseVersionId).toList());
        Set<Long> activeTestCaseIds = testCaseMapper.selectBatchIds(caseVersions.stream()
                        .map(EvaluationTestCaseVersionEntity::getTestCaseId).collect(Collectors.toSet())).stream()
                .filter(value -> !Boolean.TRUE.equals(value.getArchived()))
                .map(EvaluationTestCaseEntity::getId).collect(Collectors.toSet());
        // 二次校验引用有效性（防止发布过程中别的事务归档了资源）
        if (caseVersions.size() != bindings.size() || caseVersions.stream().anyMatch(value ->
                !version.getSpaceId().equals(value.getSpaceId())
                        || !EvaluationVersionStatus.PUBLISHED.name().equals(value.getStatus())
                        || !activeTestCaseIds.contains(value.getTestCaseId()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "DatasetVersion 引用了已归档或不可用的 TestCaseVersion");
        }
        // 生成稳定快照哈希，顺序敏感
        version.setContentHash(StableSnapshotUtils.snapshotHash(CONTENT_HASH_SCHEMA_VERSION,
                bindings.stream().map(value -> new DatasetCaseHash(value.getTestCaseVersionId(),
                        value.getSortOrder(), value.getEnabled())).toList()));
        version.setStatus(EvaluationVersionStatus.PUBLISHED.name());
        version.setPublishedAt(LocalDateTime.now());
        datasetVersionMapper.updateById(version);
        return DatasetVersionVO.from(version);
    }

    /**
     * 创建测试用例主资源
     */
    @Transactional
    public EvaluationTestCaseVO createTestCase(EvaluationTestCaseCreateDTO dto) {
        spaceAccessService.requirePermission(dto.spaceId(), EVALUATION_MANAGE);
        EvaluationTestCaseEntity entity = new EvaluationTestCaseEntity();
        entity.setId(IdWorker.getId());
        entity.setSpaceId(dto.spaceId());
        entity.setName(dto.name().trim());
        entity.setDescription(trim(dto.description()));
        entity.setArchived(false);
        entity.setCreatedBy(AuthUtils.getUserIdOrException());
        testCaseMapper.insert(entity);
        return EvaluationTestCaseVO.from(entity);
    }

    /**
     * 创建测试用例版本
     * <p>首版仅支持 LIVE 来源；拉取任务回放源并校验可回放性、溯源类型、文档快照完整性。</p>
     *
     * @param dto 测试用例版本创建入参
     * @return 测试用例版本VO
     * @apiNote 预期JSON提前做语法校验；保存任务输入/执行快照、文档版本与内容哈希；
     *          刚创建为 DRAFT，评估器绑定后续通过 replaceTestCaseEvaluators 设置
     */
    @Transactional
    public TestCaseVersionVO createTestCaseVersion(TestCaseVersionCreateDTO dto) {
        EvaluationTestCaseEntity testCase = requireTestCase(dto.testCaseId());
        spaceAccessService.requirePermission(testCase.getSpaceId(), EVALUATION_MANAGE);
        if (!TaskExecutionMode.LIVE.name().equals(dto.sourceType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "首版测试用例仅支持 LIVE 来源");
        }
        // 远程调用任务服务，拉取回放基线元数据
        ReplaySourceVO source = requireData(taskFeign.getReplaySource(dto.sourceTaskId(), testCase.getSpaceId()));
        if (!source.replayable()) {
            throw new BusinessException(ErrorCode.CONFLICT, "来源任务不可 Replay：" + source.reasonCode());
        }
        if (!SUPPORTED_LIVE_LINEAGES.contains(source.sourceLineage())) {
            throw new BusinessException(ErrorCode.CONFLICT, "测试用例仅接受 ORIGINAL、RERUN 或 REVIEW_REWORK 来源");
        }
        if (source.documentVersionSnapshot() == null || source.documentContentSha256() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "来源任务缺少冻结文档版本或内容 hash");
        }
        // 预校验预期输出 JSON 语法合法
        JsonUtils.parse(dto.expectedJson(), Object.class);
        EvaluationTestCaseVersionEntity entity = new EvaluationTestCaseVersionEntity();
        entity.setId(IdWorker.getId());
        entity.setTestCaseId(testCase.getId());
        entity.setSpaceId(testCase.getSpaceId());
        entity.setVersionNo(nextTestCaseVersion(testCase.getId()));
        entity.setStatus(EvaluationVersionStatus.DRAFT.name());
        entity.setSourceTaskId(source.sourceTaskId());
        entity.setSourceExecutionId(source.sourceExecutionId());
        entity.setSourceInputSchemaVersion(source.inputSnapshotSchemaVersion());
        entity.setSourceInputHash(source.inputSnapshotHash());
        entity.setSourceExecutionSchemaVersion(source.executionSnapshotSchemaVersion());
        entity.setSourceExecutionHash(source.executionSnapshotHash());
        entity.setDocumentVersionSnapshot(source.documentVersionSnapshot());
        entity.setDocumentContentSha256(source.documentContentSha256());
        entity.setExpectedSchemaVersion(dto.expectedSchemaVersion());
        entity.setExpectedJson(dto.expectedJson());
        entity.setSourceType(dto.sourceType());
        entity.setSanitizationNote(trim(dto.sanitizationNote()));
        entity.setCreatedBy(AuthUtils.getUserIdOrException());
        testCaseVersionMapper.insert(entity);
        return TestCaseVersionVO.from(entity);
    }

    /**
     * 替换测试用例版本绑定的评估器
     * <p>删除旧绑定关系，批量新增评估器绑定；仅草稿版本可操作。</p>
     *
     * @param versionId 测试用例版本ID
     * @param dto 评估器绑定DTO
     * @return 测试用例版本VO
     * @apiNote 约束：不可重复绑定同一评估器版本；仅允许绑定同空间已发布评估器版本；绑定级别预期JSON语法校验
     */
    @Transactional
    public TestCaseVersionVO replaceTestCaseEvaluators(Long versionId, TestCaseEvaluatorBindingsDTO dto) {
        EvaluationTestCaseVersionEntity version = requireDraftTestCaseVersion(versionId);
        spaceAccessService.requirePermission(version.getSpaceId(), EVALUATION_MANAGE);
        if (dto.evaluators().stream().map(TestCaseEvaluatorBindingDTO::evaluatorVersionId).distinct().count()
                != dto.evaluators().size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评估器版本不能重复绑定");
        }
        List<EvaluatorVersionEntity> evaluators = evaluatorVersionMapper.selectBatchIds(
                dto.evaluators().stream().map(TestCaseEvaluatorBindingDTO::evaluatorVersionId).toList());
        Set<Long> activeEvaluatorIds = evaluatorMapper.selectBatchIds(evaluators.stream()
                        .map(EvaluatorVersionEntity::getEvaluatorId).collect(Collectors.toSet())).stream()
                .filter(value -> !Boolean.TRUE.equals(value.getArchived()))
                .map(EvaluatorEntity::getId).collect(Collectors.toSet());
        if (evaluators.size() != dto.evaluators().size() || evaluators.stream().anyMatch(value ->
                !version.getSpaceId().equals(value.getSpaceId())
                        || !EvaluationVersionStatus.PUBLISHED.name().equals(value.getStatus())
                        || !activeEvaluatorIds.contains(value.getEvaluatorId()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "测试用例只能绑定同空间已发布评估器版本");
        }
        // 校验绑定行上自定义预期JSON语法
        dto.evaluators().stream().map(TestCaseEvaluatorBindingDTO::expectedJson)
                .filter(value -> value != null && !value.isBlank()).forEach(value -> JsonUtils.parse(value, Object.class));
        // 清空旧绑定
        testCaseEvaluatorMapper.delete(new LambdaQueryWrapper<TestCaseEvaluatorEntity>()
                .eq(TestCaseEvaluatorEntity::getTestCaseVersionId, versionId));
        // 插入新绑定
        List<TestCaseEvaluatorEntity> evaluatorBindings = dto.evaluators().stream().map(binding -> {
            TestCaseEvaluatorEntity entity = new TestCaseEvaluatorEntity();
            entity.setId(IdWorker.getId());
            entity.setTestCaseVersionId(versionId);
            entity.setEvaluatorVersionId(binding.evaluatorVersionId());
            entity.setExpectedJson(trim(binding.expectedJson()));
            entity.setSortOrder(binding.sortOrder());
            return entity;
        }).toList();
        if (!evaluatorBindings.isEmpty()) {
            testCaseEvaluatorMapper.insertBatch(evaluatorBindings);
        }
        return TestCaseVersionVO.from(version);
    }

    /**
     * 发布测试用例草稿版本
     * <p>发布前校验评估器契约；固化内容Hash，版本不可修改。</p>
     *
     * @param id 测试用例版本ID
     * @return 测试用例版本VO
     * @apiNote 至少绑定一个评估器；逐个调用评估器契约校验器验证预期输出结构；合并所有绑定信息生成快照Hash；
     *          发布后基线固定，后续评估运行都复用这套输入+预期+评估器集合
     */
    @Transactional
    public TestCaseVersionVO publishTestCaseVersion(Long id) {
        EvaluationTestCaseVersionEntity version = requireDraftTestCaseVersion(id);
        spaceAccessService.requirePermission(version.getSpaceId(), EVALUATION_MANAGE);
        EvaluationTestCaseEntity testCase = requireTestCase(version.getTestCaseId());
        if (!version.getSpaceId().equals(testCase.getSpaceId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "TestCaseVersion 空间归属不一致");
        }
        List<TestCaseEvaluatorEntity> bindings = testCaseEvaluatorMapper.selectList(
                new LambdaQueryWrapper<TestCaseEvaluatorEntity>()
                        .eq(TestCaseEvaluatorEntity::getTestCaseVersionId, id)
                        .orderByAsc(TestCaseEvaluatorEntity::getSortOrder, TestCaseEvaluatorEntity::getId));
        if (bindings.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "测试用例版本至少需要绑定一个评估器版本");
        }
        Map<Long, EvaluatorVersionEntity> evaluatorVersions = evaluatorVersionMapper.selectBatchIds(
                        bindings.stream().map(TestCaseEvaluatorEntity::getEvaluatorVersionId).toList()).stream()
                .collect(Collectors.toMap(EvaluatorVersionEntity::getId, Function.identity()));
        Set<Long> activeEvaluatorIds = evaluatorMapper.selectBatchIds(evaluatorVersions.values().stream()
                        .map(EvaluatorVersionEntity::getEvaluatorId).collect(Collectors.toSet())).stream()
                .filter(value -> !Boolean.TRUE.equals(value.getArchived()))
                .map(EvaluatorEntity::getId).collect(Collectors.toSet());
        // 逐个校验评估器契约：评估器存在、状态合法、预期输出符合评估器定义 schema
        for (TestCaseEvaluatorEntity binding : bindings) {
            EvaluatorVersionEntity evaluatorVersion = evaluatorVersions.get(binding.getEvaluatorVersionId());
            if (evaluatorVersion == null || !version.getSpaceId().equals(evaluatorVersion.getSpaceId())
                    || !EvaluationVersionStatus.PUBLISHED.name().equals(evaluatorVersion.getStatus())
                    || !activeEvaluatorIds.contains(evaluatorVersion.getEvaluatorId())) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "TestCaseVersion 引用了不可用的 EvaluatorVersion");
            }
            // 优先取绑定行上的预期；为空则回退到用例版本全局 expectedJson
            String expectedJson = binding.getExpectedJson() == null || binding.getExpectedJson().isBlank()
                    ? version.getExpectedJson() : binding.getExpectedJson();
            evaluatorContractValidator.validateExpected(evaluatorVersion.getEvaluatorKey(), expectedJson);
        }
        Object expected = JsonUtils.parse(version.getExpectedJson(), Object.class);
        List<TestCaseEvaluatorHash> evaluatorHashes = bindings.stream().map(value -> new TestCaseEvaluatorHash(
                value.getEvaluatorVersionId(), parseOptionalJson(value.getExpectedJson()), value.getSortOrder())).toList();
        // 构建完整基线快照，计算全局 contentHash
        version.setContentHash(StableSnapshotUtils.snapshotHash(CONTENT_HASH_SCHEMA_VERSION,
                new TestCaseHash(version.getSourceTaskId(), version.getSourceExecutionId(),
                        version.getSourceInputSchemaVersion(), version.getSourceInputHash(),
                        version.getSourceExecutionSchemaVersion(), version.getSourceExecutionHash(),
                        version.getDocumentVersionSnapshot(), version.getDocumentContentSha256(),
                        version.getExpectedSchemaVersion(), expected, version.getSourceType(),
                        version.getSanitizationNote(), evaluatorHashes)));
        version.setStatus(EvaluationVersionStatus.PUBLISHED.name());
        version.setPublishedAt(LocalDateTime.now());
        testCaseVersionMapper.updateById(version);
        return TestCaseVersionVO.from(version);
    }

    /**
     * 创建评估器定义
     *
     * @param dto 评估器创建入参
     * @return 评估器VO
     * @apiNote 仅允许创建内置评估器，key必须在 EvaluationConstant.BUILT_IN_EVALUATORS 内；
     *          内置评估器是系统预置实现，用户只能配置版本参数，不能自定义代码实现
     */
    @Transactional
    public EvaluatorVO createEvaluator(EvaluatorCreateDTO dto) {
        spaceAccessService.requirePermission(dto.spaceId(), EVALUATION_MANAGE);
        if (!BUILT_IN_EVALUATORS.contains(dto.evaluatorKey())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的内置 Evaluator key");
        }
        EvaluatorEntity entity = new EvaluatorEntity();
        entity.setId(IdWorker.getId());
        entity.setSpaceId(dto.spaceId());
        entity.setName(dto.name().trim());
        entity.setEvaluatorKey(dto.evaluatorKey());
        entity.setDescription(trim(dto.description()));
        entity.setArchived(false);
        entity.setCreatedBy(AuthUtils.getUserIdOrException());
        evaluatorMapper.insert(entity);
        return EvaluatorVO.from(entity);
    }

    /**
     * 创建评估器版本
     *
     * @param dto 评估器版本创建入参
     * @return 评估器版本VO
     * @apiNote 配置JSON语法校验；版本初始状态DRAFT；固化实现版本标识；
     *          implementationVersion 标记评估器运行时代码版本，用于灰度与兼容性排查
     */
    @Transactional
    public EvaluatorVersionVO createEvaluatorVersion(EvaluatorVersionCreateDTO dto) {
        EvaluatorEntity evaluator = requireEvaluator(dto.evaluatorId());
        spaceAccessService.requirePermission(evaluator.getSpaceId(), EVALUATION_MANAGE);
        JsonUtils.parse(dto.configJson(), Object.class);
        EvaluatorVersionEntity entity = new EvaluatorVersionEntity();
        entity.setId(IdWorker.getId());
        entity.setEvaluatorId(evaluator.getId());
        entity.setSpaceId(evaluator.getSpaceId());
        entity.setVersionNo(nextEvaluatorVersion(evaluator.getId()));
        entity.setStatus(EvaluationVersionStatus.DRAFT.name());
        entity.setEvaluatorKey(evaluator.getEvaluatorKey());
        entity.setConfigSchemaVersion(dto.configSchemaVersion());
        entity.setConfigJson(dto.configJson());
        entity.setResultSchemaVersion(dto.resultSchemaVersion());
        entity.setImplementationVersion("phase3-v1");
        entity.setCreatedBy(AuthUtils.getUserIdOrException());
        evaluatorVersionMapper.insert(entity);
        return EvaluatorVersionVO.from(entity);
    }

    /**
     * 发布评估器草稿版本
     * <p>执行评估器契约校验，校验配置结构、输出结构；生成内容快照Hash，版本固化。</p>
     *
     * @param id 评估器版本ID
     * @return 评估器版本VO
     */
    @Transactional
    public EvaluatorVersionVO publishEvaluatorVersion(Long id) {
        EvaluatorVersionEntity version = requireEvaluatorVersion(id);
        spaceAccessService.requirePermission(version.getSpaceId(), EVALUATION_MANAGE);
        EvaluatorEntity evaluator = requireEvaluator(version.getEvaluatorId());
        if (!version.getSpaceId().equals(evaluator.getSpaceId())
                || !version.getEvaluatorKey().equals(evaluator.getEvaluatorKey())) {
            throw new BusinessException(ErrorCode.CONFLICT, "EvaluatorVersion 资源归属不一致");
        }
        if (!EvaluationVersionStatus.DRAFT.name().equals(version.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有 DRAFT 评估器版本可以发布");
        }
        // 契约校验：configJson、resultSchemaVersion 符合该 evaluatorKey 的规范
        evaluatorContractValidator.validateVersion(version.getEvaluatorKey(), version.getConfigSchemaVersion(),
                version.getConfigJson(), version.getResultSchemaVersion());
        version.setContentHash(StableSnapshotUtils.snapshotHash(CONTENT_HASH_SCHEMA_VERSION,
                new EvaluatorHash(version.getEvaluatorKey(), version.getConfigSchemaVersion(),
                        JsonUtils.parse(version.getConfigJson(), Object.class), version.getResultSchemaVersion(),
                        version.getImplementationVersion())));
        version.setStatus(EvaluationVersionStatus.PUBLISHED.name());
        version.setPublishedAt(LocalDateTime.now());
        evaluatorVersionMapper.updateById(version);
        return EvaluatorVersionVO.from(version);
    }

    // ========== 私有校验工具方法 ==========

    /**
     * 获取数据集，不存在/已归档抛异常
     * @param id 数据集ID
     * @return 数据集实体
     */
    private EvaluationDatasetEntity requireDataset(Long id) {
        EvaluationDatasetEntity value = datasetMapper.selectById(id);
        if (value == null || Boolean.TRUE.equals(value.getArchived())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在");
        }
        return value;
    }

    /**
     * 获取测试用例，不存在/已归档抛异常
     * @param id 测试用例ID
     * @return 测试用例实体
     */
    private EvaluationTestCaseEntity requireTestCase(Long id) {
        EvaluationTestCaseEntity value = testCaseMapper.selectById(id);
        if (value == null || Boolean.TRUE.equals(value.getArchived())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "测试用例不存在");
        }
        return value;
    }

    /**
     * 获取【草稿状态】测试用例版本，不存在或已发布抛异常
     * @param id 测试用例版本ID
     * @return 草稿版本实体
     */
    private EvaluationTestCaseVersionEntity requireDraftTestCaseVersion(Long id) {
        EvaluationTestCaseVersionEntity value = testCaseVersionMapper.selectById(id);
        if (value == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "测试用例版本不存在");
        }
        if (!EvaluationVersionStatus.DRAFT.name().equals(value.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已发布测试用例版本不可修改");
        }
        return value;
    }

    /**
     * 获取【草稿状态】数据集版本，不存在或已发布抛异常
     * @param id 数据集版本ID
     * @return 草稿版本实体
     */
    private EvaluationDatasetVersionEntity requireDraftDatasetVersion(Long id) {
        EvaluationDatasetVersionEntity value = datasetVersionMapper.selectById(id);
        if (value == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "数据集版本不存在");
        }
        if (!EvaluationVersionStatus.DRAFT.name().equals(value.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已发布版本不可修改");
        }
        return value;
    }

    /**
     * 获取评估器定义，不存在/已归档抛异常
     * @param id 评估器ID
     * @return 评估器实体
     */
    private EvaluatorEntity requireEvaluator(Long id) {
        EvaluatorEntity value = evaluatorMapper.selectById(id);
        if (value == null || Boolean.TRUE.equals(value.getArchived())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Evaluator 不存在");
        }
        return value;
    }

    /**
     * 获取评估器版本，不存在抛异常（不校验状态）
     * @param id 评估器版本ID
     * @return 评估器版本实体
     */
    private EvaluatorVersionEntity requireEvaluatorVersion(Long id) {
        EvaluatorVersionEntity value = evaluatorVersionMapper.selectById(id);
        if (value == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "EvaluatorVersion 不存在");
        }
        return value;
    }

    /**
     * 读权限校验：资源存在且有 EVALUATION_READ
     */
    private void requireReadable(Long spaceId, String resource) {
        if (spaceId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, resource + " 不存在");
        }
        spaceAccessService.requirePermission(spaceId, EVALUATION_READ);
    }

    /**
     * 管理权限校验：资源存在且有 EVALUATION_MANAGE
     */
    private void requireManage(Long spaceId, String resource) {
        if (spaceId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, resource + " 不存在");
        }
        spaceAccessService.requirePermission(spaceId, EVALUATION_MANAGE);
    }

    /**
     * 版本归档前置校验：只有 PUBLISHED 版本允许归档
     */
    private void requireArchivable(Long spaceId, String status, String resource) {
        requireManage(spaceId, resource);
        if (EvaluationVersionStatus.ARCHIVED.name().equals(status)) {
            return;
        }
        if (!EvaluationVersionStatus.PUBLISHED.name().equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有 PUBLISHED " + resource + " 可以归档");
        }
    }

    /**
     * 获取数据集下一个版本号：取当前最大版本号 +1，无记录则从1开始
     * @param id 数据集ID
     * @return 新版本号
     */
    private int nextDatasetVersion(Long id) {
        EvaluationDatasetVersionEntity latest = datasetVersionMapper.selectOne(new LambdaQueryWrapper<EvaluationDatasetVersionEntity>()
                .eq(EvaluationDatasetVersionEntity::getDatasetId, id).orderByDesc(EvaluationDatasetVersionEntity::getVersionNo).last("LIMIT 1"));
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    /**
     * 获取评估器下一个版本号
     * @param id 评估器ID
     * @return 新版本号
     */
    private int nextEvaluatorVersion(Long id) {
        EvaluatorVersionEntity latest = evaluatorVersionMapper.selectOne(new LambdaQueryWrapper<EvaluatorVersionEntity>()
                .eq(EvaluatorVersionEntity::getEvaluatorId, id).orderByDesc(EvaluatorVersionEntity::getVersionNo).last("LIMIT 1"));
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    /**
     * 获取测试用例下一个版本号
     * @param id 测试用例ID
     * @return 新版本号
     */
    private int nextTestCaseVersion(Long id) {
        EvaluationTestCaseVersionEntity latest = testCaseVersionMapper.selectOne(
                new LambdaQueryWrapper<EvaluationTestCaseVersionEntity>()
                        .eq(EvaluationTestCaseVersionEntity::getTestCaseId, id)
                        .orderByDesc(EvaluationTestCaseVersionEntity::getVersionNo).last("LIMIT 1"));
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    /**
     * Feign结果解包，非成功响应抛出内部异常
     * @param result feign返回结果包装对象
     * @return 业务数据体
     */
    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取 Replay 来源失败");
        }
        return result.data();
    }

    /**
     * 可选JSON解析，空字符串/NULL返回null，否则解析为Object
     * @param value JSON字符串
     * @return 解析对象或null
     */
    private Object parseOptionalJson(String value) {
        return value == null || value.isBlank() ? null : JsonUtils.parse(value, Object.class);
    }

    /**
     * 字符串trim工具：空白字符串统一转为null，避免空串入库
     * @param value 原始字符串
     * @return 处理后字符串
     */
    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 转换为MyBatis-Plus分页对象
     */
    private <T> Page<T> page(com.agentdoc.common.pojo.dto.PageParam param) {
        return new Page<>(param.getPageNum(), param.getPageSize());
    }

    // ========== 快照Hash辅助Record，用于构建稳定签名 ==========
    /** 数据集绑定用例快照结构体 */
    private record DatasetCaseHash(Long testCaseVersionId, Integer sortOrder, Boolean enabled) { }

    /** 评估器版本快照结构体 */
    private record EvaluatorHash(String evaluatorKey, Integer configSchemaVersion, Object config,
                                 Integer resultSchemaVersion, String implementationVersion) { }

    /** 测试用例绑定评估器快照结构体 */
    private record TestCaseEvaluatorHash(Long evaluatorVersionId, Object expected, Integer sortOrder) { }

    /** 测试用例版本完整快照结构体，用于计算contentHash */
    private record TestCaseHash(Long sourceTaskId, Long sourceExecutionId, Integer sourceInputSchemaVersion,
                                String sourceInputHash, Integer sourceExecutionSchemaVersion,
                                String sourceExecutionHash, Long documentVersionSnapshot,
                                String documentContentSha256, Integer expectedSchemaVersion, Object expected,
                                String sourceType, String sanitizationNote,
                                List<TestCaseEvaluatorHash> evaluators) { }
}
