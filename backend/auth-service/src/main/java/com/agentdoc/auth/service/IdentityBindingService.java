package com.agentdoc.auth.service;

import com.agentdoc.auth.config.IdentityBindingProperties;
import com.agentdoc.auth.constant.IdentityBindingConstant;
import com.agentdoc.auth.mapper.IdentityBindingMapper;
import com.agentdoc.auth.pojo.entity.IdentityBindingEntity;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.IdentityKeyUtils;
import com.agentdoc.common.utils.AuthUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;

/** 外部身份绑定与本地用户解析；调用方必须先完成令牌验证和操作权限检查。 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(IdentityBindingProperties.class)
public class IdentityBindingService {
    private final IdentityBindingMapper mapper;
    private final PlatformUserService users;
    private final IdentityBindingProperties properties;
    private final PlatformAuditLogService audit;

    /** 首次绑定既有启用用户；任何已存在摘要（包括已撤销记录）均拒绝重绑。 */
    @Transactional
    @PreAuthorize("@platformRoleService.hasCurrentUserRole('" + SUPER_ADMIN + "')")
    public void bind(String issuer, String subject, Long userId) {
        byte[] key = validate(issuer, subject);
        if (userId == null) { throw new BusinessException(ErrorCode.BAD_REQUEST); }
        Long operatorId = AuthUtils.getUserIdOrException();
        users.requireEnabledUser(userId);
        if (find(key) != null) { throw new BusinessException(ErrorCode.CONFLICT, "身份已绑定或已撤销"); }
        var binding = new IdentityBindingEntity();
        binding.setIdentityKey(key);
        binding.setIssuer(issuer);
        binding.setSubject(subject);
        binding.setUserId(userId);
        binding.setStatus(IdentityBindingConstant.ACTIVE);
        binding.setBoundBy(operatorId);
        binding.setBoundAt(LocalDateTime.now());
        try {
            mapper.insert(binding);
        } catch (DuplicateKeyException error) {
            throw new BusinessException(ErrorCode.CONFLICT, "身份已绑定或已撤销");
        }
        audit.record(IdentityBindingConstant.AUDIT_BOUND, IdentityBindingConstant.AUDIT_TARGET, binding.getId(),
                Map.of("userId", userId));
    }

    /** 精确校验原字段、状态及本地用户状态；不按邮箱或用户名自动匹配。 */
    public Long resolve(String issuer, String subject) {
        var binding = find(validate(issuer, subject));
        if (binding == null || !IdentityBindingConstant.ACTIVE.equals(binding.getStatus())
                || !issuer.equals(binding.getIssuer()) || !subject.equals(binding.getSubject())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        users.requireEnabledUser(binding.getUserId());
        return binding.getUserId();
    }

    /** 撤销保留历史及唯一键，不自动重新绑定。 */
    @Transactional
    @PreAuthorize("@platformRoleService.hasCurrentUserRole('" + SUPER_ADMIN + "')")
    public void revoke(String issuer, String subject, Long userId) {
        byte[] key = validate(issuer, subject);
        Long operatorId = AuthUtils.getUserIdOrException();
        var binding = find(key);
        if (binding == null || !IdentityBindingConstant.ACTIVE.equals(binding.getStatus())
                || !issuer.equals(binding.getIssuer()) || !subject.equals(binding.getSubject())
                || !binding.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "身份不存在或状态已改变");
        }
        int changed = mapper.update(null, new LambdaUpdateWrapper<IdentityBindingEntity>()
                .eq(IdentityBindingEntity::getId, binding.getId())
                .eq(IdentityBindingEntity::getStatus, IdentityBindingConstant.ACTIVE)
                .set(IdentityBindingEntity::getStatus, IdentityBindingConstant.REVOKED)
                .set(IdentityBindingEntity::getRevokedBy, operatorId)
                .set(IdentityBindingEntity::getRevokedAt, LocalDateTime.now()));
        if (changed != 1) { throw new BusinessException(ErrorCode.CONFLICT, "身份状态已改变"); }
        audit.record(IdentityBindingConstant.AUDIT_REVOKED, IdentityBindingConstant.AUDIT_TARGET, binding.getId(),
                Map.of("userId", userId));
    }

    private IdentityBindingEntity find(byte[] key) {
        return mapper.selectOne(new LambdaQueryWrapper<IdentityBindingEntity>().eq(IdentityBindingEntity::getIdentityKey, key));
    }

    private byte[] validate(String issuer, String subject) {
        if (properties.getIssuer() == null || properties.getIssuer().isBlank()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "尚未配置可信issuer");
        }
        if (issuer == null || !properties.getIssuer().equals(issuer) || subject == null || subject.isBlank()
                || issuer.codePointCount(0, issuer.length()) > IdentityBindingConstant.ISSUER_MAX_LENGTH
                || subject.codePointCount(0, subject.length()) > IdentityBindingConstant.SUBJECT_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        try {
            return IdentityKeyUtils.identityKey(issuer, subject);
        } catch (IllegalArgumentException error) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }
}
