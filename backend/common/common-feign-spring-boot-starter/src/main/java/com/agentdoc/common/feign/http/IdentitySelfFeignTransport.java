package com.agentdoc.common.feign.http;

import com.agentdoc.common.feign.config.AuthenticationHttpBudget;
import feign.Client;
import feign.Response;
import org.springframework.http.HttpHeaders;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 只由本人身份Feign工厂使用，不作为全局Client Bean替换其他业务传输。 */
public final class IdentitySelfFeignTransport implements AutoCloseable {
    private final AuthenticationHttpClient transport = new AuthenticationHttpClient("auth_identity", AuthenticationHttpBudget.IDENTITY);

    public Client client() {
        return (request, options) -> {
            HttpHeaders headers = new HttpHeaders();
            request.headers().forEach((name, values) -> headers.put(name, List.copyOf(values)));
            var response = transport.execute(URI.create(request.url()), request.httpMethod().name(), headers, request.body());
            Map<String, Collection<String>> responseHeaders = new LinkedHashMap<>();
            responseHeaders.putAll(response.headers());
            return Response.builder().status(response.status()).reason(response.reason()).headers(responseHeaders)
                    .body(response.body()).request(request).build();
        };
    }

    @Override public void close() { transport.close(); }
}
