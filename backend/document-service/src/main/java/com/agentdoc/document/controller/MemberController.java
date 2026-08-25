package com.agentdoc.document.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.document.pojo.dto.MemberAddDTO;
import com.agentdoc.document.pojo.dto.MemberRoleUpdateDTO;
import com.agentdoc.document.pojo.vo.MemberVO;
import com.agentdoc.document.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "空间成员管理", description = "成员添加、列表、改角色、移除")
@RestController
@RequestMapping("/api/document/spaces/{spaceId}/members")
@RequireLogin
@Validated
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "添加成员（OWNER 权限）")
    @PostMapping
    public Result<MemberVO> add(@PathVariable Long spaceId, @Valid @RequestBody MemberAddDTO dto) {
        return Result.ok(memberService.add(spaceId, dto));
    }

    @Operation(summary = "成员列表（空间成员可查看）")
    @GetMapping
    public Result<List<MemberVO>> list(@PathVariable Long spaceId) {
        return Result.ok(memberService.list(spaceId));
    }

    @Operation(summary = "修改成员角色（OWNER 权限，空间至少保留一名 OWNER）")
    @PutMapping("/{userId}")
    public Result<MemberVO> changeRole(@PathVariable Long spaceId, @PathVariable Long userId,
                                       @Valid @RequestBody MemberRoleUpdateDTO dto) {
        return Result.ok(memberService.changeRole(spaceId, userId, dto));
    }

    @Operation(summary = "移除成员（OWNER 权限，空间至少保留一名 OWNER）")
    @DeleteMapping("/{userId}")
    public Result<Void> remove(@PathVariable Long spaceId, @PathVariable Long userId) {
        memberService.remove(spaceId, userId);
        return Result.ok();
    }
}
