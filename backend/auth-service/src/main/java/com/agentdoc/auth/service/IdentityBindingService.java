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

/**
 * 外部身份绑定与本地用户解析；调用方必须先完成令牌验证和操作权限检查。
 * <p>
 * OIDC外部身份绑定业务服务，维护外部IdP(issuer+subject)与平台内部userId的绑定关系；
 * 支持绑定、撤销、解析查询，绑定记录逻辑撤销（保留历史），同一条外部身份不可重复绑定。
 * 配合 {@link OidcHumanAuthenticationConverter} 完成OIDC登录时外部身份到平台用户的映射。
 * </p>
 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(IdentityBindingProperties.class)
public class IdentityBindingService {

    private final IdentityBindingMapper mapper;
    private final PlatformUserService users;
    private final IdentityBindingProperties properties;
    private final PlatformAuditLogService audit;

    /**
     * 绑定外部OIDC身份到平台用户
     * 规则：只绑定启用状态的本地用户；只要该外部身份摘要存在（ACTIVE / REVOKED）都拒绝再次绑定；
     * 幂等防重依靠唯一索引 identityKey，同时记录审计日志。
     * 仅超级管理员可执行绑定操作。
     * @param issuer OIDC身份提供商地址
     * @param subject IdP侧用户唯一标识 sub
     * @param userId 平台内部用户ID
     */
    @Transactional
    public void bind(String issuer, String subject, Long userId) {
        byte[] key = validate(issuer, subject);
        if (userId == null) { throw new BusinessException(ErrorCode.BAD_REQUEST); }
        Long operatorId = AuthUtils.getUserIdOrException();
        // 目标平台用户必须是启用状态
        users.requireEnabledUser(userId);
        // 只要该外部身份记录存在（含已撤销），禁止重绑
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
            // 兜底捕获数据库唯一键冲突，防止并发场景绕过上层查询校验
            throw new BusinessException(ErrorCode.CONFLICT, "身份已绑定或已撤销");
        }
        audit.record(IdentityBindingConstant.AUDIT_BOUND, IdentityBindingConstant.AUDIT_TARGET, binding.getId(),
                Map.of("userId", userId));
    }

    /**
     * 根据OIDC的iss + sub解析得到平台userId
     * 强校验：字段原样匹配、绑定状态为ACTIVE、平台用户启用；
     * 不做邮箱、用户名模糊自动匹配，严格依赖预先绑定记录。
     * @param issuer OIDC身份提供商地址
     * @param subject IdP侧用户唯一标识 sub
     * @return 平台内部userId
     */
    public Long resolve(String issuer, String subject) {
        var binding = find(validate(issuer, subject));
        if (binding == null || !IdentityBindingConstant.ACTIVE.equals(binding.getStatus())
                || !issuer.equals(binding.getIssuer()) || !subject.equals(binding.getSubject())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        users.requireEnabledUser(binding.getUserId());
        return binding.getUserId();
    }

    /**
     * 撤销一条身份绑定（逻辑删除）
     * 保留数据库历史记录、保留identityKey唯一键，撤销之后该外部身份不能再直接复用绑定；
     * 仅超级管理员可执行撤销操作。
     * @param issuer OIDC身份提供商地址
     * @param subject IdP侧用户唯一标识 sub
     * @param userId 预期绑定的平台用户ID，用于防误操作
     */
    @Transactional
    public void revoke(String issuer, String subject, Long userId) {
        byte[] key = validate(issuer, subject);
        Long operatorId = AuthUtils.getUserIdOrException();
        var binding = find(key);
        // 多重一致性校验：记录存在、状态ACTIVE、iss/sub/userId完全匹配
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
        // 行更新断言，防止并发下状态已被变更
        if (changed != 1) { throw new BusinessException(ErrorCode.CONFLICT, "身份状态已改变"); }
        audit.record(IdentityBindingConstant.AUDIT_REVOKED, IdentityBindingConstant.AUDIT_TARGET, binding.getId(),
                Map.of("userId", userId));
    }

    /**
     * 通过摘要key查询绑定记录
     */
    private IdentityBindingEntity find(byte[] key) {
        return mapper.selectOne(new LambdaQueryWrapper<IdentityBindingEntity>().eq(IdentityBindingEntity::getIdentityKey, key));
    }

    /**
     * 输入合法性校验：issuer必须和配置的可信issuer一致、长度校验，计算identityKey
     * 使用 {@link IdentityKeyUtils} 生成 iss+sub 的SHA256摘要
     */
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
