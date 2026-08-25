package com.agentdoc.agent.a2a.server;

import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.context.TaskCapabilityContext;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class A2aRequestAuthorizationServiceTest {

    private static final String CAPABILITY = "task-capability";
    private static final long TASK_ID = 11L;
    private static final long AGENT_ID = 22L;
    private static final long SPACE_ID = 33L;
    private static final long DOCUMENT_ID = 44L;

    private A2aRequestAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new A2aRequestAuthorizationService(new ObjectMapper(), mock(AgentExecutionMapper.class));
        TaskCapabilityContext.set(CAPABILITY);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(capabilityJwt()));
    }

    @AfterEach
    void clearContext() {
        TaskCapabilityContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAcceptMatchingCapabilityAndScope() {
        service.requireTaskScope(params(input(CAPABILITY, DOCUMENT_ID)));
    }

    @Test
    void shouldRejectCapabilityFromMessageBodyWhenItDiffersFromRequestHeader() {
        assertThatThrownBy(() -> service.requireTaskScope(params(input("another-capability", DOCUMENT_ID))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("令牌不一致");
    }

    @Test
    void shouldRejectDocumentOutsideCapabilityScope() {
        assertThatThrownBy(() -> service.requireTaskScope(params(input(CAPABILITY, DOCUMENT_ID + 1))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("范围不匹配");
    }

    private AgentTaskInputDTO input(String capability, Long documentId) {
        return new AgentTaskInputDTO(TASK_ID, AGENT_ID, SPACE_ID, documentId, 1000L,
                "http://task-service/mcp", capability);
    }

    private MessageSendParams params(AgentTaskInputDTO input) {
        Message message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId("message-id")
                .parts(new DataPart(input))
                .build();
        return MessageSendParams.builder().message(message).build();
    }

    private Jwt capabilityJwt() {
        Instant issuedAt = Instant.now();
        return Jwt.withTokenValue(CAPABILITY)
                .header("alg", "none")
                .subject("agent-task")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(60))
                .claim(JwtConstant.CLAIM_TASK_ID, TASK_ID)
                .claim(JwtConstant.CLAIM_AGENT_ID, AGENT_ID)
                .claim(JwtConstant.CLAIM_SPACE_ID, SPACE_ID)
                .claim(JwtConstant.CLAIM_DOCUMENT_ID, DOCUMENT_ID)
                .build();
    }
}
