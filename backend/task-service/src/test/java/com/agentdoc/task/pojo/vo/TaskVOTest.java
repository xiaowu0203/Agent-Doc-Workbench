package com.agentdoc.task.pojo.vo;

import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.task.enums.TaskLineageType;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.pojo.entity.TaskEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskVOTest {
    @Test
    void normalTaskHasNoOnlineAssociation() {
        var view = TaskVO.from(task());
        assertThat(view.onlineExperimentId()).isNull();
        assertThat(view.onlineAssignmentId()).isNull();
        assertThat(view.onlineBindingSchemaVersion()).isNull();
        assertThat(view.onlineBindingHash()).isNull();
    }

    @Test
    void tokenEnrichmentPreservesFrozenAssociationAndLongIdentities() {
        var entity = task();
        entity.setOnlineExperimentId(9007199254740993L);
        entity.setOnlineAssignmentId(9007199254740995L);
        entity.setOnlineBindingSchemaVersion(2);
        entity.setOnlineBindingHash("a".repeat(64));
        var view = TaskVO.from(entity).withTokenUsage(123L, false);
        assertThat(view.onlineExperimentId()).isEqualTo("9007199254740993");
        assertThat(view.onlineAssignmentId()).isEqualTo("9007199254740995");
        assertThat(view.onlineBindingSchemaVersion()).isEqualTo(2);
        assertThat(view.onlineBindingHash()).isEqualTo("a".repeat(64));
        assertThat(view.tokensUsed()).isEqualTo(123L);
    }

    @Test
    void publicViewDoesNotSerializeTaskCredentialsOrSlotProof() {
        var entity = task();
        entity.setOnlineExperimentId(9L);
        entity.setOnlineAssignmentId(10L);
        entity.setOnlineBindingSchemaVersion(2);
        entity.setOnlineBindingHash("a".repeat(64));
        entity.setOnlineWaitCapability("private-wait-fixture");
        entity.setCapabilityToken("private-execution-fixture");
        assertThat(JsonUtils.toJson(TaskVO.from(entity)))
                .contains("onlineExperimentId", "onlineAssignmentId", "onlineBindingSchemaVersion", "onlineBindingHash")
                .doesNotContain("private-wait-fixture", "private-execution-fixture", "onlineWaitCapability",
                        "capabilityToken", "onlineSlotGeneration", "onlineSlotPermitHash");
    }

    private static TaskEntity task() {
        var entity = new TaskEntity();
        entity.setId(11L);
        entity.setRootTaskId(11L);
        entity.setSpaceId(7L);
        entity.setAgentId(3L);
        entity.setDocumentId(101L);
        entity.setDocumentType(DocType.FORMAL.getCode());
        entity.setStatus(TaskStatus.PENDING.getCode());
        entity.setExecutionMode(TaskExecutionMode.LIVE.name());
        entity.setLineageType(TaskLineageType.ORIGINAL.name());
        return entity;
    }
}
