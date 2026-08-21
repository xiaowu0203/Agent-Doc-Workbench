package com.agentdoc.common.web;

import com.agentdoc.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 服务探活 Ping 控制器，用于健康检查、服务可用性探测。
 * {@code GET /api/{service}/ping} 与根路径 {@code GET /} 探测。
 * 例如 auth-service 同时响应 {@code /api/auth/ping}（网关白名单路径）与 {@code /}。
 * 可通过配置 {@code agent-doc.web.ping-enabled=false} 关闭。
 */
@RestController
public class PingController {

    /** 当前spring应用服务名称，取自 spring.application.name */
    private final String serviceName;

    public PingController(String serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * 探活接口，返回服务就绪状态信息。
     * @param service 可选路径变量，服务标识；为空则使用应用自身服务名
     * @return Result包装的服务状态Map，包含 service 服务标识、status 就绪状态
     */
    @GetMapping({"/", "/api/{service}/ping"})
    public Result<Map<String, String>> ping(@PathVariable(required = false) String service) {
        String name = service == null ? serviceName : service + "-service";
        return Result.ok(Map.of(
                "service", name,
                "status", "ready"
        ));
    }
}
