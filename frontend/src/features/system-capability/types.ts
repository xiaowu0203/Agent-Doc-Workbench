import type { EntityId } from '@/features/workspace/types'
import type { SkillSelectionMode } from '@/features/agent/types'
import type { McpAuthType } from '@/features/mcp/types'

export type SystemCapabilityType = 'SKILL' | 'AGENT_TEMPLATE' | 'MCP_TEMPLATE'
export type TemplateVersionStatus = 0 | 1 | 2 | 'DRAFT' | 'PUBLISHED' | 'DISABLED'

export interface SystemCapability {
  id: EntityId
  type: SystemCapabilityType
  technicalKey: string
  displayName: string
  description: string | null
  status: 0 | 1
  latestPublishedVersionId: EntityId | null
  latestPublishedVersionNo: number | null
  installationCount: number
  skillVersionCount: number | null
  skillBoundAgentCount: number | null
  latestSkillVersionNo: number | null
  latestSkillVersionStatus: 0 | 1 | null
  latestSkillActivationDescription: string | null
  latestSkillAllowedToolCount: number | null
  latestSkillVersionCreatedAt: string | null
  latestSkillVersionPublishedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface SystemCapabilityPage {
  records: SystemCapability[]
  total: number
  pageNum: number
  pageSize: number
}

export interface SystemCapabilityTypeStatistics {
  type: SystemCapabilityType
  totalCount: number
  enabledCount: number
  publishedVersionCount: number
  installationCount: number
}

export interface SystemCapabilityStatistics {
  totalCount: number
  enabledCount: number
  publishedVersionCount: number
  installationCount: number
  byType: SystemCapabilityTypeStatistics[]
}

export interface CapabilityVersion {
  id: EntityId
  versionNo: number
  status: TemplateVersionStatus
  displayName?: string
  description?: string | null
  systemPrompt?: string
  modelId?: EntityId
  skillSelectionMode?: SkillSelectionMode
  skillRouterModelId?: EntityId | null
  externalMcpEnabled?: boolean
  tokenBudget?: number | null
  toolWhitelist?: string[] | null
  maxIterations?: number
  executionTimeoutSeconds?: number
  skills?: Array<{ skillId: EntityId; skillVersionId: EntityId }>
  mcps?: Array<{
    mcpTemplateId?: EntityId
    mcpTemplateVersionId: EntityId
    toolWhitelist?: string[] | null
  }>
  createdAt: string
  publishedAt?: string | null
  endpointUrl?: string
  authType?: McpAuthType
  authParamName?: string | null
}

export interface AgentTemplateVersionCreateInput {
  displayName: string
  description?: string
  systemPrompt: string
  modelId: EntityId
  skillSelectionMode: SkillSelectionMode
  skillRouterModelId?: EntityId | null
  externalMcpEnabled: boolean
  tokenBudget?: number | null
  toolWhitelist?: string[] | null
  maxIterations?: number
  executionTimeoutSeconds?: number
  skills?: Array<{ skillId: EntityId; skillVersionId: EntityId }>
  mcps?: Array<{ mcpTemplateVersionId: EntityId; toolWhitelist?: string[] | null }>
}

export interface SystemCapabilityCreateInput {
  technicalKey: string
  displayName: string
  description: string
}

export interface McpTemplateVersionInput {
  displayName: string
  endpointUrl: string
  authType: McpAuthType
  authParamName?: string
}

export interface SystemCapabilityUpdateInput {
  displayName: string
  description?: string
  status: 0 | 1
}

export interface SpaceSkillInstallation {
  id: EntityId
  spaceId: EntityId
  skillId: EntityId
  skillName: string
  displayName: string
  description: string | null
  skillVersionId: EntityId
  versionNo: number
  latestPublishedVersionId: EntityId
  latestPublishedVersionNo: number
  upgradeAvailable: boolean
  enabled: boolean
  createdAt: string
  updatedAt: string
}
