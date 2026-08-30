package com.agentdoc.agent.execution.skill;

import java.util.List;

/**
 * Skill 选择结果。
 *
 * @param effectiveMode     实际生效模式，包含可审计的降级模式
 * @param selectedSkills    本次执行选择的 Skill
 * @param routerSnapshotJson Router 调用快照；未调用时为空
 */
public record SkillSelectionResult(
        String effectiveMode,
        List<SkillCandidate> selectedSkills,
        String routerSnapshotJson) {
    public SkillSelectionResult {
        selectedSkills = List.copyOf(selectedSkills);
    }
}
