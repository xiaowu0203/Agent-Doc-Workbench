package com.agentdoc.document.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.WorkbenchSearchQueryDTO;
import com.agentdoc.common.feign.vo.WorkbenchSearchVO;
import com.agentdoc.document.service.WorkbenchSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 工作台全局搜索接口。 */
@Tag(name = "工作台搜索", description = "聚合搜索当前空间内的文档、任务和 Agent")
@RestController
@RequestMapping("/api/workbench")
@RequireLogin
@RequiredArgsConstructor
public class WorkbenchSearchController {

    private final WorkbenchSearchService searchService;

    @Operation(summary = "全局搜索")
    @PostMapping("/search")
    public Result<WorkbenchSearchVO> search(@RequestBody WorkbenchSearchQueryDTO request) {
        return Result.ok(searchService.search(request));
    }
}
