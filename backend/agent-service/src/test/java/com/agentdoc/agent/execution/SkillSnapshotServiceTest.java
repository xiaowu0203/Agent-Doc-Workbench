package com.agentdoc.agent.execution;

import com.agentdoc.agent.config.SkillPackageProperties;
import com.agentdoc.agent.enums.SkillEntryType;
import com.agentdoc.agent.enums.SkillStatus;
import com.agentdoc.agent.enums.SkillVersionStatus;
import com.agentdoc.agent.mapper.AgentSkillMapper;
import com.agentdoc.agent.mapper.SkillMapper;
import com.agentdoc.agent.mapper.SkillVersionMapper;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.AgentSkillEntity;
import com.agentdoc.agent.pojo.entity.SkillEntity;
import com.agentdoc.agent.pojo.entity.SkillVersionEntity;
import com.agentdoc.agent.execution.runtime.SkillExecutionSnapshot;
import com.agentdoc.agent.service.SkillPackageEntry;
import com.agentdoc.agent.service.SkillSnapshotService;
import com.agentdoc.common.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillSnapshotServiceTest {

    @Test
    void batchesBindingsAndHashesSkillsBySkillId() throws Exception {
        AgentSkillMapper bindingMapper = mock(AgentSkillMapper.class);
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillVersionMapper versionMapper = mock(SkillVersionMapper.class);
        SkillSnapshotService service = new SkillSnapshotService(bindingMapper, skillMapper, versionMapper,
                new SkillPackageProperties());

        AgentEntity agent = new AgentEntity();
        agent.setId(1L);
        agent.setSpaceId(9L);
        AgentSkillEntity second = binding(20L, 200L);
        AgentSkillEntity first = binding(10L, 100L);
        SkillEntity secondSkill = skill(20L, "second");
        SkillEntity firstSkill = skill(10L, "first");
        SkillVersionEntity secondVersion = version(200L, 20L, "second-text");
        SkillVersionEntity firstVersion = version(100L, 10L, "first-text");
        when(bindingMapper.selectList(any())).thenReturn(List.of(second, first));
        when(skillMapper.selectBatchIds(anyCollection())).thenReturn(List.of(secondSkill, firstSkill));
        when(versionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(secondVersion, firstVersion));

        SkillExecutionSnapshot snapshot = service.snapshot(agent);

        assertThat(snapshot.skills()).extracting(SkillExecutionSnapshot.BoundSkillSnapshot::skillId)
                .containsExactly(20L, 10L);
        assertThat(snapshot.readableResourcePaths()).containsExactly("references/100.md", "references/200.md");
        String expectedInput = "first1first-shafirst-textsecond2second-shasecond-text";
        assertThat(snapshot.skillInstructionHash()).isEqualTo(sha256(expectedInput));
    }

    private AgentSkillEntity binding(long skillId, long versionId) {
        AgentSkillEntity binding = new AgentSkillEntity();
        binding.setAgentId(1L);
        binding.setSkillId(skillId);
        binding.setSkillVersionId(versionId);
        binding.setEnabled(true);
        return binding;
    }

    private SkillEntity skill(long id, String name) {
        SkillEntity skill = new SkillEntity();
        skill.setId(id);
        skill.setSpaceId(9L);
        skill.setName(name);
        skill.setStatus(SkillStatus.ACTIVE.getCode());
        return skill;
    }

    private SkillVersionEntity version(long id, long skillId, String instruction) {
        SkillVersionEntity version = new SkillVersionEntity();
        version.setId(id);
        version.setSkillId(skillId);
        version.setVersionNo((int) (id / 100));
        version.setStatus(SkillVersionStatus.PUBLISHED.getCode());
        version.setSha256(skillId == 10L ? "first-sha" : "second-sha");
        version.setInstructionText(instruction);
        version.setAllowedToolsJson("[]");
        version.setManifestJson(JsonUtils.toJson(List.of(new SkillPackageEntry(
                "references/" + id + ".md", SkillEntryType.REFERENCE, 1, "resource-sha", true))));
        return version;
    }

    private String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
