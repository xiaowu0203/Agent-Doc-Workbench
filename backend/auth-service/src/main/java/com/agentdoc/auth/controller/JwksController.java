package com.agentdoc.auth.controller;

import com.agentdoc.auth.service.JwtService;
import com.nimbusds.jose.jwk.JWKSet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * JWKS 公钥分发端点：供网关与业务服务校验 JWT。
 */
@Tag(name = "JWKS", description = "RSA 公钥分发")
@RestController
public class JwksController {

    private final JwtService jwtService;

    public JwksController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Operation(summary = "RSA 公钥 JWK Set")
    @GetMapping("/oauth2/jwks")
    public Map<String, Object> jwks() {
        JWKSet jwkSet = jwtService.jwkSet();
        return Map.of("keys", jwkSet.toJSONObject().get("keys"));
    }
}