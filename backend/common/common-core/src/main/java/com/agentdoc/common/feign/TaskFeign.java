package com.agentdoc.common.feign;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.DocumentVersionRollbackAuditDTO;
import com.agentdoc.common.feign.dto.DocumentVersionSourceQueryDTO;
import com.agentdoc.common.feign.dto.SpaceRoleAuditDTO;
import com.agentdoc.common.feign.vo.DocumentVersionSourceVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * Task 服务内部能力校验契约。
 */
@FeignClient(name = "task-service", url = "${agent-doc.feign.gateway-url:http://localhost:9090}")
public interface TaskFeign {

    /**
     * 检查任务是否具备执行能力。
     */
    @GetMapping("/api/task/internal/tasks/{taskId}/capability")
    Result<Void> checkTaskCapability(@PathVariable Long taskId);

    /** 批量查询文档版本关联的任务与审批展示信息。 */
    @PostMapping("/api/task/internal/tasks/version-sources/query")
    Result<List<DocumentVersionSourceVO>> queryDocumentVersionSources(
            @RequestBody DocumentVersionSourceQueryDTO request);

    /** 记录文档版本回滚审计。 */
    @PostMapping("/api/task/internal/tasks/document-version-rollback-audit")
    Result<Void> recordDocumentVersionRollback(@RequestBody DocumentVersionRollbackAuditDTO request);

    /** 记录空间角色与权限变更审计。 */
    @PostMapping("/api/task/internal/tasks/space-role-audit")
    Result<Void> recordSpaceRoleAudit(@RequestBody SpaceRoleAuditDTO request);
}
