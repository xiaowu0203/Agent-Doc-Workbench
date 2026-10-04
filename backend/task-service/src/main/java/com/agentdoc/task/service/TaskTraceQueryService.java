package com.agentdoc.task.service;

import com.agentdoc.task.constant.TaskTraceConstant;
import com.agentdoc.task.convertor.TaskTraceConvertor;
import com.agentdoc.task.enums.TaskTraceAvailability;
import com.agentdoc.task.infrastructure.JaegerTraceClient;
import com.agentdoc.task.pojo.vo.TaskTraceViewVO;
import com.agentdoc.task.pojo.vo.TaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 先解析 Task 并校验 task:read，再访问冻结 Trace；不提供任意 Trace/URL 查询。 */
@Service
@RequiredArgsConstructor
public class TaskTraceQueryService {
    private final TaskService taskService;
    private final JaegerTraceClient client;
    private final TaskTraceConvertor convertor;

    public TaskTraceViewVO detail(Long taskId) {
        TaskVO task = taskService.detail(taskId);
        String traceId = task.traceId();
        if (traceId == null || traceId.isBlank()) {
            return convertor.unavailable(task.id(), task.spaceId(), traceId, TaskTraceAvailability.NO_TRACE);
        }
        if (!traceId.matches("[0-9a-f]{32}") || traceId.matches("0{32}")) {
            return convertor.unavailable(task.id(), task.spaceId(), null, TaskTraceAvailability.INVALID_TRACE_ID);
        }
        JaegerTraceClient.Fetch fetched = client.fetch(traceId);
        TaskTraceViewVO result = fetched.code() == TaskTraceAvailability.AVAILABLE
                ? convertor.project(task.id(), task.spaceId(), traceId, fetched.payload())
                : convertor.unavailable(task.id(), task.spaceId(), traceId, fetched.code());
        LocalDateTime associatedAt = task.endTime() != null ? task.endTime()
                : task.startTime() != null ? task.startTime() : task.createdAt();
        if (result.availabilityCode() == TaskTraceAvailability.NOT_FOUND_OR_NOT_SAMPLED
                && associatedAt != null && associatedAt.isBefore(LocalDateTime.now().minusDays(TaskTraceConstant.RETENTION_DAYS))) {
            return convertor.unavailable(task.id(), task.spaceId(), traceId, TaskTraceAvailability.RETENTION_WINDOW_ELAPSED);
        }
        return result;
    }
}
