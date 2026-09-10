package com.agentdoc.agent.service;

import com.agentdoc.agent.constant.McpConstant;
import com.agentdoc.agent.enums.McpAuthType;
import com.agentdoc.agent.enums.McpServerStatus;
import com.agentdoc.agent.mapper.McpTemplateMapper;
import com.agentdoc.agent.pojo.dto.McpServerCreateDTO;
import com.agentdoc.agent.pojo.dto.McpTemplateCreateDTO;
import com.agentdoc.agent.pojo.dto.McpTemplateInstallDTO;
import com.agentdoc.agent.pojo.dto.McpTemplateUpdateDTO;
import com.agentdoc.agent.pojo.entity.McpTemplateEntity;
import com.agentdoc.agent.pojo.param.McpTemplateSearchParam;
import com.agentdoc.agent.pojo.vo.McpServerVO;
import com.agentdoc.agent.pojo.vo.McpTemplateVO;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;

/** 系统 MCP 模板及空间安装服务。模板只保存公开连接元数据，不保存凭证。 */
@Service
@RequiredArgsConstructor
public class McpTemplateService {
    private final McpTemplateMapper mapper;
    private final PlatformAccessService platformAccessService;
    private final McpEndpointSecurityValidator endpointValidator;
    private final McpServerService mcpServerService;
    private final SkillAuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;

    public McpTemplateVO create(McpTemplateCreateDTO dto) {
        requireManage();
        validateAuthConfig(dto.authType(), dto.authParamName());
        endpointValidator.validateExternal(dto.endpointUrl());
        return transactionTemplate.execute(status -> createLocked(dto));
    }

