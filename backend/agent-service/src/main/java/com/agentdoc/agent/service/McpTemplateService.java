package com.agentdoc.agent.service;

import com.agentdoc.agent.enums.McpAuthType;
import com.agentdoc.agent.enums.McpServerStatus;
import com.agentdoc.agent.enums.SkillVersionStatus;
import com.agentdoc.agent.mapper.McpTemplateMapper;
import com.agentdoc.agent.mapper.McpTemplateVersionMapper;
import com.agentdoc.agent.pojo.dto.McpServerCreateDTO;
import com.agentdoc.agent.pojo.dto.McpTemplateCreateDTO;
import com.agentdoc.agent.pojo.dto.McpTemplateInstallDTO;
import com.agentdoc.agent.pojo.dto.McpTemplateUpdateDTO;
import com.agentdoc.agent.pojo.dto.McpTemplateVersionCreateDTO;
import com.agentdoc.agent.pojo.entity.McpTemplateEntity;
import com.agentdoc.agent.pojo.entity.McpTemplateVersionEntity;
import com.agentdoc.agent.pojo.param.McpTemplateSearchParam;
import com.agentdoc.agent.pojo.vo.McpServerVO;
import com.agentdoc.agent.pojo.vo.McpTemplateVO;
import com.agentdoc.agent.pojo.vo.McpTemplateVersionVO;
import com.agentdoc.agent.security.McpEndpointSecurityValidator;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.PageUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;

/**
 * 系统 MCP 模板、不可变版本及空间安装服务。
 * <p>
 * 模板主体保存稳定身份和目录元数据，版本保存不可变连接配置；凭证由空间实例（McpServer）单独持有。
 * 提供模板的增删改查、版本管理、空间安装、Agent引用版本校验能力。
 * 模板安装到空间时，会基于模板元数据创建独立空间MCP连接，并执行连接测试。
 */
@Service
@RequiredArgsConstructor
public class McpTemplateService {
    private final McpTemplateMapper mapper;
    private final McpTemplateVersionMapper versionMapper;
    private final PlatformAccessService platformAccessService;
    private final McpEndpointSecurityValidator endpointValidator;
    private final McpServerService mcpServerService;
    private final SkillAuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建系统MCP模板
     * <p>
     * 权限：仅超级管理员
     * 模板主体不包含连接配置；数据库事务内执行落库，serverKey 唯一防重复。
     *
     * @param dto 创建参数
     * @return 创建完成的模板VO
     */
    public McpTemplateVO create(McpTemplateCreateDTO dto) {
        requireManage();
        return transactionTemplate.execute(status -> createLocked(dto));
    }

