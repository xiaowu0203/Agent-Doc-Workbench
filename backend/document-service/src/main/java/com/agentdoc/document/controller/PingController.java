package com.agentdoc.document.controller;

import com.agentdoc.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PingController {

    @GetMapping({"/", "/api/ping"})
    public Result<Map<String, String>> ping() {
        return Result.ok(Map.of(
                "service", "document-service",
                "status", "ready"
        ));
    }
}
