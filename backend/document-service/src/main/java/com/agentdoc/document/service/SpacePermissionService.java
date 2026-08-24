package com.agentdoc.document.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.SpaceRole;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.context.TaskCapabilityContext;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.document.mapper.MemberMapper;
import com.agentdoc.document.pojo.entity.MemberEntity;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 空间成员权限校验服务：所有空间内操作统一经本服务校验当前登录用户的成员角色。
 * <p>
 * 角色绑定到具体空间（member 表），非用户全局属性；
 * 空间操作权限分级：{@link SpaceRole#OWNER} &gt; {@link SpaceRole#EDITOR} &gt; {@link SpaceRole#VIEWER}。
 * </p>
 */
@Service
public class SpacePermissionService {

    private final MemberMapper memberMapper;
    private final TaskFeign taskFeign;
    private final TaskCapabilityVerifier taskCapabilityVerifier;
    private final HttpServletRequest request;

    @Autowired
    public SpacePermissionService(MemberMapper memberMapper, TaskFeign taskFeign,
                                  TaskCapabilityVerifier taskCapabilityVerifier, HttpServletRequest request) {
        this.memberMapper = memberMapper;
        this.taskFeign = taskFeign;
        this.taskCapabilityVerifier = taskCapabilityVerifier;
        this.request = request;
    }

    /**
     * 查询用户在指定空间的角色，非成员返回 null。
     * @param spaceId 空间 ID
     * @param userId 用户 ID
     * @return 成员角色；非成员返回 null
     */
    public SpaceRole getRole(Long spaceId, Long userId) {
        MemberEntity member = memberMapper.selectOne(new LambdaQueryWrapper<MemberEntity>()
                .eq(MemberEntity::getSpaceId, spaceId)
                .eq(MemberEntity::getUserId, userId));
        return member == null ? null : SpaceRole.fromCode(member.getRole());
    }

    /**
     * 校验当前登录用户是空间成员，返回其角色。
     * @param spaceId 空间 ID
     * @return 当前用户的成员角色
     * @throws BusinessException 未登录（{@link ErrorCode#UNAUTHORIZED}）或非空间成员（{@link ErrorCode#FORBIDDEN}）
     */
    public SpaceRole requireMember(Long spaceId) {
        Long userId = requireUserId();
        SpaceRole role = getRole(spaceId, userId);
        if (role == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不是该空间成员");
        }
        return role;
    }

    /**
     * 校验 Agent 任务能力令牌，并回查 task‑service 当前任务范围。
     * <p>
     * 用于 Agent‑Doc‑Workbench Agent 执行动作时鉴权：
     * 1. 从请求头获取任务能力令牌 X‑TASK‑CAPABILITY
     * 2. 本地上下文校验 Agent 身份、spaceId、documentId、操作权限、taskId
     * 3. 校验令牌签名合法性
     * 4. Feign远程调用 task‑service，回校该任务的实际可用能力范围
     * 5. 校验通过将令牌放入线程上下文，执行完毕清理上下文
     * </p>
     * @param spaceId 空间ID，待校验访问的目标空间
     * @param documentId 文档ID，待校验访问的目标文档
     * @param action Agent待执行动作标识，用于校验动作权限
     * @throws BusinessException 校验不通过抛出业务异常：无权限、未配置校验组件、令牌非法、远程任务校验失败等
     */
    public void requireAgentCapability(Long spaceId, Long documentId, String action) {
        // 从HTTP请求头获取【X‑TASK‑CAPABILITY】任务能力令牌
        String token = request == null ? null : request.getHeader(HeaderConstants.X_TASK_CAPABILITY);

        /**
         * 本地前置合法性校验：
         * 1. 当前上下文必须为Agent调用；
         * 2. 请求头必须携带任务能力令牌；
         * 3. 传入的spaceId、documentId必须与令牌上下文内一致，防止越权访问其他空间/文档；
         * 4. Agent具备该action动作执行权限；
         * 5. 上下文必须携带taskId任务编号；
         * 任意条件不满足，直接抛出无权访问异常
         */
        if (!AuthUtils.isAgent()
                || token == null
                || !spaceId.equals(AuthUtils.getSpaceId())
                || !documentId.equals(AuthUtils.getDocumentId())
                || !AuthUtils.hasAgentAction(action)
                || AuthUtils.getTaskId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Agent 无权访问该文档");
        }

        // 校验依赖组件是否已注入，缺少校验器/Feign客户端直接拒绝
        if (taskCapabilityVerifier == null || taskFeign == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Agent 能力校验未配置");
        }

        // 本地验签：校验X‑TASK‑CAPABILITY令牌签名、有效期合法性
        taskCapabilityVerifier.verify(token);
        // 将任务能力令牌设置到当前线程上下文，供后续业务逻辑取用
        TaskCapabilityContext.set(token);
        try {
            // Feign远程调用task‑service，回查该taskId对应的任务实际能力范围，做二次强校验
            Result<Void> result = taskFeign.checkTaskCapability(AuthUtils.getTaskId());
            // 远程调用返回非成功状态，抛出业务异常
            if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
                throw new BusinessException(result == null ? ErrorCode.FORBIDDEN.getCode() : result.code(),
                        result == null ? "任务能力校验失败" : result.message());
            }
        } finally {
            // 无论正常/异常，强制清空当前线程令牌上下文，防止线程池线程复用造成上下文泄露
            TaskCapabilityContext.clear();
        }
    }

    /**
     * 校验当前登录用户的角色不低于 required（OWNER 满足一切要求，VIEWER 仅满足只读要求）。
     * @param spaceId 空间 ID
     * @param required 最低要求角色
     * @return 当前用户的成员角色
     * @throws BusinessException 未登录 / 非成员 / 角色权限不足（{@link ErrorCode#FORBIDDEN}）
     */
    public SpaceRole requireRole(Long spaceId, SpaceRole required) {
        SpaceRole role = requireMember(spaceId);
        if (role.getCode() > required.getCode()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要角色：" + required.getName() + " 及以上");
        }
        return role;
    }

    /**
     * 取当前登录用户 ID（委托 {@link AuthUtils#getUserIdOrException()}，未登录抛 401）。
     * @return 用户 ID
     * @throws BusinessException 未登录，{@link ErrorCode#UNAUTHORIZED}
     */
    public Long requireUserId() {
        return AuthUtils.getUserIdOrException();
    }
}
