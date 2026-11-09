package com.agentdoc.task.config;

import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.task.constant.EvaluationWorkerSecurityConstant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationWorkerSecurityConfigTest {
    @Test
    void onlyDesignatedPostPathsWithDedicatedHeaderBypassHumanBearerResolver() {
        var resolver = new EvaluationWorkerSecurityConfig().evaluationWorkerBearerTokenResolver();
        for (String path : EvaluationWorkerSecurityConstant.PATHS) {
            var request = new MockHttpServletRequest("POST", path);
            request.setServletPath(path);
            request.addHeader("Authorization", "Bearer fixture");
            request.addHeader(HeaderConstants.X_EVALUATION_WORKER_CAPABILITY, "worker");
            assertThat(resolver.resolve(request)).isNull();
            request.setMethod("GET");
            assertThat(resolver.resolve(request)).isEqualTo("fixture");
            request.setMethod("POST");
            request.setServletPath("/api/task/tasks");
            assertThat(resolver.resolve(request)).isEqualTo("fixture");
        }
        var request = new MockHttpServletRequest("POST", EvaluationWorkerSecurityConstant.PATHS.iterator().next());
        request.setServletPath(EvaluationWorkerSecurityConstant.PATHS.iterator().next());
        request.addHeader("Authorization", "Bearer fixture");
        assertThat(resolver.resolve(request)).isEqualTo("fixture");
    }
}
