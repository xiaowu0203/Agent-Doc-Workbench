package com.agentdoc.task.mcp;

import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.context.TaskCapabilityContext;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import com.agentdoc.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MCP任务作用域校验服务
 * <p>
 * 用于MCP工具调用链路，从线程上下文获取任务能力令牌，完成令牌校验、权限动作鉴权，
 * 解析JWT中的任务、Agent、空间、文档ID，输出MCP调用的安全作用域对象{@link McpTaskScope}。
 * 保证MCP工具只能在令牌授予的任务与权限范围内执行操作。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class McpTaskScopeService {

    private final TaskCapabilityVerifier capabilityVerifier;
    private final TaskService taskService;

    /**
     * 校验并构建MCP任务调用作用域
     * <p>流程：从线程上下文获取任务能力令牌 → 校验JWT签名合法性 → 提取taskId校验数据库层面令牌有效性
     * → 校验JWT包含的action权限列表是否允许当前操作 → 解析各项业务ID，返回作用域对象。
     * 任意一步校验失败抛出FORBIDDEN业务异常，拒绝MCP调用。
     * </p>
     *
     * @param action 需要执行的MCP操作标识，用于权限匹配
     * @return 封装taskId、agentId、spaceId、documentId的MCP任务作用域
     * @throws BusinessException 令牌缺失、签名校验失败、数据库令牌校验不通过、无对应action权限、claim缺失或格式非法时抛出
     */
    public McpTaskScope require(String action) {
        // 从当前线程上下文获取任务能力令牌
        String token = TaskCapabilityContext.current();
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少任务能力令牌");
        }
        // 校验JWT签名合法性
        Jwt jwt = capabilityVerifier.verify(token);
        // 解析任务ID
        Long taskId = longClaim(jwt, JwtConstant.CLAIM_TASK_ID);
        // 校验任务能力令牌：JWT密码学校验 + 业务维度双重校验
        taskService.checkCapability(taskId, token);
        // 读取令牌允许的操作集合，校验是否包含当前action
        List<String> actions = jwt.getClaimAsStringList(JwtConstant.CLAIM_AGENT_ACTIONS);
        if (actions == null || !actions.contains(action)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "任务能力令牌不允许执行该操作");
        }
        // 组装MCP调用安全作用域
        return new McpTaskScope(
                taskId,
                longClaim(jwt, JwtConstant.CLAIM_AGENT_ID),
                longClaim(jwt, JwtConstant.CLAIM_SPACE_ID),
                longClaim(jwt, JwtConstant.CLAIM_DOCUMENT_ID));
    }

    /**
     * 从JWT中读取claim并转换为Long类型
     *
     * @param jwt       JWT对象
     * @param claimName claim字段名称
     * @return 解析后的Long数值
     * @throws BusinessException claim不存在或数值格式非法时抛出权限异常
     */
    private Long longClaim(Jwt jwt, String claimName) {
        Object value = jwt.getClaim(claimName);
        if (value == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "任务能力令牌缺少必要范围");
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "任务能力令牌范围格式无效");
        }
    }
}
