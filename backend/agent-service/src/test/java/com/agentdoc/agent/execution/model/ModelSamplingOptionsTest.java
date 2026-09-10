package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.pojo.entity.ModelEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelSamplingOptionsTest {

    @Test
    void parsesSupportedSamplingParameters() {
        ModelEntity model = new ModelEntity();
        model.setOptionsJson("{\"temperature\":0.7,\"topP\":0.9,\"futureOption\":true}");

        ModelSamplingOptions options = ModelSamplingOptions.from(model);

        assertThat(options.temperature()).isEqualTo(0.7);
        assertThat(options.topP()).isEqualTo(0.9);
    }

    @Test
    void ignoresMalformedOrOutOfRangeParameters() {
        ModelEntity model = new ModelEntity();
        model.setOptionsJson("{\"temperature\":3,\"topP\":\"invalid\"}");

        ModelSamplingOptions options = ModelSamplingOptions.from(model);

        assertThat(options.temperature()).isNull();
        assertThat(options.topP()).isNull();
    }
}
