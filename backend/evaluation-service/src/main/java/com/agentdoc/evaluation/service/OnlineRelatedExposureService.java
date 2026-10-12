package com.agentdoc.evaluation.service;

import static com.agentdoc.common.constant.SpacePermissionConstant.*;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.OnlineReviewExposureDTO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.enums.OnlineReviewExposureSource;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/** 只降低当前人类的盲态资格；任务批次按真实assignment映射，不接收客户端actor。 */
@Service
@RequiredArgsConstructor
public class OnlineRelatedExposureService {
    private final OnlineAssignmentMapper assignments;
    private final OnlineReviewExposureService exposures;
    private final SpaceAccessService access;
    public void recordAssignments(Long space, List<Long> assignmentIds) {
        if (assignmentIds.isEmpty()) { return; }
        Long actor = AuthUtils.getUserIdOrException(); access.requirePermission(space, EVALUATION_READ);
        var rows = assignments.selectList(new LambdaQueryWrapper<OnlineAssignmentEntity>().eq(OnlineAssignmentEntity::getSpaceId, space)
                .in(OnlineAssignmentEntity::getId, assignmentIds.stream().distinct().toList()));
        exposures.recordAll(space, rows.stream().map(OnlineAssignmentEntity::getExperimentId).distinct().sorted().toList(), actor, OnlineReviewExposureSource.RELATED_RESOURCE_READ);
    }
    public void record(OnlineReviewExposureDTO request) {
        long actor = AuthUtils.getUserIdOrException();
        if (request == null || request.taskIds() == null || request.experimentIds() == null || request.taskIds().size() + request.experimentIds().size() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "知情批次无效");
        }
        long space = OnlineProtocolUtils.id(request.spaceId()); var ids = new ArrayList<Long>();
        if (!request.taskIds().isEmpty()) {
            access.requirePermission(space, TASK_READ); var taskIds = request.taskIds().stream().map(OnlineProtocolUtils::id).distinct().toList();
            var rows = assignments.selectList(new LambdaQueryWrapper<OnlineAssignmentEntity>().eq(OnlineAssignmentEntity::getSpaceId, space)
                    .in(OnlineAssignmentEntity::getTaskId, taskIds)); ids.addAll(rows.stream().map(OnlineAssignmentEntity::getExperimentId).toList());
        }
        if (!request.experimentIds().isEmpty()) { access.requirePermission(space, EVALUATION_READ); ids.addAll(request.experimentIds().stream().map(OnlineProtocolUtils::id).toList()); }
        exposures.recordAll(space, ids, actor, OnlineReviewExposureSource.RELATED_RESOURCE_READ);
    }
}