    /**
     * 事务内创建模板核心逻辑
     * <p>
     * 构建实体，插入数据库；捕获唯一索引冲突抛出业务异常；记录审计日志
     *
     * @param dto 创建参数
     * @return 模板VO
     */
    private McpTemplateVO createLocked(McpTemplateCreateDTO dto) {
        McpTemplateEntity entity = new McpTemplateEntity();
        entity.setServerKey(dto.serverKey());
        entity.setDisplayName(dto.displayName());
        entity.setDescription(dto.description());
        entity.setStatus(McpServerStatus.ENABLED.getCode());
        entity.setNextVersionNo(1);
        entity.setCreatedBy(AuthUtils.getUserIdOrException());
        try {
            mapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "系统 MCP serverKey 已存在");
        }
        // 审计日志：记录模板创建事件，携带serverKey
        auditLogService.record(null, "MCP_TEMPLATE_CREATED", "mcp_template", entity.getId(),
                Map.of("serverKey", entity.getServerKey()));
        return toVO(entity, null);
    }

    /**
     * 分页查询MCP模板列表
     * <p>
     * 权限控制：普通用户仅能查询【已启用且存在已发布版本】的模板；超级管理员可按状态筛选全部模板
     * 支持关键词模糊搜索：serverKey / 展示名 / 描述；按更新时间、ID倒序
     *
     * @param param 分页&筛选参数
     * @return 分页VO
     */
    public PageVO<McpTemplateVO> search(McpTemplateSearchParam param) {
        param.validate();
        boolean manager = platformAccessService.hasRole(SUPER_ADMIN);
        LambdaQueryWrapper<McpTemplateEntity> query = new LambdaQueryWrapper<McpTemplateEntity>()
                .orderByDesc(McpTemplateEntity::getUpdatedAt).orderByDesc(McpTemplateEntity::getId);
        // 非管理员只能查启用状态模板
        if (!manager) {
            query.eq(McpTemplateEntity::getStatus, McpServerStatus.ENABLED.getCode())
                    .inSql(McpTemplateEntity::getId,
                            "SELECT DISTINCT template_id FROM mcp_template_version WHERE status = "
                                    + SkillVersionStatus.PUBLISHED.getCode());
        } else if (param.getStatus() != null) {
            // 管理员支持按状态筛选
            query.eq(McpTemplateEntity::getStatus, param.getStatus());
        }
        // 认证类型过滤
        if (param.getAuthType() != null) {
            query.inSql(McpTemplateEntity::getId,
                    "SELECT mcpv.template_id FROM mcp_template_version mcpv "
                            + "WHERE mcpv.status = " + SkillVersionStatus.PUBLISHED.getCode()
                            + " AND mcpv.auth_type = '" + param.getAuthType().name() + "'"
                            + " AND mcpv.version_no = (SELECT MAX(latest.version_no) "
                            + "FROM mcp_template_version latest WHERE latest.template_id = mcpv.template_id "
                            + "AND latest.status = " + SkillVersionStatus.PUBLISHED.getCode() + ")");
        }
        // 关键词多字段模糊查询
        if (StringUtils.isNotBlank(param.getKeyword())) {
            String keyword = param.getKeyword().trim();
            query.and(value -> value.like(McpTemplateEntity::getServerKey, keyword)
                    .or().like(McpTemplateEntity::getDisplayName, keyword)
                    .or().like(McpTemplateEntity::getDescription, keyword));
        }
        Page<McpTemplateEntity> page = mapper.selectPage(PageUtils.toPage(param), query);
        Map<Long, McpTemplateVersionEntity> latestVersions = latestPublishedVersions(page.getRecords().stream()
                .map(McpTemplateEntity::getId).toList());
        return PageVO.of(page.getRecords().stream()
                        .map(entity -> toVO(entity, latestVersions.get(entity.getId()))).toList(),
                page.getTotal(), param);
    }

    /**
     * 获取MCP模板详情
     * <p>
     * 权限：非管理员只能查看已启用模板；不存在/无权则统一返回不存在，避免信息泄露
     *
     * @param id 模板主键ID
     * @return 模板详情VO
     */
    public McpTemplateVO detail(Long id) {
        McpTemplateEntity entity = require(id);
        McpTemplateVersionEntity latest = latestPublishedVersion(id);
        if (!platformAccessService.hasRole(SUPER_ADMIN)
                && (!McpServerStatus.ENABLED.matches(entity.getStatus()) || latest == null)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "系统 MCP 模板不存在");
        }
        return toVO(entity, latest);
    }

    /**
     * 更新系统MCP模板
     * <p>
     * 权限：仅超级管理员；这里只更新目录信息，不改变任何已发布版本。
     *
     * @param id 模板主键ID
     * @param dto 更新参数
     * @return 更新后模板VO
     */
    public McpTemplateVO update(Long id, McpTemplateUpdateDTO dto) {
        requireManage();
        return transactionTemplate.execute(status -> updateLocked(id, dto));
    }

    /**
     * 事务内更新模板核心逻辑（行锁FOR UPDATE）
     * <p>
     * 更新模板展示信息与启停状态，不覆盖版本快照。
     *
     * @param id 模板主键ID
     * @param dto 更新参数
     * @return 更新后模板VO
     */
    private McpTemplateVO updateLocked(Long id, McpTemplateUpdateDTO dto) {
        // 行锁，防止并发更新
        McpTemplateEntity entity = mapper.selectOne(new LambdaQueryWrapper<McpTemplateEntity>()
                .eq(McpTemplateEntity::getId, id).last("FOR UPDATE"));
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "系统 MCP 模板不存在");
        }
        entity.setDisplayName(dto.displayName());
        entity.setDescription(dto.description());
        entity.setStatus(dto.status());
        mapper.updateById(entity);
        auditLogService.record(null, "MCP_TEMPLATE_UPDATED", "mcp_template", entity.getId(),
                Map.of("status", entity.getStatus()));
        return toVO(entity, latestPublishedVersion(id));
    }

    /** 创建不可变的 MCP 模板草稿版本，版本号在模板行锁内分配。 */
    @Transactional(rollbackFor = Exception.class)
    public McpTemplateVersionVO createVersion(Long templateId, McpTemplateVersionCreateDTO dto) {
        requireManage();
        validateVersionConfig(dto.endpointUrl(), dto.authType(), dto.authParamName());
        McpTemplateEntity template = mapper.selectOne(new LambdaQueryWrapper<McpTemplateEntity>()
                .eq(McpTemplateEntity::getId, templateId).last("FOR UPDATE"));
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "系统 MCP 模板不存在");
        }
        McpTemplateVersionEntity version = new McpTemplateVersionEntity();
        version.setTemplateId(templateId);
        version.setVersionNo(template.getNextVersionNo());
        version.setStatus(SkillVersionStatus.DRAFT.getCode());
        version.setDisplayName(dto.displayName());
        version.setEndpointUrl(dto.endpointUrl());
        version.setAuthType(dto.authType().name());
        version.setAuthParamName(dto.authType() == McpAuthType.QUERY_PARAM ? dto.authParamName() : null);
        version.setCreatedBy(AuthUtils.getUserIdOrException());
        versionMapper.insert(version);
        template.setNextVersionNo(template.getNextVersionNo() + 1);
        mapper.updateById(template);
        auditLogService.record(null, "MCP_TEMPLATE_VERSION_CREATED", "mcp_template_version", version.getId(),
                Map.of("templateId", templateId, "versionNo", version.getVersionNo()));
        return toVersionVO(version);
    }

    /** 发布 MCP 模板草稿版本；已发布版本不再修改。 */
    @Transactional(rollbackFor = Exception.class)
    public McpTemplateVersionVO publish(Long versionId) {
        requireManage();
        McpTemplateVersionEntity version = requireVersion(versionId);
        if (!SkillVersionStatus.DRAFT.matches(version.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有草稿 MCP 模板版本可以发布");
        }
        McpTemplateEntity template = require(version.getTemplateId());
        if (!McpServerStatus.ENABLED.matches(template.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已停用 MCP 模板不能发布版本");
        }
        validateVersionConfig(version.getEndpointUrl(), McpAuthType.valueOf(version.getAuthType()),
                version.getAuthParamName());
        version.setStatus(SkillVersionStatus.PUBLISHED.getCode());
        version.setPublishedBy(AuthUtils.getUserIdOrException());
        version.setPublishedAt(LocalDateTime.now());
        versionMapper.updateById(version);
        auditLogService.record(null, "MCP_TEMPLATE_VERSION_PUBLISHED", "mcp_template_version", version.getId(),
                Map.of("templateId", version.getTemplateId(), "versionNo", version.getVersionNo()));
        return toVersionVO(version);
    }

    /** 普通用户只读取已发布版本，平台管理员可同时读取草稿。 */
    public List<McpTemplateVersionVO> listVersions(Long templateId) {
        McpTemplateEntity template = require(templateId);
        boolean manager = platformAccessService.hasRole(SUPER_ADMIN);
        if (!manager && !McpServerStatus.ENABLED.matches(template.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "系统 MCP 模板不存在");
        }
        LambdaQueryWrapper<McpTemplateVersionEntity> query =
                new LambdaQueryWrapper<McpTemplateVersionEntity>()
                        .eq(McpTemplateVersionEntity::getTemplateId, templateId)
                        .orderByDesc(McpTemplateVersionEntity::getVersionNo);
        if (!manager) {
            query.eq(McpTemplateVersionEntity::getStatus, SkillVersionStatus.PUBLISHED.getCode());
        }
        return versionMapper.selectList(query).stream().map(this::toVersionVO).toList();
    }

    /**
     * 将系统MCP模板安装到指定空间，生成空间独立MCP连接实例
     * <p>
     * 复用McpServerService的连接创建、连接测试、工具发现逻辑；
     * 模板仅存元数据，凭证由安装时传入，保存在空间MCP实例中。
     *
     * @param spaceId 空间ID
     * @param dto 模板安装入参（携带用户凭证authToken）
     * @return 空间MCP服务实例VO
     */
    public McpServerVO install(Long spaceId, McpTemplateInstallDTO dto) {
        McpTemplateVersionEntity version = requirePublishedVersion(dto.templateVersionId(), true);
        McpTemplateEntity template = require(version.getTemplateId());
        McpAuthType authType = McpAuthType.valueOf(version.getAuthType());
        // 基于模板元数据 + 用户传入凭证，构造McpServer创建DTO
        McpServerCreateDTO create = new McpServerCreateDTO(spaceId, template.getServerKey(),
                version.getDisplayName(), version.getEndpointUrl(), authType,
                version.getAuthParamName(), dto.authToken());
        // 创建空间MCP实例，关联模板ID与模板版本
        McpServerVO server = mcpServerService.createFromTemplate(create, template.getId(), version.getId());
        // 执行连接连通性测试
        mcpServerService.testConnection(server.id());
        // 记录模板安装审计日志
        auditLogService.record(spaceId, "MCP_TEMPLATE_INSTALLED", "mcp_server", server.id(),
                Map.of("templateId", template.getId(), "templateVersionId", version.getId()));
        return mcpServerService.managementDetail(server.id());
    }

    /** 批量校验 Agent 模板引用的真实 MCP 模板版本。 */
    public Map<Long, McpTemplateVersionEntity> requirePublishedVersions(Collection<Long> versionIds,
                                                                         boolean requireActive) {
        if (versionIds == null || versionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, McpTemplateVersionEntity> versions = versionMapper.selectBatchIds(versionIds).stream()
                .collect(Collectors.toMap(McpTemplateVersionEntity::getId, Function.identity()));
        if (versions.size() != versionIds.stream().distinct().count()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 模板引用的 MCP 模板版本不存在");
        }
        Map<Long, McpTemplateEntity> templates = mapper.selectBatchIds(versions.values().stream()
                        .map(McpTemplateVersionEntity::getTemplateId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(McpTemplateEntity::getId, Function.identity()));
        for (McpTemplateVersionEntity version : versions.values()) {
            McpTemplateEntity template = templates.get(version.getTemplateId());
            if (template == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 模板引用的 MCP 模板不存在");
            }
            if (!SkillVersionStatus.PUBLISHED.matches(version.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "Agent 模板只能引用已发布的 MCP 模板版本");
            }
            if (requireActive && !McpServerStatus.ENABLED.matches(template.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "Agent 模板只能引用已启用的 MCP 模板");
            }
        }
        return Map.copyOf(versions);
    }

    private McpTemplateVersionEntity requirePublishedVersion(Long id, boolean requireActive) {
        McpTemplateVersionEntity version = requireVersion(id);
        requirePublishedVersions(List.of(id), requireActive);
        return version;
    }

    /**
     * 根据ID获取模板实体，不存在抛业务异常
     *
     * @param id 模板主键ID
     * @return 模板实体
     */
    private McpTemplateEntity require(Long id) {
        McpTemplateEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "系统 MCP 模板不存在");
        }
        return entity;
    }

    private McpTemplateVersionEntity requireVersion(Long id) {
        McpTemplateVersionEntity entity = versionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "MCP 模板版本不存在");
        }
        return entity;
    }

    private Map<Long, McpTemplateVersionEntity> latestPublishedVersions(Collection<Long> templateIds) {
        if (templateIds.isEmpty()) {
            return Map.of();
        }
        return versionMapper.selectList(new LambdaQueryWrapper<McpTemplateVersionEntity>()
                        .in(McpTemplateVersionEntity::getTemplateId, templateIds)
                        .eq(McpTemplateVersionEntity::getStatus, SkillVersionStatus.PUBLISHED.getCode()))
                .stream().collect(Collectors.toMap(McpTemplateVersionEntity::getTemplateId, Function.identity(),
                        (left, right) -> left.getVersionNo() >= right.getVersionNo() ? left : right));
    }

    private McpTemplateVersionEntity latestPublishedVersion(Long templateId) {
        return versionMapper.selectOne(new LambdaQueryWrapper<McpTemplateVersionEntity>()
                .eq(McpTemplateVersionEntity::getTemplateId, templateId)
                .eq(McpTemplateVersionEntity::getStatus, SkillVersionStatus.PUBLISHED.getCode())
                .orderByDesc(McpTemplateVersionEntity::getVersionNo).last("LIMIT 1"));
    }

    private McpTemplateVO toVO(McpTemplateEntity entity, McpTemplateVersionEntity latest) {
        return new McpTemplateVO(entity.getId(), entity.getServerKey(), entity.getDisplayName(),
                entity.getDescription(), entity.getStatus(), latest == null ? null : latest.getId(),
                latest == null ? null : latest.getVersionNo(), entity.getDiscoveredToolCount(),
                entity.getToolsDiscoveredAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private McpTemplateVersionVO toVersionVO(McpTemplateVersionEntity entity) {
        return new McpTemplateVersionVO(entity.getId(), entity.getTemplateId(), entity.getVersionNo(),
                entity.getStatus(), entity.getDisplayName(), entity.getEndpointUrl(),
                McpAuthType.valueOf(entity.getAuthType()), entity.getAuthParamName(), entity.getCreatedBy(),
                entity.getPublishedBy(), entity.getPublishedAt(), entity.getCreatedAt());
    }

    private void validateVersionConfig(String endpointUrl, McpAuthType type, String authParamName) {
        endpointValidator.validateExternal(endpointUrl);
        if (type == McpAuthType.QUERY_PARAM && StringUtils.isBlank(authParamName)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "QUERY_PARAM 认证必须提供 query 参数名");
        }
    }

    /**
     * 权限校验：要求超级管理员角色
     */
    private void requireManage() {
        platformAccessService.requireRole(SUPER_ADMIN);
    }
}
