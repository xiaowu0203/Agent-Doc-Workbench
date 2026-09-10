package com.agentdoc.document.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.document.pojo.dto.PlatformUserMembershipQueryDTO;
import com.agentdoc.document.pojo.vo.PlatformUserMembershipVO;
import com.agentdoc.document.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "平台成员查询", description = "平台超级管理员批量读取用户的空间参与情况")
@RestController
@RequestMapping("/api/document/platform/user-memberships")
@RequireLogin
@PreAuthorize("@SpacePermission.isPlatformSuperAdmin()")
@RequiredArgsConstructor
public class PlatformMembershipController {

    private final MemberService memberService;

    @Operation(summary = "批量查询用户空间参与情况")
    @PostMapping("/query")
    public Result<List<PlatformUserMembershipVO>> query(
            @Valid @RequestBody PlatformUserMembershipQueryDTO dto) {
        return Result.ok(memberService.queryPlatformMemberships(dto.userIds()));
    }
}
