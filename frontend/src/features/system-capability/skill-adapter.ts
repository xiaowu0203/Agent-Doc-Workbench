import type { Skill } from '@/features/skill/types'
import type { SystemCapability } from '@/features/system-capability/types'

export function toSystemSkill(capability: SystemCapability): Skill {
  return {
    id: capability.id,
    scopeType: 'SYSTEM',
    installationId: null,
    spaceId: null,
    name: capability.technicalKey,
    displayName: capability.displayName,
    description: capability.description || '',
    status: capability.status === 1 ? 'ACTIVE' : 'DISABLED',
    versionCount: capability.skillVersionCount ?? 0,
    boundAgentCount: capability.skillBoundAgentCount ?? 0,
    latestVersion:
      capability.latestSkillVersionNo === null
        ? null
        : {
            id: capability.latestPublishedVersionId ?? 0,
            versionNo: capability.latestSkillVersionNo,
            status: capability.latestSkillVersionStatus === 1 ? 'PUBLISHED' : 'DRAFT',
            activationDescription: capability.latestSkillActivationDescription || '',
            allowedToolCount: capability.latestSkillAllowedToolCount ?? 0,
            createdAt: capability.latestSkillVersionCreatedAt,
            publishedAt: capability.latestSkillVersionPublishedAt,
          },
    createdBy: 0,
    createdAt: capability.createdAt,
    updatedAt: capability.updatedAt,
  }
}
