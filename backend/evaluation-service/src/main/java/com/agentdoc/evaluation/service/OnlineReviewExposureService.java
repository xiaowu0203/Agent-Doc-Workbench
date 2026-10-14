package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.enums.OnlineReviewExposureSource;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.mapper.OnlineReviewExposureMapper;
import com.agentdoc.evaluation.pojo.entity.OnlineReviewExposureEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/** 调用者先核验当前权限；记录失败即中止暴露响应，事务内不 RPC。 */
@Service
@RequiredArgsConstructor
public class OnlineReviewExposureService {
    private final OnlineExperimentMapper experiments;
    private final OnlineReviewExposureMapper exposures;

    @Transactional(rollbackFor = Exception.class)
    public void record(Long experimentId, Long spaceId, Long actor, OnlineReviewExposureSource source) {
        var experiment = experiments.lock(experimentId);
        if (experiment == null || !Objects.equals(experiment.getSpaceId(), spaceId) || actor == null || actor <= 0 || source == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "知情记录身份不一致");
        }
        var prior = known(experimentId, actor);
        if (prior != null) {
            if (!Objects.equals(prior.getSpaceId(), spaceId)) { throw new BusinessException(ErrorCode.CONFLICT, "知情记录空间不一致"); }
            return;
        }
        var row = new OnlineReviewExposureEntity(); row.setId(IdWorker.getId()); row.setExperimentId(experimentId);
        row.setSpaceId(spaceId); row.setActorId(actor); row.setSource(source.name());
        row.setCapturedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)); row.setSourceHash(hash(row)); exposures.insert(row);
    }

    public OnlineReviewExposureEntity known(Long experimentId, Long actor) {
        var row = exposures.selectOne(new LambdaQueryWrapper<OnlineReviewExposureEntity>()
                .eq(OnlineReviewExposureEntity::getExperimentId, experimentId).eq(OnlineReviewExposureEntity::getActorId, actor));
        if (row != null && (!Objects.equals(row.getExperimentId(), experimentId) || !Objects.equals(row.getActorId(), actor)
                || !Objects.equals(row.getSourceHash(), hash(row)))) { throw new BusinessException(ErrorCode.CONFLICT, "知情事实摘要不一致"); }
        return row;
    }
    /** 分页跨实验知情标记按稳定实验锁序批量完成，不逐项RPC或逐条查询。 */
    @Transactional(rollbackFor = Exception.class)
    public void recordAll(Long space, List<Long> experimentIds, Long actor, OnlineReviewExposureSource source) {
        if (experimentIds.isEmpty()) { return; }
        var ids = experimentIds.stream().distinct().sorted().toList(); var rows = experiments.lockAll(ids);
        if (rows.size() != ids.size() || actor == null || actor <= 0 || source == null || rows.stream().anyMatch(row -> !Objects.equals(space, row.getSpaceId()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "批量知情记录身份不一致");
        }
        var existing = exposures.selectList(new LambdaQueryWrapper<OnlineReviewExposureEntity>().in(OnlineReviewExposureEntity::getExperimentId, ids)
                .eq(OnlineReviewExposureEntity::getActorId, actor)).stream().collect(Collectors.toMap(OnlineReviewExposureEntity::getExperimentId, row -> row));
        var added = new ArrayList<OnlineReviewExposureEntity>(); var now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        for (var experiment : rows) {
            var previous = existing.get(experiment.getId());
            if (previous != null) {
                if (!Objects.equals(previous.getSpaceId(), space) || !Objects.equals(previous.getSourceHash(), hash(previous))) { throw new BusinessException(ErrorCode.CONFLICT, "批量知情事实损坏"); }
                continue;
            }
            var row = new OnlineReviewExposureEntity(); row.setId(IdWorker.getId()); row.setExperimentId(experiment.getId()); row.setSpaceId(space);
            row.setActorId(actor); row.setSource(source.name()); row.setCapturedAt(now); row.setSourceHash(hash(row)); added.add(row);
        }
        if (!added.isEmpty()) { exposures.insertAll(added); }
    }
    public static String hash(OnlineReviewExposureEntity row) {
        return OnlineProtocolUtils.hash("online.review-exposure", Map.of("id", row.getId().toString(),
                "experimentId", row.getExperimentId().toString(), "spaceId", row.getSpaceId().toString(), "actorId", row.getActorId().toString(),
                "source", row.getSource(), "capturedAt", row.getCapturedAt().toString()));
    }
}
