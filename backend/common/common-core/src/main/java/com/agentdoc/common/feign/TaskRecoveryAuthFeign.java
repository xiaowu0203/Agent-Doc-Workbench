package com.agentdoc.common.feign;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.TaskRecoveryConstant;
import com.agentdoc.common.feign.dto.TaskRecoveryIssueDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** 专用直连 auth-service 的恢复契约，不复用公共 Gateway URL。 */
@FeignClient(name = "task-recovery-auth", url = "${agent-doc.task-recovery.auth-url:http://localhost:8081}")
public interface TaskRecoveryAuthFeign {
    /** 机器身份申请单 Task 查询/必要取消窄凭证。 */
    @PostMapping("/api/auth/internal/task-recovery-capabilities")
    Result<String> issueRecovery(@RequestHeader(TaskRecoveryConstant.MACHINE_KEY_HEADER) String machineKey,
                                 @RequestBody TaskRecoveryIssueDTO request);

    /** 机器身份申请 LIVE 既有草稿收尾窄凭证。 */
    @PostMapping("/api/auth/internal/task-draft-finalization-capabilities")
    Result<String> issueFinalization(@RequestHeader(TaskRecoveryConstant.MACHINE_KEY_HEADER) String machineKey,
                                     @RequestBody TaskRecoveryIssueDTO request);
}
