package com.agentdoc.agent.service;

import com.agentdoc.agent.pojo.param.ModelSearchParam;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelSearchParamTest {

    @Test
    void defaultsToEightModelsPerPage() {
        ModelSearchParam param = new ModelSearchParam();

        param.validate();

        assertThat(param.getPageNum()).isEqualTo(1);
        assertThat(param.getPageSize()).isEqualTo(8);
    }
}
