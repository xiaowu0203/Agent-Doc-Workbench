package com.agentdoc.auth.controller;

import com.agentdoc.auth.pojo.dto.DepartmentCreateDTO;
import com.agentdoc.auth.pojo.dto.DepartmentUpdateDTO;
import com.agentdoc.auth.pojo.vo.DepartmentVO;
import com.agentdoc.auth.service.DepartmentService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;

@Tag(name = "平台部门管理", description = "平台组织部门查询、创建、修改和删除")
@RestController
@RequestMapping("/api/platform/departments")
@RequireLogin
@PreAuthorize("@platformRoleService.hasCurrentUserRole('" + SUPER_ADMIN + "')")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @Operation(summary = "查询部门树数据")
    @GetMapping
    public Result<List<DepartmentVO>> list() {
        return Result.ok(departmentService.list());
    }

    @Operation(summary = "查询部门详情")
    @GetMapping("/{departmentId}")
    public Result<DepartmentVO> detail(@PathVariable Long departmentId) {
        return Result.ok(departmentService.detail(departmentId));
    }

    @Operation(summary = "创建部门")
    @PostMapping
    public Result<DepartmentVO> create(@Valid @RequestBody DepartmentCreateDTO dto) {
        return Result.ok(departmentService.create(dto));
    }

    @Operation(summary = "修改部门")
    @PutMapping("/{departmentId}")
    public Result<DepartmentVO> update(@PathVariable Long departmentId,
                                       @Valid @RequestBody DepartmentUpdateDTO dto) {
        return Result.ok(departmentService.update(departmentId, dto));
    }

    @Operation(summary = "删除空部门")
    @DeleteMapping("/{departmentId}")
    public Result<Void> delete(@PathVariable Long departmentId) {
        departmentService.delete(departmentId);
        return Result.ok();
    }
}
