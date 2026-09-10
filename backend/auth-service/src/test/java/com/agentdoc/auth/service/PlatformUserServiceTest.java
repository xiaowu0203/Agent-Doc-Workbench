package com.agentdoc.auth.service;

import com.agentdoc.auth.enums.UserStatus;
import com.agentdoc.auth.mapper.DepartmentMapper;
import com.agentdoc.auth.mapper.PlatformRoleMapper;
import com.agentdoc.auth.mapper.UserMapper;
import com.agentdoc.auth.mapper.UserPlatformRoleMapper;
import com.agentdoc.auth.pojo.dto.PlatformUserStatusUpdateDTO;
import com.agentdoc.auth.pojo.entity.PlatformRoleEntity;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.pojo.entity.UserPlatformRoleEntity;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformUserServiceTest {

    @Test
    void disablingLastEnabledSuperAdminIsRejected() {
        UserMapper userMapper = mock(UserMapper.class);
        PlatformRoleMapper roleMapper = mock(PlatformRoleMapper.class);
        UserPlatformRoleMapper bindingMapper = mock(UserPlatformRoleMapper.class);
        UserEntity user = new UserEntity();
        user.setId(10L);
        user.setStatus(UserStatus.ENABLED.getCode());
        PlatformRoleEntity role = new PlatformRoleEntity();
        role.setId(1L);
        role.setRoleKey("PLATFORM_SUPER_ADMIN");
        UserPlatformRoleEntity binding = new UserPlatformRoleEntity();
        binding.setUserId(10L);
        binding.setRoleId(1L);
        when(userMapper.selectById(10L)).thenReturn(user);
        when(roleMapper.selectOne(any(Wrapper.class))).thenReturn(role);
        when(bindingMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(bindingMapper.selectList(any(Wrapper.class))).thenReturn(List.of(binding));
        PlatformUserService service = new PlatformUserService(userMapper, mock(DepartmentMapper.class),
                roleMapper, bindingMapper, mock(DepartmentService.class), mock(PasswordEncoder.class),
                mock(RefreshTokenService.class), mock(PlatformAuditLogService.class));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateStatus(10L, new PlatformUserStatusUpdateDTO(0)));

        assertEquals(ErrorCode.CONFLICT.getCode(), exception.getCode());
    }
}
