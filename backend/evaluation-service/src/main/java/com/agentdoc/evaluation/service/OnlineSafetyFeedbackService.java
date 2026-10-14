package com.agentdoc.evaluation.service;

import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.evaluation.config.OnlineReconciliationProperties;
import com.agentdoc.evaluation.mapper.*;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEventEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.ObjectProvider;
import java.util.Map;
import java.util.Objects;

/** 已确认安全事件独立消费；暂停分配不假装远端已取消，追加审计与暂停原子提交。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OnlineSafetyFeedbackService {
    private final OnlineExperimentMapper experiments;
    private final OnlineAssignmentMapper assignments;
    private final EvaluationFeedbackMapper feedback;
    private final OnlineExperimentEventMapper events;
    private final OnlineExecutionAuthority authority;
    private final OnlineReconciliationProperties properties;
    private final ObjectProvider<OnlineSafetyFeedbackService> self;

    @Transactional(rollbackFor = Exception.class)
    public void consume(Long feedbackId) {
        var fact = feedback.selectById(feedbackId);
        if (!OnlineQualityFeedbackFacts.valid(fact) || !Boolean.TRUE.equals(fact.getSeriousSafetyEvent())) { throw new BusinessException(ErrorCode.CONFLICT, "安全确认事实无效"); }
        var assignment = assignments.selectById(fact.getOnlineAssignmentId());
        if (assignment == null || !Objects.equals(assignment.getSpaceId(), fact.getSpaceId()) || !Objects.equals(assignment.getTaskId(), fact.getTaskId())
                || !Objects.equals(assignment.getExecutionId(), fact.getExecutionId())) { throw new BusinessException(ErrorCode.CONFLICT, "安全确认来源不匹配"); }
        var experiment = experiments.lock(assignment.getExperimentId()); if (experiment == null) { throw new BusinessException(ErrorCode.CONFLICT, "安全确认实验不存在"); }
        var prior = events.selectOne(new LambdaQueryWrapper<OnlineExperimentEventEntity>().eq(OnlineExperimentEventEntity::getExperimentId, experiment.getId())
                .eq(OnlineExperimentEventEntity::getAssignmentId, assignment.getId()).eq(OnlineExperimentEventEntity::getEventType, "CONFIRMED_SAFETY_EVENT").last("LIMIT 1"));
        if (prior != null) { return; }
        authority.safetyPause(experiment.getId(), "ONLINE_CONFIRMED_SAFETY_EVENT");
        var current = experiments.selectById(experiment.getId()); var event = new OnlineExperimentEventEntity(); event.setId(IdWorker.getId());
        event.setExperimentId(experiment.getId()); event.setSpaceId(experiment.getSpaceId()); event.setAssignmentId(assignment.getId()); event.setEventType("CONFIRMED_SAFETY_EVENT");
        event.setActorType("SERVICE"); event.setActorId(JwtConstant.EVALUATION_SERVICE); event.setAuthorizedBy(fact.getCreatedBy()); event.setStateVersion(current.getStateVersion());
        event.setReasonCode("ONLINE_CONFIRMED_SAFETY_EVENT"); event.setDetailJson(JsonUtils.toJson(Map.of("feedbackId", fact.getId().toString(), "sourceHash", fact.getSourceHash()))); events.insert(event);
    }

    @Scheduled(fixedDelayString = "${agent-doc.online.reconcile-delay-ms:30000}")
    public void retry() {
        if (!properties.isEnabled()) { return; }
        for (var id : feedback.pendingOnlineSafetyFeedback()) {
            try { self.getObject().consume(id); }
            catch (RuntimeException unavailable) { log.warn("线上安全事实待消费 feedbackId={} failureType={}", id, unavailable.getClass().getSimpleName()); }
        }
    }
}
