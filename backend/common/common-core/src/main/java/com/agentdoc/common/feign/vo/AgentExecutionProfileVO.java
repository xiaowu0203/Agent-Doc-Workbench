package com.agentdoc.common.feign.vo;

import java.math.BigDecimal;

public record AgentExecutionProfileVO(
        Long agentId,
        Long spaceId,
        Long modelId,
        Long tokenBudget,
        String documentScope,
        Long configVersion,
        boolean enabled,
        BigDecimal inputPricePerMillion,
        BigDecimal outputPricePerMillion) {
}
