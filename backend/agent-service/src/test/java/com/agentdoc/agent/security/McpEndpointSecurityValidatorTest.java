package com.agentdoc.agent.security;

import com.agentdoc.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpEndpointSecurityValidatorTest {

    private final McpEndpointSecurityValidator validator = new McpEndpointSecurityValidator();

    @Test
    void acceptsPublicHttpsAddress() {
        assertThatCode(() -> validator.validateExternal("https://8.8.8.8/mcp"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsPrivateSharedAndDocumentationAddresses() {
        assertInvalid("https://127.0.0.1/mcp");
        assertInvalid("https://10.0.0.1/mcp");
        assertInvalid("https://100.64.0.1/mcp");
        assertInvalid("https://192.0.2.1/mcp");
        assertInvalid("https://198.51.100.1/mcp");
        assertInvalid("https://203.0.113.1/mcp");
        assertInvalid("https://[::1]/mcp");
        assertInvalid("https://[2001:db8::1]/mcp");
    }

    @Test
    void validatesTheAddressActuallyResolvedByTheHttpClient() {
        assertThatThrownBy(() -> validator.validateResolved(
                new InetSocketAddress("127.0.0.1", 443)))
                .isInstanceOf(BusinessException.class);
        assertThatCode(() -> validator.validateResolved(
                new InetSocketAddress("8.8.8.8", 443)))
                .doesNotThrowAnyException();
    }

    private void assertInvalid(String value) {
        assertThatThrownBy(() -> validator.validateExternal(value))
                .isInstanceOf(BusinessException.class);
    }
}
