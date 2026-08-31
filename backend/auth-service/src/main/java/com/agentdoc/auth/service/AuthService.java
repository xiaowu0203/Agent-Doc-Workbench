package com.agentdoc.auth.service;

import com.agentdoc.auth.enums.UserStatus;
import com.agentdoc.auth.pojo.dto.RegisterRequestDTO;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.pojo.vo.AuthResponseVO;
import com.agentdoc.auth.pojo.vo.UserVO;
import com.agentdoc.auth.mapper.UserMapper;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.TaskCapabilityIssueDTO;
import com.agentdoc.common.utils.AuthUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务：注册、登录、刷新、登出。
 */
@Slf4j
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PlatformRoleService platformRoleService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokenService,
                       PlatformRoleService platformRoleService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.platformRoleService = platformRoleService;
    }

    /**
     * 用户注册
     * @param request 注册请求参数：用户名、密码、昵称、邮箱
     * @return UserVO 用户信息VO
     * @throws BusinessException 用户名已存在抛出业务异常
     */
    @Transactional
    public UserVO register(RegisterRequestDTO request) {
        // 查询用户名是否已经存在
        Long existing = userMapper.selectCount(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, request.username()));
        // 如果用户名已存在，抛出异常
        if (existing != null && existing > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        // 请求转实体：字段搬运统一收敛在实体类，此处仅准备密码哈希
        UserEntity user = request.toEntity(passwordEncoder.encode(request.password()));
        // 落库
        userMapper.insert(user);
        return user.toVO();
    }

    /**
     * 账号登录
     * @param username 用户名
     * @param password 明文密码
     * @return AuthResponseVO 返回 accessToken、refreshToken、用户信息
     * @throws BusinessException 账号密码错误 / 账号禁用抛出异常
     */
    public AuthResponseVO login(String username, String password) {
        // 根据用户名查询用户
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username));
        // 用户不存在，抛出异常；日志不记录用户名，避免泄露登录标识
        if (user == null) {
            log.warn("登录失败：用户不存在");
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        // 密码校验失败，抛出异常；禁止记录明文密码或密码哈希
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("登录失败：密码校验未通过，userId={}", user.getId());
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        // 账号禁用，抛出异常
        if (!UserStatus.isEnabled(user.getStatus())) {
            log.warn("登录失败：账号已禁用，userId={}", user.getId());
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        // 下发全新一对令牌
        log.info("登录成功，userId={}", user.getId());
        return issueTokens(user);
    }

    /**
     * 使用refreshToken刷新获取新的访问令牌，令牌轮换模式
     * @param refreshToken 旧的刷新令牌
     * @return AuthResponseVO 返回全新 accessToken + refreshToken
     * @throws BusinessException refreshToken无效、用户不存在、账号禁用抛出异常
     */
    public AuthResponseVO refresh(String refreshToken) {
        // 校验refreshToken，拿到绑定的用户ID
        Long userId = refreshTokenService.validateAndGetUserId(refreshToken);
        if (userId == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        UserEntity user = userMapper.selectById(userId);
        // 用户已删除或者账号被禁用，拒绝刷新
        if (user == null || !UserStatus.isEnabled(user.getStatus())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        // 令牌轮换：撤销当前旧refreshToken，防止令牌复用
        refreshTokenService.revoke(refreshToken);
        // 下发全新一对令牌
        return issueTokens(user);
    }

    /**
     * 用户登出，撤销refreshToken
     * @param refreshToken 当前有效的刷新令牌
     */
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
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
        if (request == null
                || request.taskId() == null
                || request.agentId() == null
                || request.spaceId() == null
                || request.actions() == null
                || request.actions().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务能力令牌参数无效");
        }
        return jwtService.createTaskCapabilityToken(request.taskId(), request.agentId(),
                request.spaceId(), request.documentId(), request.actions());
    }

    /**
     * 签发令牌对：生成accessToken JWT，生成并存储refreshToken
     * @param user 用户数据库实体
     * @return AuthResponseVO 令牌响应对象
     */
    private AuthResponseVO issueTokens(UserEntity user) {
        // 生成短期访问JWT
        String accessToken = jwtService.createAccessToken(
                user, platformRoleService.listRoleKeys(user.getId()));
        // 生成refreshToken字符串
        String refreshToken = jwtService.createRefreshToken();
        // 将refreshToken存入服务端存储（Redis）
        refreshTokenService.store(refreshToken, user.getId());
        return AuthResponseVO.of(
                accessToken, refreshToken, jwtService.props().accessTtl().toSeconds(), user.toVO());
    }
}
