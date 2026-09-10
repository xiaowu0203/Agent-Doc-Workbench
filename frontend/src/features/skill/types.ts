import type { EntityId } from '@/features/workspace/types'

export type SkillStatus = 'ACTIVE' | 'DISABLED'
export type SkillVersionStatus = 'DRAFT' | 'PUBLISHED'

export interface SkillLatestVersion {
  id: EntityId
  versionNo: number
  status: SkillVersionStatus
  activationDescription: string
  allowedToolCount: number
  createdAt: string | null
  publishedAt: string | null
}

export interface Skill {
  id: EntityId
  spaceId: EntityId
  name: string
  displayName: string
  description: string
  status: SkillStatus
  versionCount: number
  boundAgentCount: number
  latestVersion: SkillLatestVersion | null
  createdBy: EntityId
  createdAt: string | null
  updatedAt: string | null
}

export interface SkillVersion {
  id: EntityId
  skillId: EntityId
  versionNo: number
  status: SkillVersionStatus
  activationDescription: string
  sha256: string
  packageSize: number
  allowedTools: string[]
  readableResourcePaths: string[]
  createdBy: EntityId
  createdAt: string | null
  publishedAt: string | null
}

export interface SkillAgentBinding {
  id: EntityId
  agentId: EntityId
  agentName: string
  agentStatus: 'ENABLED' | 'DISABLED'
  skillVersionId: EntityId
  versionNo: number
  enabled: boolean
}

export interface SkillPage {
  records: Skill[]
  total: number
  pageNum: number
  pageSize: number
}

export interface SkillMetadataInput {
  spaceId?: EntityId
  name: string
  displayName: string
  description: string
}

export interface SkillImportResult {
  skill: Skill
  version: SkillVersion
}

export interface SkillPackageFile {
  path: string
  content: string
}

export interface OnlineSkillPackageInput {
  name: string
  activationDescription: string
  instructions: string
  allowedTools: string[]
  files?: SkillPackageFile[]
}
