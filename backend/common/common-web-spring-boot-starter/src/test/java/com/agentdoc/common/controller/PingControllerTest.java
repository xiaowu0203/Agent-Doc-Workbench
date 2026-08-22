package com.agentdoc.common.controller;

import com.agentdoc.common.api.Result;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PingControllerTest {

    private final PingController controller = new PingController("auth-service");

    @Test
    void pingWithServicePathVariableUsesServiceName() {
        Result<Map<String, String>> result = controller.ping("auth");
        assertEquals("auth-service", result.data().get("service"));
        assertEquals("ready", result.data().get("status"));
    }

    @Test
    void pingWithoutPathVariableUsesApplicationName() {
        Result<Map<String, String>> result = controller.ping(null);
        assertEquals("auth-service", result.data().get("service"));
    }
}
