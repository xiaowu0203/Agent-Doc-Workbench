package com.agentdoc.document.service;

import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.document.mapper.MemberMapper;
import com.agentdoc.document.mapper.PermissionMapper;
import com.agentdoc.document.mapper.SpaceRoleMapper;
import com.agentdoc.document.mapper.SpaceRolePermissionMapper;
import com.agentdoc.document.pojo.entity.PermissionEntity;
import com.agentdoc.document.pojo.entity.SpaceRoleEntity;
import com.agentdoc.document.pojo.entity.SpaceRolePermissionEntity;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_MANAGE;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_RUN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpaceRoleInitializationTest {

    @Test
    void newSpaceRolesReceiveTheSameEvaluationPermissionsAsExistingRoles() {
        PermissionMapper permissions = mock(PermissionMapper.class);
        SpaceRoleMapper roles = mock(SpaceRoleMapper.class);
        SpaceRolePermissionMapper bindings = mock(SpaceRolePermissionMapper.class);
        List<PermissionEntity> definitions = List.of(EVALUATION_READ, EVALUATION_MANAGE, EVALUATION_RUN)
                .stream().map(code -> {
                    PermissionEntity permission = new PermissionEntity();
                    permission.setCode(code);
                    return permission;
                }).toList();
        when(permissions.selectList(any(Wrapper.class))).thenReturn(definitions);
        AtomicLong roleId = new AtomicLong();
        doAnswer(invocation -> {
            SpaceRoleEntity role = invocation.getArgument(0);
            role.setId(roleId.incrementAndGet());
            return 1;
        }).when(roles).insert(any(SpaceRoleEntity.class));
        List<SpaceRolePermissionEntity> written = new ArrayList<>();
        doAnswer(invocation -> {
            List<SpaceRolePermissionEntity> batch = invocation.getArgument(0);
            written.addAll(batch);
            return batch.size();
        }).when(bindings).insertBatch(any());

        new SpaceRoleService(permissions, roles, bindings, mock(MemberMapper.class), mock(TaskFeign.class))
                .initializeDefaultRoles(1L, 2L);

        assertEquals(List.of(EVALUATION_READ, EVALUATION_MANAGE, EVALUATION_RUN), evaluationPermissions(written, 1L));
        assertEquals(List.of(EVALUATION_READ, EVALUATION_MANAGE, EVALUATION_RUN), evaluationPermissions(written, 2L));
        assertEquals(List.of(EVALUATION_READ), evaluationPermissions(written, 3L));
    }

    private List<String> evaluationPermissions(List<SpaceRolePermissionEntity> bindings, long roleId) {
        return bindings.stream().filter(binding -> binding.getRoleId() == roleId)
                .map(SpaceRolePermissionEntity::getPermissionCode)
                .filter(code -> code.startsWith("evaluation:"))
                .toList();
    }
}
