package com.agentdoc.auth.service;

import com.agentdoc.auth.enums.UserStatus;
import com.agentdoc.auth.mapper.UserMapper;
import com.agentdoc.auth.pojo.dto.ChangePasswordRequestDTO;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.pojo.vo.UserVO;
import com.agentdoc.auth.utils.LocalPasswordPolicy;
import com.agentdoc.common.config.WorkbenchAuthProperties;
import com.agentdoc.common.enums.AuthenticationMode;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.TaskCapabilityIssueDTO;
import com.agentdoc.common.feign.dto.EvaluationWorkerCapabilityIssueDTO;
import com.agentdoc.common.feign.dto.UserBatchQueryDTO;
import com.agentdoc.common.feign.vo.UserRefVO;
import com.agentdoc.common.utils.AuthUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.Map;

import static com.agentdoc.auth.constant.PlatformManagementConstant.USER_PASSWORD_CHANGED;
import static com.agentdoc.auth.constant.PlatformManagementConstant.USER_TARGET;

/**
 * 本地密码验证、改密及既有业务能力签发；不提供浏览器登录/Refresh协议。
 */
@Slf4j
@Service
public class AuthService {

    private static final long MIN_WORKER_CAPABILITY_TTL_SECONDS = 300L;
    private static final long MAX_WORKER_CAPABILITY_TTL_SECONDS = 86_400L;
    private static final Pattern BCRYPT_HASH = Pattern.compile("\\$2[aby]\\$(0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final HumanAuthenticationService humanAuthenticationService;
    private final WorkbenchAuthProperties authenticationProperties;
    private final String dummyPasswordHash;
    private final PlatformAuditLogService audit;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       JwtService jwtService, HumanAuthenticationService humanAuthenticationService,
                       WorkbenchAuthProperties authenticationProperties, PlatformAuditLogService audit) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.humanAuthenticationService = humanAuthenticationService;
        this.authenticationProperties = authenticationProperties;
        this.audit = audit;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** 仅验证local密码/状态并原子回填登录时间，不在这里签任何凭据。 */
    public UserEntity authenticateLocalUser(String username, String password) {
        requireLocalPasswordMode();
        if (!LocalPasswordPolicy.supported(password)) { throw new BusinessException(ErrorCode.LOGIN_FAILED); }
        // 根据用户名查询用户
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username));
        // 用户不存在，抛出异常；日志不记录用户名，避免泄露登录标识
        boolean localHash = user != null && user.getPasswordHash() != null && BCRYPT_HASH.matcher(user.getPasswordHash()).matches();
        boolean passwordMatches = passwordEncoder.matches(password, localHash ? user.getPasswordHash() : dummyPasswordHash);
        if (!passwordMatches || !localHash || !UserStatus.isEnabled(user.getStatus())) {
            log.warn("本地密码认证未通过");
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (user.getAuthVersion() == null || user.getAuthVersion() < 0) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        // 条件更新防止密码校验期间的禁用、改密或强退被过期实体覆盖。
        user.setLastLoginAt(LocalDateTime.now());
        int changed = userMapper.update(null, new LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getId, user.getId())
                .eq(UserEntity::getStatus, UserStatus.ENABLED.getCode())
                .eq(UserEntity::getPasswordHash, user.getPasswordHash())
                .eq(UserEntity::getAuthVersion, user.getAuthVersion())
                .set(UserEntity::getLastLoginAt, user.getLastLoginAt()));
        if (changed != 1) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        log.info("本地密码认证通过，userId={}", user.getId());
        return user;
    }

    /**
     * 修改当前用户密码并推进认证版本，要求重新登录。
     *
     * @param request 修改密码请求
     */
    @Transactional
    public void changePassword(ChangePasswordRequestDTO request) {
        requireLocalPasswordMode();
        LocalPasswordPolicy.requireSupported(request.currentPassword());
        LocalPasswordPolicy.requireSupported(request.newPassword());
        Long userId = AuthUtils.getUserIdOrException();
        UserEntity user = userMapper.lockById(userId);
        if (user == null || !UserStatus.isEnabled(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()
                || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_INVALID);
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(user);
        humanAuthenticationService.revoke(userId);
        audit.record(USER_PASSWORD_CHANGED, USER_TARGET, userId, Map.of());
    }

    private void requireLocalPasswordMode() {
        if (authenticationProperties.getMode() != AuthenticationMode.LOCAL) { throw new BusinessException(ErrorCode.FORBIDDEN); }
    }

    /**
     * 根据用户ID查询用户信息
     * @param userId 用户ID
     * @return UserVO 用户VO，不存在返回null
     */
    public UserVO getById(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        return user == null ? null : user.toVO();
    }

    /**
     * 批量查询用户最小展示信息，避免业务服务逐条查询用户。
     *
     * @param request 用户 ID 集合
     * @return 用户展示信息；不存在的用户不返回
     */
    public List<UserRefVO> queryUsers(UserBatchQueryDTO request) {
        if (request == null || request.userIds() == null || request.userIds().isEmpty()) {
            return List.of();
        }
        return userMapper.selectBatchIds(request.userIds()).stream()
                .map(user -> new UserRefVO(user.getId(), user.getUsername(), user.getNickname(),
                        UserStatus.isEnabled(user.getStatus())))
                .toList();
    }

    /**
     * 获取当前认证用户信息。
     */
    public UserVO currentUser() {
        UserVO user = getById(AuthUtils.getUserIdOrException());
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    /**
     * 校验任务能力参数并签发短时令牌。
     */
    public String issueTaskCapability(TaskCapabilityIssueDTO request) {
        if (request != null && request.onlineIdentity() != null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "线上执行凭证必须经 WAIT 与权威槽交换");
        }
        if (request == null
                || request.taskId() == null
                || request.agentId() == null
                || request.spaceId() == null
                || request.executionMode() == null
                || request.documentVersionSnapshot() == null
                || request.documentContentSha256() == null
                || request.inputSnapshotSchemaVersion() == null
                || request.inputSnapshotHash() == null
                || (request.derivationRequestHash() != null
                && !request.derivationRequestHash().matches("[0-9a-f]{64}"))
                || request.actions() == null
                || request.actions().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务能力令牌参数无效");
        }
        return jwtService.createTaskCapabilityToken(request.taskId(), request.agentId(),
                request.spaceId(), request.documentId(), request.executionMode(), request.documentVersionSnapshot(),
                request.documentContentSha256(), request.inputSnapshotSchemaVersion(), request.inputSnapshotHash(),
                request.derivationRequestHash(), request.actions(), null);
    }

    /**
     * 校验并签发 Evaluation Worker 窄权限令牌。
     */
    public String issueEvaluationWorkerCapability(EvaluationWorkerCapabilityIssueDTO request) {
        if (request == null || request.runId() == null || request.spaceId() == null
                || request.taskIdsHash() == null || request.taskIdsHash().length() != 64
                || request.ttlSeconds() == null
                || request.ttlSeconds() < MIN_WORKER_CAPABILITY_TTL_SECONDS
                || request.ttlSeconds() > MAX_WORKER_CAPABILITY_TTL_SECONDS
                || request.actions() == null || request.actions().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Evaluation Worker 能力令牌参数无效");
        }
        return jwtService.createEvaluationWorkerCapabilityToken(request.runId(), request.spaceId(),
                request.taskIdsHash(), request.ttlSeconds(), List.copyOf(request.actions()));
    }

}
