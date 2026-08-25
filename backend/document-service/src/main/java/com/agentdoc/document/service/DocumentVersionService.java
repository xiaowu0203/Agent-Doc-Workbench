package com.agentdoc.document.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.utils.PageUtils;
import com.agentdoc.document.mapper.DocumentMapper;
import com.agentdoc.document.mapper.DocumentVersionMapper;
import com.agentdoc.document.pojo.entity.DocumentEntity;
import com.agentdoc.document.pojo.entity.DocumentVersionEntity;
import com.agentdoc.document.pojo.vo.DocumentVersionDetailVO;
import com.agentdoc.document.pojo.vo.DocumentVersionVO;
import com.agentdoc.document.pojo.vo.VersionCompareVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档版本服务
 * 能力：版本快照生成、版本分页列表、版本详情、版本对比，为文档回滚提供底层支撑
 * 版本规则：
 * 1. 文档内容变更、审批合并、版本回滚时自动生成新版本；version_no 从1开始持续递增
 * 2. 回滚操作会生成全新版本快照，不会修改、删除任何历史版本记录
 * 权限：版本属于文档子资源，读取版本信息需要为所属空间成员
 */
@Service
@RequiredArgsConstructor
public class DocumentVersionService {

    private final DocumentVersionMapper versionMapper;
    private final DocumentMapper documentMapper;
    private final SpacePermissionService permissionService;

    /**
     * 创建文档版本快照
     * 注意：版本号由上层调用方计算并传入，本服务不做版本号自增逻辑
     *
     * @param documentId 文档ID
     * @param versionNo 已经递增完成的新版本号
     * @param content 当前版本完整Markdown正文快照
     * @param changeSummary 本次变更描述摘要
     * @param userId 执行变更的操作人ID
     * @return 版本简单视图VO
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentVersionVO createSnapshot(Long documentId, Long versionNo, String content,
                                            String changeSummary, Long userId) {
        DocumentVersionEntity entity = DocumentVersionEntity.create(
                documentId, versionNo, content, changeSummary, userId);
        // 插入版本快照记录
        versionMapper.insert(entity);
        return entity.toVO();
    }

    /**
     * 查询文档版本分页列表
     * 权限：空间成员可读；按版本号倒序（最新版本排在最前面）；返回VO不含大文本正文快照，用于版本历史列表展示
     *
     * @param documentId 文档ID
     * @param pageParam 分页参数
     * @return 分页对象，版本列表（无正文）
     */
    public PageVO<DocumentVersionVO> listVersions(Long documentId, PageParam pageParam) {
        // 校验文档存在 + 用户具备空间成员可读权限
        checkReadable(documentId);
        Page<DocumentVersionEntity> page = versionMapper.selectPage(
                PageUtils.toPage(pageParam),
                new LambdaQueryWrapper<DocumentVersionEntity>()
                        .eq(DocumentVersionEntity::getDocumentId, documentId)
                        .orderByDesc(DocumentVersionEntity::getVersionNo));
        // 转换为VO返回，不携带content大字段
        return PageVO.of(page.getRecords().stream().map(DocumentVersionEntity::toVO).toList(),
                page.getTotal(), pageParam);
    }

    /**
     * 获取指定版本完整详情，包含该版本的正文快照
     * 权限：空间成员可读
     *
     * @param documentId 文档ID
     * @param versionNo 目标版本号
     * @return 版本详情VO，携带完整Markdown正文快照
     */
    public DocumentVersionDetailVO versionDetail(Long documentId, Long versionNo) {
        // 校验文档存在 + 用户具备空间成员可读权限
        checkReadable(documentId);
        // 获取版本实体并转换为详情VO（包含content）
        return requireVersion(documentId, versionNo).toDetailVO();
    }

    /**
     * 两个版本对比（v0.1简化实现）
     * 后端只取出源版本、目标版本两份完整快照文本；diff文本高亮差异计算交给前端完成
     *
     * @param documentId 文档ID
     * @param fromVersionNo 对比源版本号（旧版本）
     * @param toVersionNo 对比目标版本号（新版本）
     * @return 版本对比VO，封装源版本与目标版本完整快照数据
     */
    public VersionCompareVO compare(Long documentId, Long fromVersionNo, Long toVersionNo) {
        // 校验文档存在 + 用户具备空间成员可读权限
        checkReadable(documentId);
        // 根据【对比源版本号】查询旧版本信息
        DocumentVersionDetailVO from = requireVersion(documentId, fromVersionNo).toDetailVO();
        // 根据【对比目标版本号】查询新版本信息
        DocumentVersionDetailVO to = requireVersion(documentId, toVersionNo).toDetailVO();
        return new VersionCompareVO(from, to);
    }

    /**
     * 读权限校验：校验文档存在，且当前登录用户是该文档所属空间的成员
     * 说明：版本是文档的子资源，所有版本查询接口必须先过此校验
     *
     * @param documentId 文档ID
     */
    private void checkReadable(Long documentId) {
        DocumentEntity doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        // 校验用户属于该空间成员
        permissionService.requireMember(doc.getSpaceId());
    }

    /**
     * 根据文档ID + 版本号获取版本实体，不存在抛出404业务异常
     *
     * @param documentId 文档ID
     * @param versionNo 版本号
     * @return 文档版本数据库实体
     */
    public DocumentVersionEntity requireVersion(Long documentId, Long versionNo) {
        DocumentVersionEntity version = versionMapper.selectOne(new LambdaQueryWrapper<DocumentVersionEntity>()
                .eq(DocumentVersionEntity::getDocumentId, documentId)
                .eq(DocumentVersionEntity::getVersionNo, versionNo));
        if (version == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "版本不存在");
        }
        return version;
    }
}
