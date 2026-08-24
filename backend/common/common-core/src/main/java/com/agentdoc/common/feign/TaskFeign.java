package com.agentdoc.common.feign;

import com.agentdoc.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

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
}
