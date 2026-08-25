package com.agentdoc.task.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.task.constant.TaskConstant;
import com.agentdoc.task.pojo.vo.TokenUsageTodayVO;
import com.agentdoc.task.pojo.vo.TokenUsageTrendVO;
import com.agentdoc.task.service.TokenUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Token 用量", description = "空间当日消耗和历史趋势")
@RestController
@RequestMapping("/api/task/token-usage")
@RequireLogin
@RequiredArgsConstructor
public class TokenUsageController {

    private final TokenUsageService tokenUsageService;

    @Operation(summary = "查询空间今日 Token 消耗")
    @GetMapping("/today")
    public Result<TokenUsageTodayVO> today(@RequestParam Long spaceId) {
        return Result.ok(tokenUsageService.today(spaceId));
    }

    @Operation(summary = "查询空间历史 Token 趋势")
    @GetMapping("/trend")
    public Result<List<TokenUsageTrendVO>> trend(@RequestParam Long spaceId,
                                                  @RequestParam(defaultValue = TaskConstant.DEFAULT_TREND_DAYS)
                                                  int days) {
        return Result.ok(tokenUsageService.trend(spaceId, days));
    }
}
