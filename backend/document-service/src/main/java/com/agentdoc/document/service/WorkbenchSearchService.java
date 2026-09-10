package com.agentdoc.document.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.WorkbenchSearchConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.dto.WorkbenchSearchQueryDTO;
import com.agentdoc.common.feign.vo.WorkbenchSearchGroupVO;
import com.agentdoc.common.feign.vo.WorkbenchSearchVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

import static com.agentdoc.common.constant.SpacePermissionConstant.AGENT_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_READ;

/** 聚合当前空间内文档、任务和 Agent 的全局搜索结果。 */
@Service
@RequiredArgsConstructor
public class WorkbenchSearchService {

    private final DocumentService documentService;
    private final SpacePermissionService permissionService;
    private final TaskFeign taskFeign;
    private final AgentFeign agentFeign;

    /**
     * 按当前用户的空间权限聚合可见资源。
     *
     * @param request 搜索条件
     * @return 分组搜索结果
     */
    public WorkbenchSearchVO search(WorkbenchSearchQueryDTO request) {
        WorkbenchSearchQueryDTO normalized = normalize(request);
        Set<String> permissions = new HashSet<>(permissionService
                .getEffectivePermissions(normalized.spaceId()).permissions());
        WorkbenchSearchGroupVO documents = permissions.contains(DOCUMENT_READ)
                ? documentService.searchWorkbench(normalized) : WorkbenchSearchGroupVO.empty();
        WorkbenchSearchGroupVO tasks = permissions.contains(TASK_READ)
                ? requireData(taskFeign.searchWorkbench(normalized)) : WorkbenchSearchGroupVO.empty();
        WorkbenchSearchGroupVO agents = permissions.contains(AGENT_READ)
                ? requireData(agentFeign.searchWorkbench(normalized)) : WorkbenchSearchGroupVO.empty();
        return new WorkbenchSearchVO(documents, tasks, agents);
    }

    private WorkbenchSearchQueryDTO normalize(WorkbenchSearchQueryDTO request) {
        if (request == null || request.spaceId() == null || request.keyword() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "空间和搜索关键词不能为空");
        }
        String keyword = request.keyword().trim();
        if (keyword.length() < WorkbenchSearchConstant.MIN_KEYWORD_LENGTH
                || keyword.length() > WorkbenchSearchConstant.MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "搜索关键词长度必须为 2-100 个字符");
        }
        int limit = request.limitPerType() == null
                ? WorkbenchSearchConstant.DEFAULT_LIMIT_PER_TYPE : request.limitPerType();
        limit = Math.max(1, Math.min(limit, WorkbenchSearchConstant.MAX_LIMIT_PER_TYPE));
        return new WorkbenchSearchQueryDTO(request.spaceId(), keyword, limit);
    }

    private <T> T requireData(Result<T> result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "搜索服务暂不可用");
        }
        if (result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result.code(), result.message());
        }
        if (result.data() == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "搜索服务返回结果为空");
        }
        return result.data();
    }
}
