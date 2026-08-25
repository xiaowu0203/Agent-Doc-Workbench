package com.agentdoc.task.a2a;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class A2aPropertiesTest {

    @Test
    void keepsStandardA2aPathsAsDefaults() {
        A2aProperties properties = new A2aProperties();

        assertThat(properties.getPaths().getSend()).isEqualTo("/a2a/message:send");
        assertThat(properties.getPaths().getTask()).isEqualTo("/a2a/tasks/{taskId}");
        assertThat(properties.getPaths().getCancel()).isEqualTo("/a2a/tasks/{taskId}:cancel");
    }

    @Test
    void supportsOverridingA2aPaths() {
        A2aProperties properties = new A2aProperties();
        A2aProperties.Paths paths = new A2aProperties.Paths();
        paths.setSend("/proxy/a2a/message:send");
        paths.setTask("/proxy/a2a/tasks/{taskId}");
        paths.setCancel("/proxy/a2a/tasks/{taskId}:cancel");
        properties.setPaths(paths);

        assertThat(properties.getPaths().getSend()).isEqualTo("/proxy/a2a/message:send");
        assertThat(properties.getPaths().getTask()).isEqualTo("/proxy/a2a/tasks/{taskId}");
        assertThat(properties.getPaths().getCancel()).isEqualTo("/proxy/a2a/tasks/{taskId}:cancel");
    }
}
