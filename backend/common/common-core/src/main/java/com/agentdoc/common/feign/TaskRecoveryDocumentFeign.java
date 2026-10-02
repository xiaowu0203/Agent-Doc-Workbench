package com.agentdoc.common.feign;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.TaskRecoveryConstant;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/** 专属恢复直连文档服务；不复用通用写接口。 */
@FeignClient(name = "task-recovery-document", url = "${agent-doc.task-recovery.document-url:http://localhost:8082}")
public interface TaskRecoveryDocumentFeign {
    /** 根据已验签的冻结身份/唯一动作收尾已有暂存。 */
    @PostMapping("/api/document/internal/task-drafts/{taskId}/finalize")
    Result<Void> finalizeDraft(@PathVariable Long taskId,
                               @RequestHeader(TaskRecoveryConstant.CAPABILITY_HEADER) String token);
}
