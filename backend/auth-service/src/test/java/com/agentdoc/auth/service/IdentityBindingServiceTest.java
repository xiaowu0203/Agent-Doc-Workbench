package com.agentdoc.auth.service;

import java.util.List;
import com.agentdoc.common.security.ResolvedHumanAuthentication;
import com.agentdoc.auth.config.IdentityBindingProperties;
import com.agentdoc.auth.constant.IdentityBindingConstant;
import com.agentdoc.auth.mapper.IdentityBindingMapper;
import com.agentdoc.auth.pojo.entity.IdentityBindingEntity;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.enums.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityBindingServiceTest {
    private static final String ISSUER = "https://id.example/realm";
    private static final String SUBJECT = "550e8400-e29b-41d4-a716-446655440000";
    @Mock private IdentityBindingMapper mapper;
    @Mock private PlatformUserService users;
    @Mock private PlatformAuditLogService audit;
    private IdentityBindingProperties properties;
    private IdentityBindingService service;

    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace(IdentityBindingServiceTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, IdentityBindingEntity.class);
    }

    @BeforeEach
    void setup() {
        properties = new IdentityBindingProperties();
        properties.setIssuer(ISSUER);
        service = new IdentityBindingService(mapper, users, properties, audit);
        Jwt jwt = Jwt.withTokenValue("fixture").header("alg", "RS256").subject("12").claim("scope", "user").build();
        SecurityContextHolder.getContext().setAuthentication(new ResolvedHumanAuthentication(jwt, Long.valueOf(jwt.getSubject()), List.of()));
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void bindsExistingUserAndRecordsAudit() {
        service.bind(ISSUER, SUBJECT, 42L);
        verify(users).requireEnabledUser(42L);
        verify(mapper).insert(any(IdentityBindingEntity.class));
        verify(audit).record(any(), any(), any(), any());
    }

    @Test
    void duplicateAndRevokedIdentitiesCannotBeRebound() {
        var existing = binding(IdentityBindingConstant.REVOKED);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        assertThatThrownBy(() -> service.bind(ISSUER, SUBJECT, 42L)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).insert(any(IdentityBindingEntity.class));
        assertThatThrownBy(() -> service.resolve(ISSUER, SUBJECT)).isInstanceOf(BusinessException.class);
    }

    @Test
    void verifiesExactIdentityAndEnabledUserOnEveryResolve() {
        var existing = binding(IdentityBindingConstant.ACTIVE);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        var user = new UserEntity();
        user.setId(42L);
        when(users.requireEnabledUser(42L)).thenReturn(user);
        assertThat(service.resolve(ISSUER, SUBJECT)).isSameAs(user);
        verify(users).requireEnabledUser(42L);
        existing.setSubject("different");
        assertThatThrownBy(() -> service.resolve(ISSUER, SUBJECT)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.resolve(ISSUER + "/", SUBJECT)).isInstanceOf(BusinessException.class);
        properties.setIssuer("");
        assertThatThrownBy(() -> service.resolve(ISSUER, SUBJECT)).isInstanceOf(BusinessException.class);
    }

    @Test
    void concurrentDuplicateFailsClosed() {
        doThrow(new DuplicateKeyException("fixture")).when(mapper).insert(any(IdentityBindingEntity.class));
        assertThatThrownBy(() -> service.bind(ISSUER, SUBJECT, 42L)).isInstanceOf(BusinessException.class);
        verify(audit, never()).record(any(), any(), any(), any());
    }

    @Test
    void disabledUserCannotBeBound() {
        doThrow(new BusinessException(ErrorCode.BAD_REQUEST)).when(users).requireEnabledUser(42L);
        assertThatThrownBy(() -> service.bind(ISSUER, SUBJECT, 42L)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).insert(any(IdentityBindingEntity.class));
    }

    @Test
    void revokeRequiresMatchingUserAndUpdatesOnlyActiveRecord() {
        var existing = binding(IdentityBindingConstant.ACTIVE);
        existing.setId(7L);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        assertThatThrownBy(() -> service.revoke(ISSUER, SUBJECT, 43L)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
        when(mapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        service.revoke(ISSUER, SUBJECT, 42L);
        verify(users).invalidateHumanAuthentication(42L);
        verify(audit).record(any(), any(), any(), any());
    }

    private IdentityBindingEntity binding(String status) {
        var result = new IdentityBindingEntity();
        result.setIssuer(ISSUER);
        result.setSubject(SUBJECT);
        result.setStatus(status);
        result.setUserId(42L);
        return result;
    }
}
