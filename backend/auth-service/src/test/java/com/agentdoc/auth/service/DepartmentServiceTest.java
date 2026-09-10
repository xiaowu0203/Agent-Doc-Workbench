package com.agentdoc.auth.service;

import com.agentdoc.auth.mapper.DepartmentMapper;
import com.agentdoc.auth.mapper.UserMapper;
import com.agentdoc.auth.pojo.dto.DepartmentUpdateDTO;
import com.agentdoc.auth.pojo.entity.DepartmentEntity;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DepartmentServiceTest {

    @Test
    void updateRejectsParentCycle() {
        DepartmentMapper departmentMapper = mock(DepartmentMapper.class);
        DepartmentEntity department = department(10L, 0L);
        DepartmentEntity child = department(20L, 10L);
        when(departmentMapper.selectById(10L)).thenReturn(department);
        when(departmentMapper.selectById(20L)).thenReturn(child);
        DepartmentService service = new DepartmentService(departmentMapper, mock(UserMapper.class),
                mock(PlatformAuditLogService.class));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.update(10L,
                        new DepartmentUpdateDTO("研发部", 20L, null, 0, 1)));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), exception.getCode());
    }

    private DepartmentEntity department(Long id, Long parentId) {
        DepartmentEntity department = new DepartmentEntity();
        department.setId(id);
        department.setParentId(parentId);
        department.setName("部门" + id);
        department.setCode("DEPARTMENT_" + id);
        department.setStatus(1);
        return department;
    }
}