    private McpTemplateVO createLocked(McpTemplateCreateDTO dto) {
        McpTemplateEntity entity = new McpTemplateEntity();
        entity.setServerKey(dto.serverKey());
        entity.setDisplayName(dto.displayName());
        entity.setDescription(dto.description());
        entity.setEndpointUrl(dto.endpointUrl());
        entity.setAuthType(dto.authType().name());
        entity.setAuthParamName(dto.authType() == McpAuthType.QUERY_PARAM ? dto.authParamName() : null);
        entity.setConfigVersion(McpConstant.INITIAL_CONFIG_VERSION);
        entity.setStatus(McpServerStatus.ENABLED.getCode());
        entity.setCreatedBy(AuthUtils.getUserIdOrException());
        try {
            mapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "系统 MCP serverKey 已存在");
        }
        auditLogService.record(null, "MCP_TEMPLATE_CREATED", "mcp_template", entity.getId(),
                Map.of("serverKey", entity.getServerKey()));
        return toVO(entity);
    }

    public PageVO<McpTemplateVO> search(McpTemplateSearchParam param) {
        param.validate();
        boolean manager = platformAccessService.hasRole(SUPER_ADMIN);
        LambdaQueryWrapper<McpTemplateEntity> query = new LambdaQueryWrapper<McpTemplateEntity>()
                .orderByDesc(McpTemplateEntity::getUpdatedAt).orderByDesc(McpTemplateEntity::getId);
        if (!manager) {
            query.eq(McpTemplateEntity::getStatus, McpServerStatus.ENABLED.getCode());
        } else if (param.getStatus() != null) {
            query.eq(McpTemplateEntity::getStatus, param.getStatus());
        }
        if (param.getAuthType() != null) {
            query.eq(McpTemplateEntity::getAuthType, param.getAuthType().name());
        }
        if (StringUtils.isNotBlank(param.getKeyword())) {
            String keyword = param.getKeyword().trim();
            query.and(value -> value.like(McpTemplateEntity::getServerKey, keyword)
                    .or().like(McpTemplateEntity::getDisplayName, keyword)
                    .or().like(McpTemplateEntity::getDescription, keyword));
        }
        Page<McpTemplateEntity> page = mapper.selectPage(PageUtils.toPage(param), query);
        return PageVO.of(page.getRecords().stream().map(this::toVO).toList(), page.getTotal(), param);
    }

    public McpTemplateVO detail(Long id) {
        McpTemplateEntity entity = require(id);
        if (!platformAccessService.hasRole(SUPER_ADMIN)
                && !McpServerStatus.ENABLED.matches(entity.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "系统 MCP 模板不存在");
        }
        return toVO(entity);
    }

    public McpTemplateVO update(Long id, McpTemplateUpdateDTO dto) {
        requireManage();
        validateAuthConfig(dto.authType(), dto.authParamName());
        endpointValidator.validateExternal(dto.endpointUrl());
        return transactionTemplate.execute(status -> updateLocked(id, dto));
    }

    private McpTemplateVO updateLocked(Long id, McpTemplateUpdateDTO dto) {
        McpTemplateEntity entity = mapper.selectOne(new LambdaQueryWrapper<McpTemplateEntity>()
                .eq(McpTemplateEntity::getId, id).last("FOR UPDATE"));
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "系统 MCP 模板不存在");
        }
        String authParamName = dto.authType() == McpAuthType.QUERY_PARAM ? dto.authParamName() : null;
        boolean connectionConfigChanged = !Objects.equals(entity.getEndpointUrl(), dto.endpointUrl())
                || !Objects.equals(entity.getAuthType(), dto.authType().name())
                || !Objects.equals(entity.getAuthParamName(), authParamName);
        boolean installConfigChanged = connectionConfigChanged
                || !Objects.equals(entity.getDisplayName(), dto.displayName());
        entity.setDisplayName(dto.displayName());
        entity.setDescription(dto.description());
        entity.setEndpointUrl(dto.endpointUrl());
        entity.setAuthType(dto.authType().name());
        entity.setAuthParamName(authParamName);
        if (installConfigChanged) {
            entity.setConfigVersion(entity.getConfigVersion() + McpConstant.CONFIG_VERSION_INCREMENT);
        }
        entity.setStatus(dto.status());
        if (connectionConfigChanged) {
            entity.setDiscoveredToolCount(null);
            entity.setDiscoveredToolsJson(null);
            entity.setToolsDiscoveredAt(null);
        }
        mapper.updateById(entity);
        auditLogService.record(null, "MCP_TEMPLATE_UPDATED", "mcp_template", entity.getId(),
                Map.of("configVersion", entity.getConfigVersion(), "status", entity.getStatus()));
        return toVO(entity);
    }

    /** 安装为空间独立连接，并复用空间 MCP 的连接测试与工具发现。 */
    public McpServerVO install(Long spaceId, McpTemplateInstallDTO dto) {
        McpTemplateEntity template = requireActive(dto.templateId());
        McpAuthType authType = McpAuthType.valueOf(template.getAuthType());
        McpServerCreateDTO create = new McpServerCreateDTO(spaceId, template.getServerKey(),
                template.getDisplayName(), template.getEndpointUrl(), authType,
                template.getAuthParamName(), dto.authToken());
        McpServerVO server = mcpServerService.createFromTemplate(create, template.getId(),
                template.getConfigVersion());
        mcpServerService.testConnection(server.id());
        auditLogService.record(spaceId, "MCP_TEMPLATE_INSTALLED", "mcp_server", server.id(),
                Map.of("templateId", template.getId(), "templateVersion", template.getConfigVersion()));
        return mcpServerService.managementDetail(server.id());
    }

    public McpTemplateEntity requireActive(Long id) {
        McpTemplateEntity entity = require(id);
        if (!McpServerStatus.ENABLED.matches(entity.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "系统 MCP 模板已停用");
        }
        return entity;
    }

    /** 批量校验 Agent 模板引用的 MCP 模板及配置版本。 */
    public Map<Long, McpTemplateEntity> requireVersions(Map<Long, Long> expectedVersions,
                                                         boolean requireActive) {
        if (expectedVersions.isEmpty()) {
            return Map.of();
        }
        Map<Long, McpTemplateEntity> templates = mapper.selectBatchIds(expectedVersions.keySet()).stream()
                .collect(Collectors.toMap(McpTemplateEntity::getId, Function.identity()));
        if (templates.size() != expectedVersions.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 模板引用的 MCP 模板不存在");
        }
        expectedVersions.forEach((id, version) -> {
            McpTemplateEntity entity = templates.get(id);
            if (!entity.getConfigVersion().equals(version)) {
                throw new BusinessException(ErrorCode.CONFLICT, "Agent 模板引用的 MCP 模板版本已不可用");
            }
            if (requireActive && !McpServerStatus.ENABLED.matches(entity.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "Agent 模板只能引用已启用的 MCP 模板");
            }
        });
        return Map.copyOf(templates);
    }

    private McpTemplateEntity require(Long id) {
        McpTemplateEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "系统 MCP 模板不存在");
        }
        return entity;
    }

    private McpTemplateVO toVO(McpTemplateEntity entity) {
        return new McpTemplateVO(entity.getId(), entity.getServerKey(), entity.getDisplayName(),
                entity.getDescription(), entity.getEndpointUrl(), McpAuthType.valueOf(entity.getAuthType()),
                entity.getAuthParamName(), entity.getConfigVersion(), entity.getStatus(),
                entity.getDiscoveredToolCount(), entity.getToolsDiscoveredAt(), entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private void validateAuthConfig(McpAuthType type, String authParamName) {
        if (type == McpAuthType.QUERY_PARAM && StringUtils.isBlank(authParamName)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "QUERY_PARAM 认证必须提供 query 参数名");
        }
    }

    private void requireManage() {
        platformAccessService.requireRole(SUPER_ADMIN);
    }
}
