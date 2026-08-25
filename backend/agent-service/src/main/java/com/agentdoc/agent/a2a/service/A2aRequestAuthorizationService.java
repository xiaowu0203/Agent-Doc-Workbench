package com.agentdoc.agent.a2a.service;

import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.context.TaskCapabilityContext;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * A2A 请求能力令牌鉴权服务
 * <p>
 * 负责校验A2A协议请求的Task‑Capability JWT能力令牌作用域；
 * 保证A2A消息、任务查询、上下文查询操作，只能访问当前令牌绑定的 taskId / agentId / spaceId / documentId；
 * 防止越权访问其它任务、其它空间的数据。
 * </p>
 * <p>鉴权逻辑：从Security上下文取出JWT，对比令牌内声明与请求传入/数据库存储的业务标识是否一致。</p>
 */
@Service
@RequiredArgsConstructor
public class A2aRequestAuthorizationService {

    private final ObjectMapper objectMapper;
    private final AgentExecutionMapper executionMapper;

    /**
     * 校验A2A消息发送的任务作用域
     * <p>
     * 从A2A消息的DataPart解析出{@link AgentTaskInputDTO}；
     * 校验当前线程上下文的taskCapability令牌字符串一致；
     * 再校验JWT中的各项业务声明(taskId/agentId/spaceId/documentId)与入参完全匹配。
     * </p>
     *
     * @param params A2A消息发送参数
     * @throws BusinessException 缺少DataPart、能力令牌不匹配、JWT声明越权、未携带认证令牌时抛出
     */
    public void requireTaskScope(MessageSendParams params) {
        AgentTaskInputDTO input = params.message().parts().stream()
                .filter(DataPart.class::isInstance)
                .map(DataPart.class::cast)
                .map(DataPart::data)
                .map(data -> objectMapper.convertValue(data, AgentTaskInputDTO.class))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "A2A 消息缺少 Workbench DataPart"));

        // 校验线程上下文持有的taskCapability字符串与消息内的一致
        String requestCapability = TaskCapabilityContext.current();
        if (requestCapability == null || !requestCapability.equals(input.taskCapability())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "A2A 请求能力令牌不一致");
        }
        Jwt jwt = currentCapabilityJwt();

        // 逐项比对JWT声明与消息携带的业务ID
        if (!matches(jwt, JwtConstant.CLAIM_TASK_ID, input.workbenchTaskId())
                || !matches(jwt, JwtConstant.CLAIM_AGENT_ID, input.agentId())
                || !matches(jwt, JwtConstant.CLAIM_SPACE_ID, input.spaceId())
                || !matches(jwt, JwtConstant.CLAIM_DOCUMENT_ID, input.documentId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "A2A 请求能力令牌范围不匹配");
        }
    }

    /**
     * 校验A2A‑TaskId查询作用域
     * <p>根据a2aTaskId查询执行记录，校验JWT中的taskId声明与数据库记录的workbenchTaskId匹配。</p>
     *
     * @param a2aTaskId A2A协议侧任务ID
     * @throws BusinessException 执行记录不存在、令牌越权、无认证抛出FORBIDDEN
     */
    public void requireA2aTaskScope(String a2aTaskId) {
        AgentExecutionEntity execution = executionMapper.selectOne(new LambdaQueryWrapper<AgentExecutionEntity>()
                .eq(AgentExecutionEntity::getA2aTaskId, a2aTaskId));
        if (execution == null || !matches(currentCapabilityJwt(), JwtConstant.CLAIM_TASK_ID,
                execution.getWorkbenchTaskId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "A2A Task 不在能力令牌范围内");
        }
    }

    /**
     * 校验A2A‑ContextId查询作用域
     * <p>根据a2aContextId查询执行记录，校验JWT中的taskId声明与数据库记录的workbenchTaskId匹配。</p>
     *
     * @param contextId A2A协议上下文ID
     * @throws BusinessException contextId为空、记录不存在、令牌越权抛出FORBIDDEN
     */
    public void requireA2aContextScope(String contextId) {
        if (contextId == null || contextId.isBlank()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "A2A Task 列表必须限定 contextId");
        }
        AgentExecutionEntity execution = executionMapper.selectOne(new LambdaQueryWrapper<AgentExecutionEntity>()
                .eq(AgentExecutionEntity::getA2aContextId, contextId));
        if (execution == null || !matches(currentCapabilityJwt(), JwtConstant.CLAIM_TASK_ID,
                execution.getWorkbenchTaskId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "A2A Context 不在能力令牌范围内");
        }
    }

    /**
     * 从Security上下文获取当前线程的Task‑Capability JWT令牌
     *
     * @return 解析后的JWT对象
     * @throws BusinessException 上下文不是JwtAuthenticationToken，抛出未授权
     */
    private Jwt currentCapabilityJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken();
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "A2A 请求缺少任务能力认证");
    }

    /**
     * 比对JWT中的claim声明值与预期业务ID是否相等
     * <p>做字符串兼容比对，避免数字类型(Long/Integer)类型不匹配导致判断失效。</p>
     *
     * @param jwt         JWT令牌对象
     * @param claimName   claim键名 {@link JwtConstant}
     * @param expected    预期Long类型业务ID
     * @return true：值相等；false：任意一方为null或值不相等
     */
    private boolean matches(Jwt jwt, String claimName, Long expected) {
        Object claim = jwt.getClaim(claimName);
        return expected != null && claim != null && String.valueOf(expected).equals(String.valueOf(claim));
    }
}
