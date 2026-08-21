package com.agentdoc.auth.service;

import com.agentdoc.auth.dto.AuthResponse;
import com.agentdoc.auth.dto.RegisterRequest;
import com.agentdoc.auth.dto.UserDto;
import com.agentdoc.auth.entity.UserEntity;
import com.agentdoc.auth.mapper.UserMapper;
import com.agentdoc.auth.security.JwtService;
import com.agentdoc.common.api.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * 用户注册
     * @param request 注册请求参数：用户名、密码、昵称、邮箱
     * @return UserDto 用户信息DTO
     * @throws BusinessException 用户名已存在抛出业务异常
     */
    @Transactional
    public UserDto register(RegisterRequest request) {
        // 查询用户名是否已经存在
        Long existing = userMapper.selectCount(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, request.username()));
        // 如果用户名已存在，抛出异常
        if (existing != null && existing > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        // 创建用户实体
        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() == null || request.nickname().isBlank()
                ? request.username() : request.nickname());
        user.setEmail(request.email());
        // 设置正常启用状态
        user.setStatus(1);
        // 落库
        userMapper.insert(user);
        return toDto(user);
    }

    /**
     * 账号登录
     * @param username 用户名
     * @param password 明文密码
     * @return AuthResponse 返回 accessToken、refreshToken、用户信息
     * @throws BusinessException 账号密码错误 / 账号禁用抛出异常
     */
    public AuthResponse login(String username, String password) {
        // 根据用户名查询用户
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username));
        // 用户不存在 或者密码校验失败，抛出异常
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        // 账号禁用，抛出异常
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        // 下发全新一对令牌
        return issueTokens(user);
    }

    /**
     * 使用refreshToken刷新获取新的访问令牌，令牌轮换模式
     * @param refreshToken 旧的刷新令牌
     * @return AuthResponse 返回全新 accessToken + refreshToken
     * @throws BusinessException refreshToken无效、用户不存在、账号禁用抛出异常
     */
    public AuthResponse refresh(String refreshToken) {
        // 校验refreshToken，拿到绑定的用户ID
        Long userId = refreshTokenService.validateAndGetUserId(refreshToken);
        if (userId == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        UserEntity user = userMapper.selectById(userId);
        // 用户已删除或者账号被禁用，拒绝刷新
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
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
     * @return UserDto 用户DTO，不存在返回null
     */
    public UserDto getById(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        return user == null ? null : toDto(user);
    }

    /**
     * 签发令牌对：生成accessToken JWT，生成并存储refreshToken
     * @param user 用户数据库实体
     * @return AuthResponse 令牌响应对象
     */
    private AuthResponse issueTokens(UserEntity user) {
        // 生成短期访问JWT
        String accessToken = jwtService.createAccessToken(user);
        // 生成refreshToken字符串
        String refreshToken = jwtService.createRefreshToken();
        // 将refreshToken存入服务端存储（Redis）
        refreshTokenService.store(refreshToken, user.getId());
        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.props().accessTtl().toSeconds(),
                toDto(user)
        );
    }

    /**
     * Entity转DTO，剥离密码等敏感字段对外输出
     * @param user 数据库实体
     * @return UserDto
     */
    private UserDto toDto(UserEntity user) {
        return new UserDto(user.getId(), user.getUsername(), user.getNickname(),
                user.getEmail(), user.getAvatarUrl());
    }
}