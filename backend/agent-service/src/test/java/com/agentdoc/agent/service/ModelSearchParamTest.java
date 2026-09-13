package com.agentdoc.agent.service;

import com.agentdoc.agent.pojo.param.ModelSearchParam;
import com.agentdoc.common.pojo.dto.PageParam;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelSearchParamTest {

    @Test
    void defaultsToPlatformPageSize() {
        ModelSearchParam param = new ModelSearchParam();

        param.validate();

        // 未传分页参数时使用平台统一默认值；前端模型卡片网格会显式传 pageSize=8 覆盖
        assertThat(param.getPageNum()).isEqualTo(PageParam.DEFAULT_PAGE_NUM);
        assertThat(param.getPageSize()).isEqualTo(PageParam.DEFAULT_PAGE_SIZE);
    }
}
