package com.agentdoc.task.convertor;

import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.feign.vo.AgentRefVO;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.common.feign.vo.UserRefVO;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.vo.TaskListItemVO;

/**
 * 任务列表跨域展示信息转换器。
 */
public final class TaskConvertor {

    private TaskConvertor() {
    }

    /**
     * 将任务实体及批量查询得到的引用信息转换为列表摘要。
     */
    public static TaskListItemVO toListItemVO(TaskEntity task, AgentRefVO agent,
                                              DocumentRefVO document, UserRefVO creator) {
        return new TaskListItemVO(
                task.getId(), task.getTaskNo(), task.getSpaceId(), task.getName(),
                TaskStatus.fromCode(task.getStatus()), task.getAgentId(), agent == null ? null : agent.name(),
                task.getDocumentId(), document == null ? null : document.title(),
                DocType.fromCode(task.getDocumentType()), task.getTokenBudget(), task.getTokensUsed(),
                task.getCreatedBy(), displayName(creator), task.getStartTime(), task.getEndTime(), task.getCreatedAt());
    }

    private static String displayName(UserRefVO user) {
        if (user == null) {
            return null;
        }
        return user.nickname() == null || user.nickname().isBlank() ? user.username() : user.nickname();
    }
}
