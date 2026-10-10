package com.agentdoc.evaluation.service;

import static com.agentdoc.common.enums.OnlineReasonCode.*;
import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_CREATE;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.config.SecurityVerifyProperties;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentOnlineConfigFeign;
import com.agentdoc.common.feign.OnlineTaskFeign;
import com.agentdoc.common.feign.dto.OnlineAssignmentRequestDTO;
import com.agentdoc.common.feign.vo.OnlineRouteVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.mapper.OnlineTaskRouteMapper;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineTaskRouteEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** USER 创建入口只委托 Task 证明与 Agent 当前依赖；不接受自报模板/组。 */
@Service
@RequiredArgsConstructor
public class OnlineRoutingService {
    private final SecurityVerifyProperties properties;
    private final SpaceAccessService access;
    private final OnlineTaskFeign task;
    private final AgentOnlineConfigFeign agent;
    private final OnlineExperimentMapper experiments;
    private final OnlineTaskRouteMapper routes;
    private final OnlineRoutingPersistenceService persistence;
    private final OnlineExecutionAuthority authority;
    private final OnlineControlAccessService control;

    public boolean checkScope(OnlineAssignmentRequestDTO request) {
        authorize(request);
        var current = active(request.spaceId());
        return current != null && "ACTIVE".equals(current.getStatus()) && eligible(current, request);
    }

    public OnlineRouteVO route(OnlineAssignmentRequestDTO request) {
        authorize(request);
        if (request.requestKey() == null || !request.requestKey().matches("[A-Za-z0-9._:-]{1,64}")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, REQUEST_KEY_REQUIRED.name());
        }
        if (!request.equals(data(task.creationProof(request.taskId())))) { throw denied(); }
        var prior = routes.selectById(OnlineProtocolUtils.id(request.taskId()));
        if (prior == null) {
            prior = new OnlineTaskRouteEntity(); prior.setId(OnlineProtocolUtils.id(request.taskId()));
            prior.setSpaceId(OnlineProtocolUtils.id(request.spaceId())); prior.setActorId(OnlineProtocolUtils.id(request.actorId()));
            prior.setRequestKey(request.requestKey()); prior.setRequestHash(request.requestHash());
            try { persistence.reserve(prior); }
            catch (DuplicateKeyException race) { prior = routes.selectById(prior.getId()); if (prior == null) { throw denied(); } }
        }
        if (!request.requestHash().equals(prior.getRequestHash()) || !request.spaceId().equals(prior.getSpaceId().toString())
                || !request.actorId().equals(prior.getActorId().toString()) || !request.requestKey().equals(prior.getRequestKey())) { throw denied(); }
        if (prior.getRouteJson() != null) { return parse(prior.getRouteJson()); }
        var active = active(request.spaceId());
        if (active != null && "ACTIVE".equals(active.getStatus()) && eligible(active, request)) { control.require(active.getId()); }
        String dependency = active != null && "ACTIVE".equals(active.getStatus()) && eligible(active, request)
                ? data(agent.taskDependency(active.getId().toString(), request)) : null;
        var resolution = persistence.resolve(request, active == null ? null : active.getId(), dependency);
        if (resolution.refusal() != null) { throw new BusinessException(ErrorCode.CONFLICT, resolution.refusal()); }
        return parse(resolution.routeJson());
    }

    private void authorize(OnlineAssignmentRequestDTO request) {
        if (!properties.isOnlineCapabilityEnabled()) { throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, ONLINE_EXECUTION_NOT_READY.name()); }
        if (request == null || !AuthUtils.getUserIdOrException().toString().equals(request.actorId())) { throw denied(); }
        access.requirePermission(OnlineProtocolUtils.id(request.spaceId()), TASK_CREATE);
    }
    private OnlineExperimentEntity active(String space) {
        return experiments.selectOne(new LambdaQueryWrapper<OnlineExperimentEntity>()
                .eq(OnlineExperimentEntity::getSpaceId, OnlineProtocolUtils.id(space)).eq(OnlineExperimentEntity::getActiveSlot, 1));
    }
    private boolean eligible(OnlineExperimentEntity value, OnlineAssignmentRequestDTO request) {
        if (!value.getAgentId().toString().equals(request.agentId())) { return false; }
        for (var id : authority.manifest(value).path("documentIds")) { if (request.documentId().equals(id.asText())) { return true; } }
        return false;
    }
    private static OnlineRouteVO parse(String json) {
        var node = OnlineProtocolUtils.object(json);
        var route = JsonUtils.parseStrict(JsonUtils.toJson(node.get("payload")), OnlineRouteVO.class);
        if (route == null || !OnlineProtocolUtils.canonical("online.route", route).equals(json)) { throw denied(); }
        return route;
    }
    private static <T> T data(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, ONLINE_FACT_UNKNOWN.name());
        }
        return result.data();
    }
    private static BusinessException denied() { return new BusinessException(ErrorCode.CONFLICT, BINDING_INVALID.name()); }
}
