package com.agentdoc.task.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TaskRecoveryPropertiesTest {
    @Test
    void disabledByDefaultAndEnforcesTransportAndDeadlineBoundsWhenEnabled() {
        TaskRecoveryProperties properties = new TaskRecoveryProperties();
        assertThat(properties.isConfigured()).isFalse();
        properties.setEnabled(true);
        properties.setMachineKey("test-only-".repeat(4));
        assertThatCode(properties::validate).doesNotThrowAnyException();
        properties.setAuthUrl("http://192.0.2.10:8081");
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
        properties.setAuthUrl("https://example.test");
        assertThatCode(properties::validate).doesNotThrowAnyException();
        properties.setReadTimeoutMs(6000);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
        assertThat(properties.toString()).doesNotContain(properties.getMachineKey());
    }
}
