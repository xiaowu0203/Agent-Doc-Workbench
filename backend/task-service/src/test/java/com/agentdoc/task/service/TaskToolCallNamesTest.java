package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.task.pojo.param.TaskNamesSearchParam;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import jakarta.validation.Validation;
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
import java.util.Collections;
import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskToolCallNamesTest {
    @Mock private TaskMapper tasks;
    @Mock private AgentFeign agents;
    @Mock private DocumentFeign documents;
    @Mock private OnlineEvaluationFeign onlineEvaluation;
    @InjectMocks private TaskService service;

    @BeforeAll static void initializeMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TaskEntity.class);
    }

    @Test void nameProjectionIsBoundedScopedAndNeverReadsOtherDomains() {
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserId).thenReturn(null);
            var row = new TaskEntity(); row.setId(11L); row.setSpaceId(7L); row.setName("任务名称");
            when(documents.checkSpacePermission(7L, TASK_READ)).thenReturn(Result.ok());
            when(tasks.selectList(any())).thenAnswer(call -> {
                LambdaQueryWrapper<TaskEntity> query = call.getArgument(0);
                assertThat(query.getSqlSelect()).contains("space_id", "online_experiment_id").doesNotContain("instruction", "input_snapshot");
                assertThat(query.getSqlSegment()).contains("space_id", "id IN");
                assertThat(query.getParamNameValuePairs().values()).contains(7L, 11L);
                return List.of(row);
            });
            var result = service.readNames(7L, List.of(11L));
            assertThat(result.getFirst().name()).isEqualTo("任务名称");
            verify(documents).checkSpacePermission(7L, TASK_READ); verifyNoMoreInteractions(documents);
            verifyNoInteractions(agents, onlineEvaluation);
            assertThatThrownBy(() -> service.readNames(7L, List.of())).hasMessageContaining("1~100");
            assertThatThrownBy(() -> service.readNames(7L, Collections.nCopies(101, 11L))).hasMessageContaining("1~100");
        }
    }

    @Test void deniedTaskReadOrFailedExposurePreventsNameDisclosure() {
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserId).thenReturn(1L);
            when(documents.checkSpacePermission(7L, TASK_READ)).thenReturn(Result.fail(ErrorCode.FORBIDDEN));
            assertThatThrownBy(() -> service.readNames(7L, List.of(11L))).isInstanceOf(RuntimeException.class);
            verifyNoInteractions(tasks, onlineEvaluation);
            when(documents.checkSpacePermission(7L, TASK_READ)).thenReturn(Result.ok());
            var row = new TaskEntity(); row.setId(11L); row.setSpaceId(7L); row.setName("不可披露"); row.setOnlineExperimentId(31L);
            when(tasks.selectList(any())).thenReturn(List.of(row));
            when(onlineEvaluation.recordReviewExposure(any())).thenReturn(Result.fail(ErrorCode.CONFLICT));
            assertThatThrownBy(() -> service.readNames(7L, List.of(11L))).hasMessageContaining("知情记录不可用");
            verify(onlineEvaluation).recordReviewExposure(any());
        }
    }

    @Test void namesRequestRejectsInvalidSpaceEmptyOrOversizedIds() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new TaskNamesSearchParam(7L, List.of(11L)))).isEmpty();
            assertThat(validator.validate(new TaskNamesSearchParam(0L, List.of(11L)))).isNotEmpty();
            assertThat(validator.validate(new TaskNamesSearchParam(7L, List.of()))).isNotEmpty();
            assertThat(validator.validate(new TaskNamesSearchParam(7L, Collections.nCopies(101, 11L)))).isNotEmpty();
            assertThat(validator.validate(new TaskNamesSearchParam(7L, List.of(-1L)))).isNotEmpty();
        }
    }

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
