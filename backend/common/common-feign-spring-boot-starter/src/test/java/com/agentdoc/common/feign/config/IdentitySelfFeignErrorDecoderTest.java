package com.agentdoc.common.feign.config;

import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IdentitySelfFeignErrorDecoderTest {
    @Test
    void distinguishesInvalidIdentityFromUnavailableAuthWithoutCopyingResponse() {
        var decoder = new IdentitySelfFeignErrorDecoder();
        var request = Request.create(Request.HttpMethod.GET, "http://localhost/api/auth/identity", Map.of(), null, StandardCharsets.UTF_8, null);
        var unauthorized = Response.builder().status(401).reason("Unauthorized").request(request).headers(Map.of()).body("sensitive", StandardCharsets.UTF_8).build();
        var unavailable = Response.builder().status(503).reason("Unavailable").request(request).headers(Map.of()).body("sensitive", StandardCharsets.UTF_8).build();
        assertThat(decoder.decode("fixture", unauthorized)).isInstanceOf(BadCredentialsException.class).hasMessageNotContaining("sensitive");
        assertThat(decoder.decode("fixture", unavailable)).isInstanceOf(AuthenticationServiceException.class).hasMessageNotContaining("sensitive");
    }
}
