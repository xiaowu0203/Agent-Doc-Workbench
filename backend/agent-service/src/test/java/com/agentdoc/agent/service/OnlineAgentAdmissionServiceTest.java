package com.agentdoc.agent.service;

import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.config.SecurityVerifyProperties;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.feign.OnlineTaskFeign;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import com.agentdoc.common.feign.dto.OnlineDispatchIdentityDTO;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.feign.vo.OnlineSlotPermitVO;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static com.agentdoc.common.constant.OnlineCapabilityConstant.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OnlineAgentAdmissionServiceTest {
    private final SecurityVerifyProperties properties = new SecurityVerifyProperties();
    private final OnlineEvaluationFeign evaluation = mock(OnlineEvaluationFeign.class);
    private final OnlineTaskFeign tasks = mock(OnlineTaskFeign.class);
    private final TaskCapabilityVerifier verifier = mock(TaskCapabilityVerifier.class);
    private final AgentExecutionMapper executions = mock(AgentExecutionMapper.class);
    private final OnlineAgentAdmissionService service = new OnlineAgentAdmissionService(properties, evaluation, tasks, verifier, executions);
    private final OnlineTaskBindingDTO binding = new OnlineTaskBindingDTO("61", "11", "a".repeat(64), "1", "BASELINE", 9000,
            "50", "10", "20", "30", "40", "1", "b".repeat(64), 1, "c".repeat(64), "71", 2, "d".repeat(64),
            "e".repeat(64), "f".repeat(64), "100", 600, "LIVE", "ORIGINAL", 2);
    private final OnlineDispatchIdentityDTO identity = new OnlineDispatchIdentityDTO("11", "61", 2, OnlineProtocolUtils.hash("online.binding", binding), 3L, "9".repeat(64));
    @BeforeEach void setup() {
        properties.setOnlineCapabilityEnabled(true);
        when(tasks.executionIdentity("10", "Bearer execution")).thenReturn(Result.ok("DISPATCHED"));
        when(verifier.verify("execution")).thenReturn(Jwt.withTokenValue("execution").header("alg", "RS256")
                .claim(EXPERIMENT_ID, "11").claim(ASSIGNMENT_ID, "61").claim(BINDING_SCHEMA, 2).claim(BINDING_HASH, identity.bindingHash())
                .claim(SLOT_GENERATION, 3).claim(SLOT_PERMIT_HASH, identity.permitHash()).build());
        when(evaluation.executionPermit("10", "Bearer execution")).thenReturn(Result.ok(new OnlineSlotPermitVO(binding, identity.bindingHash(), 3, identity.permitHash(), false)));
    }
    @Test void strippingBothDataPartAndJwtOnlineFieldsStillRequiresPersistedTaskProof() {
        when(verifier.verify("execution")).thenReturn(Jwt.withTokenValue("execution").header("alg", "RS256").claim(JwtConstant.CLAIM_TASK_ID, "10").build());
        when(tasks.executionIdentity("10", "Bearer execution")).thenReturn(Result.fail(403, "BINDING_INVALID"));
        assertThatThrownBy(() -> service.accept(input("LIVE", null, 100L))).isInstanceOf(BusinessException.class);
        verify(tasks).executionIdentity("10", "Bearer execution"); verifyNoInteractions(evaluation);
    }
    @Test void wrongSlotGenerationAndOfflineModeCannotReachBegin() {
        assertThatThrownBy(() -> service.binding(input("ISOLATED", identity, 100L))).isInstanceOf(BusinessException.class);
        when(evaluation.executionPermit("10", "Bearer execution")).thenReturn(Result.ok(new OnlineSlotPermitVO(binding, identity.bindingHash(), 4, identity.permitHash(), false)));
        assertThatThrownBy(() -> service.begin(input("LIVE", identity, 100L))).isInstanceOf(BusinessException.class);
        verify(evaluation, never()).begin(anyString(), anyString());
    }
    @Test void budgetAndEmergencyRecheckFailClosedAfterInitialAdmission() {
        service.accept(input("LIVE", identity, 100L));
        assertThatThrownBy(() -> service.binding(input("LIVE", identity, 101L))).isInstanceOf(BusinessException.class);
        when(evaluation.begin("10", "Bearer execution")).thenReturn(Result.fail(409, "ONLINE_GATE_CLOSED"));
        assertThatThrownBy(() -> service.begin(input("LIVE", identity, 100L))).isInstanceOf(BusinessException.class);
    }
    private AgentTaskInputDTO input(String mode, OnlineDispatchIdentityDTO online, Long budget) {
        return new AgentTaskInputDTO(10L, 30L, 20L, 40L, budget, mode, 1L, binding.documentContentHash(), 1, binding.inputHash(),
                null, null, null, null, null, null, null, null, "http://task/mcp", "execution", online);
    }
}
