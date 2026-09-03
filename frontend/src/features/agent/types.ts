import type { EntityId } from '@/features/workspace/types'

export type AgentStatus = 'ENABLED' | 'DISABLED'
export type SkillSelectionMode = 'ALL_BOUND' | 'ROUTER'

export interface AgentCard {
  id: EntityId
  spaceId: EntityId
  name: string
  description: string | null
  modelId: EntityId
  modelDisplayName: string | null
  skillSelectionMode: SkillSelectionMode
  externalMcpEnabled: boolean
  tokenBudget: number | null
  maxIterations: number
  executionTimeoutSeconds: number
  configVersion: number
  status: AgentStatus
  skillCount: number
  mcpCount: number
  toolCount: number
  createdAt: string | null
  updatedAt: string | null
}

export interface AgentDetail {
  id: EntityId
  spaceId: EntityId
  name: string
  description: string | null
  systemPrompt: string
  modelId: EntityId
  skillSelectionMode: SkillSelectionMode
  skillRouterModelId: EntityId | null
  externalMcpEnabled: boolean
  tokenBudget: number | null
  documentScope: string | null
  toolWhitelist: string[] | null
  maxIterations: number
  executionTimeoutSeconds: number
  configVersion: number
  status: AgentStatus
  createdBy: EntityId
  createdAt: string | null
  updatedAt: string | null
}

export interface AgentPage {
  records: AgentCard[]
  total: number
  pageNum: number
  pageSize: number
}

export interface AgentInput {
  spaceId?: EntityId
  name: string
  description?: string
  systemPrompt: string
  modelId: EntityId
  skillSelectionMode: SkillSelectionMode
  skillRouterModelId?: EntityId
  externalMcpEnabled: boolean
  tokenBudget?: number
  documentScope?: string
  toolWhitelist: string[] | null
  maxIterations?: number
  executionTimeoutSeconds?: number
  status?: 0 | 1
}

export interface AgentSkillBinding {
  id: EntityId
  agentId: EntityId
  skillId: EntityId
  skillName: string
  skillVersionId: EntityId
  versionNo: number
  sha256: string
  enabled: boolean
}

export interface AgentMcpBinding {
  id: EntityId
  agentId: EntityId
  mcpServerId: EntityId
  serverKey: string
  displayName: string
  toolWhitelist: string[] | null
  enabled: boolean
}

export interface AgentMcpBindingInput {
  mcpServerId: EntityId
  toolWhitelist: string[] | null
}

export interface ModelOption {
  id: EntityId
  provider: string
  adapterType: string
  modelKey: string
  displayName: string
  contextWindow: number | null
  maxOutputTokens: number | null
  status: 'ENABLED' | 'DISABLED'
  description: string | null
}

export interface AgentOverviewStats {
  activeAgentCount: number | null
  activeSkillCount: number | null
  enabledMcpCount: number | null
}
