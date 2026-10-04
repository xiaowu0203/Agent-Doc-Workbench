package com.agentdoc.evaluation.controller;

import static com.agentdoc.common.enums.OnlineReasonCode.*;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.pojo.param.OnlineExperimentSearchParam;
import com.agentdoc.evaluation.pojo.param.OnlineAssignmentSearchParam;
import com.agentdoc.evaluation.pojo.vo.OnlineAssignmentVO;
import com.agentdoc.evaluation.pojo.vo.OnlineExperimentVO;
import com.agentdoc.evaluation.pojo.vo.OnlineExperimentSummaryVO;
import com.agentdoc.evaluation.pojo.vo.OnlineExperimentPreflightVO;
import com.agentdoc.evaluation.service.OnlineExperimentRequestValidator;
import com.agentdoc.evaluation.service.OnlineExperimentService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequireLogin
@RequiredArgsConstructor
@Tag(name = "受控线上实验", description = "创建与只读预检，尚未开放真实流量")
@RequestMapping("/api/evaluation/online-experiments")
public class OnlineExperimentController {
    private final OnlineExperimentService service;

    @Operation(summary = "分页查询实验内分配，任务事实未接入时显示UNKNOWN")
    @PostMapping("/{id}/assignments/search")
    public Result<PageVO<OnlineAssignmentVO>> assignments(@PathVariable String id, @RequestBody String json) {
        JsonNode node;
        try { node = OnlineProtocolUtils.object(json); }
        catch (IllegalArgumentException invalid) { throw new BusinessException(ErrorCode.BAD_REQUEST, MANIFEST_INVALID.name()); }
        for (String field : List.of("documentId", "taskId")) {
            if (node.hasNonNull(field) && !node.path(field).isTextual()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, ID_INVALID.name());
            }
        }
        var param = JsonUtils.parseStrict(json, OnlineAssignmentSearchParam.class);
        if (param == null) { throw new BusinessException(ErrorCode.BAD_REQUEST, MANIFEST_INVALID.name()); }
        return Result.ok(service.assignments(id, param));
    }

    @Operation(summary = "创建冻结线上实验草案")
    @PostMapping
    public Result<OnlineExperimentVO> create(@RequestBody String json) {
        return Result.ok(service.create(OnlineExperimentRequestValidator.parse(json)));
    }

    @Operation(summary = "分页查询线上实验")
    @PostMapping("/search")
    public Result<PageVO<OnlineExperimentSummaryVO>> search(@RequestBody String json) {
        JsonNode node;
        try { node = OnlineProtocolUtils.object(json); }
        catch (IllegalArgumentException invalid) { throw new BusinessException(ErrorCode.BAD_REQUEST, MANIFEST_INVALID.name()); }
        if (!node.path("spaceId").isTextual() || node.hasNonNull("agentId") && !node.path("agentId").isTextual()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ID_INVALID.name());
        }
        var param = JsonUtils.parseStrict(json, OnlineExperimentSearchParam.class);
        if (param == null) { throw new BusinessException(ErrorCode.BAD_REQUEST, MANIFEST_INVALID.name()); }
        return Result.ok(service.search(param));
    }

    @Operation(summary = "查询脱敏实验详情")
    @GetMapping("/{id}")
    public Result<OnlineExperimentVO> detail(@PathVariable String id) {
        return Result.ok(service.detail(id));
    }

    @Operation(summary = "OWNER 动态预检；本批 startable 固定 false")
    @GetMapping("/{id}/preflight")
    public Result<OnlineExperimentPreflightVO> preflight(@PathVariable String id) {
        return Result.ok(service.preflight(id));
    }
}
