package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.vo.AgentRefVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskToolCallNamesTest {
    @Mock private TaskMapper tasks;
    @Mock private AgentFeign agents;
    @Mock private DocumentFeign documents;
    @InjectMocks private TaskService service;

    @Test void toolPageNamesRequireOnlyTaskReadAndNeverLoadDocumentsOrUsers() {
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserId).thenReturn(null);
            var row = new TaskEntity(); row.setId(11L); row.setSpaceId(7L); row.setName("任务名称");
            row.setAgentId(1L); row.setLineageType("ORIGINAL"); row.setExecutionMode("LIVE"); row.setStatus(2); row.setDocumentType(1);
            when(documents.checkSpacePermission(7L, TASK_READ)).thenReturn(Result.ok());
            when(tasks.selectList(any())).thenReturn(List.of(row));
            when(agents.queryAgentRefs(any())).thenReturn(Result.ok(List.of(new AgentRefVO(1L, 7L, "Agent 名称"))));
            var result = service.readListItems(7L, List.of(11L)).getFirst();
            assertThat(result.name()).isEqualTo("任务名称"); assertThat(result.agentName()).isEqualTo("Agent 名称");
            assertThat(result.documentTitle()).isNull(); assertThat(result.creatorName()).isNull();
            verify(documents).checkSpacePermission(7L, TASK_READ); verifyNoMoreInteractions(documents);
        }
    }
}
