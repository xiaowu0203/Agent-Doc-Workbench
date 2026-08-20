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

    @Transactional
    public UserDto register(RegisterRequest request) {
        Long existing = userMapper.selectCount(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, request.username()));
        if (existing != null && existing > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() == null || request.nickname().isBlank()
                ? request.username() : request.nickname());
        user.setEmail(request.email());
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setDeleted(0);
        userMapper.insert(user);
        return toDto(user);
    }

    public AuthResponse login(String username, String password) {
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username));
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        return issueTokens(user);
    }

    public AuthResponse refresh(String refreshToken) {
        Long userId = refreshTokenService.validateAndGetUserId(refreshToken);
        if (userId == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        // 轮换：撤销旧 Refresh Token，签发新的一对
        refreshTokenService.revoke(refreshToken);
        return issueTokens(user);
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    public UserDto getById(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        return user == null ? null : toDto(user);
    }

    private AuthResponse issueTokens(UserEntity user) {
        String accessToken = jwtService.createAccessToken(user);
        String refreshToken = jwtService.createRefreshToken();
        refreshTokenService.store(refreshToken, user.getId());
        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.props().accessTtl().toSeconds(),
                toDto(user)
        );
    }

    private UserDto toDto(UserEntity user) {
        return new UserDto(user.getId(), user.getUsername(), user.getNickname(),
                user.getEmail(), user.getAvatarUrl());
    }
}