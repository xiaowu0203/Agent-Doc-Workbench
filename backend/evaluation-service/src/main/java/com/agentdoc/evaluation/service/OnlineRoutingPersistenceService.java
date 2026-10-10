package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.OnlineAssignmentRequestDTO;
import com.agentdoc.common.feign.vo.OnlineRouteVO;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.mapper.OnlineTaskRouteMapper;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineTaskRouteEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** 路由决定与分配同事务；没有远程调用。锁顺序为路由→实验→槽→分配。 */
@Service
@RequiredArgsConstructor
public class OnlineRoutingPersistenceService {
    private final OnlineTaskRouteMapper routes;
    private final OnlineExperimentMapper experiments;
    private final OnlineExecutionAuthority authority;

    @Transactional
    public void reserve(OnlineTaskRouteEntity intent) { routes.insert(intent); }

    @Transactional
    public Resolution resolve(OnlineAssignmentRequestDTO request, Long checkedExperiment, String dependency) {
        var route = routes.lock(OnlineProtocolUtils.id(request.taskId()));
        if (route == null || !request.requestHash().equals(route.getRequestHash())) { throw conflict(OnlineReasonCode.IDEMPOTENCY_CONFLICT); }
        if (route.getRouteJson() != null) { return new Resolution(route.getRouteJson(), null); }
        // (space_id, active_slot) 唯一索引：没有占位时也取得 gap lock，与首次 start 取占位排序。
        var active = experiments.selectOne(new LambdaQueryWrapper<OnlineExperimentEntity>()
                .eq(OnlineExperimentEntity::getSpaceId, route.getSpaceId()).eq(OnlineExperimentEntity::getActiveSlot, 1).last("FOR UPDATE"));
        OnlineRouteVO decision;
        if (active == null || !active.getAgentId().toString().equals(request.agentId())
                || !contains(active, request.documentId())) {
            decision = new OnlineRouteVO(null, "OUTSIDE_ACTIVE_SCOPE");
        } else {
            if (!Objects.equals(active.getId(), checkedExperiment)) { throw conflict(OnlineReasonCode.ONLINE_STATE_CONFLICT); }
            if (!active.getStatus().equals("ACTIVE")) { decision = new OnlineRouteVO(null, "ASSIGNMENT_GATE_CLOSED"); }
            else {
                if (!Objects.equals(active.getManifestSchemaVersion(), OnlineProtocolUtils.SCHEMA_VERSION)
                        || !Objects.equals(dependency, active.getManifestJson() == null ? null
                                : authority.manifest(active).path("dependencyHash").asText())) {
                    authority.safetyPause(active.getId(), OnlineReasonCode.DEPENDENCY_DRIFT.name());
                    return new Resolution(null, OnlineReasonCode.DEPENDENCY_DRIFT.name());
                }
                var allocation = authority.allocate(active.getId(), request);
                if (allocation.binding() == null) { return new Resolution(null, allocation.reason()); }
                decision = new OnlineRouteVO(allocation.binding(), null);
            }
        }
        route.setRouteJson(OnlineProtocolUtils.canonical("online.route", decision)); routes.updateById(route);
        return new Resolution(route.getRouteJson(), null);
    }

    private boolean contains(OnlineExperimentEntity experiment, String documentId) {
        for (var id : authority.manifest(experiment).path("documentIds")) { if (documentId.equals(id.asText())) { return true; } }
        return false;
    }
    private static BusinessException conflict(OnlineReasonCode reason) { return new BusinessException(ErrorCode.CONFLICT, reason.name()); }
    /** 拒绝在事务提交后转 HTTP 错误，保留内核自动关门。 */
    public record Resolution(String routeJson, String refusal) { }
}
