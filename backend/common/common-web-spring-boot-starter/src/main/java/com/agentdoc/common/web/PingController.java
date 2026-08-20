package com.agentdoc.common.web;

import com.agentdoc.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 通用健康探测接口：由 common-web-spring-boot-starter 自动装配，各服务零配置获得
 * {@code GET /api/{service}/ping} 与根路径 {@code GET /} 探测。
 * 例如 auth-service 同时响应 {@code /api/auth/ping}（网关白名单路径）与 {@code /}。
 * 可通过配置 {@code agent-doc.web.ping-enabled=false} 关闭。
 */
@RestController
public class PingController {

    private final String serviceName;

    public PingController(String serviceName) {
        this.serviceName = serviceName;
    }

    @GetMapping({"/", "/api/{service}/ping"})
    public Result<Map<String, String>> ping(@PathVariable(required = false) String service) {
        String name = service == null ? serviceName : service + "-service";
        return Result.ok(Map.of(
                "service", name,
                "status", "ready"
        ));
    }
}
