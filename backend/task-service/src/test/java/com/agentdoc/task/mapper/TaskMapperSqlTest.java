package com.agentdoc.task.mapper;

import com.agentdoc.task.pojo.entity.TaskEntity;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskMapperSqlTest {

    @Test
    void batchInsertBindsExperimentCandidateIdentity() {
        Configuration configuration = new Configuration();
        String resource = "mapper/TaskMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        } catch (Exception exception) {
            throw new AssertionError("Task Mapper XML 无法加载", exception);
        }

        TaskEntity task = new TaskEntity();
        task.setCandidateConfigId(101L);
        task.setCandidateSnapshotSchemaVersion(3);
        task.setCandidateSnapshotHash("a".repeat(64));
        var sql = configuration.getMappedStatement(TaskMapper.class.getName() + ".insertBatch")
                .getBoundSql(Map.of("entities", List.of(task)));

        assertThat(sql.getSql()).contains("candidate_config_id", "candidate_snapshot_schema_version",
                "candidate_snapshot_hash");
        assertThat(sql.getParameterMappings()).extracting(mapping -> mapping.getProperty())
                .anyMatch(property -> property.endsWith("candidateConfigId"))
                .anyMatch(property -> property.endsWith("candidateSnapshotSchemaVersion"))
                .anyMatch(property -> property.endsWith("candidateSnapshotHash"));
    }
}
