package com.agentdoc.task.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.task.pojo.param.DocumentActivitySearchParam;
import com.agentdoc.task.pojo.vo.DocumentActivityVO;
import com.agentdoc.task.service.DocumentActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档活动聚合接口。
 */
@Tag(name = "文档活动", description = "聚合文档相关任务与变更请求动态")
@RestController
@RequestMapping("/api/task/documents")
@RequireLogin
@RequiredArgsConstructor
public class DocumentActivityController {

    private final DocumentActivityService documentActivityService;

    @Operation(summary = "查询文档活动")
    @PostMapping("/activity/query")
    public Result<PageVO<DocumentActivityVO>> list(@Valid @RequestBody DocumentActivitySearchParam param) {
        return Result.ok(documentActivityService.list(param));
    }
}
