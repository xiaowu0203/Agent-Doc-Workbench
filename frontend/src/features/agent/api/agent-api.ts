import { request } from '@/api/client'
import type {
  AgentDetail,
  AgentInput,
  AgentMcpBinding,
  AgentMcpBindingInput,
  AgentOverviewStats,
  AgentPage,
  AgentSkillBinding,
  AgentStatus,
  ModelOption,
} from '@/features/agent/types'
import type { EntityId } from '@/features/workspace/types'

export interface AgentOption {
  id: EntityId
  spaceId: EntityId
  name: string
  status: 'ENABLED' | 'DISABLED'
}

export function listAgents(spaceId: EntityId, signal?: AbortSignal): Promise<AgentOption[]> {
  return request<AgentOption[]>({
    method: 'GET',
    url: '/agent/agents',
    params: { spaceId },
    signal,
  }).then((agents) => agents ?? [])
}

export function searchAgents(
  spaceId: EntityId,
  options: {
    keyword?: string
    status?: AgentStatus
    modelId?: EntityId
    pageNum?: number
    pageSize?: number
    signal?: AbortSignal
  } = {},
): Promise<AgentPage> {
  return request<AgentPage>({
    method: 'POST',
    url: '/agent/agents/search',
    data: {
      spaceId,
      keyword: options.keyword || undefined,
      status: options.status === 'ENABLED' ? 1 : options.status === 'DISABLED' ? 0 : undefined,
      modelId: options.modelId,
      pageNum: options.pageNum ?? 1,
      pageSize: options.pageSize ?? 9,
    },
    signal: options.signal,
  }).then(
    (page) =>
      page ?? {
        records: [],
        total: 0,
        pageNum: options.pageNum ?? 1,
        pageSize: options.pageSize ?? 9,
      },
  )
}

export function getAgent(agentId: EntityId): Promise<AgentDetail> {
  return request<AgentDetail>({ method: 'GET', url: `/agent/agents/${agentId}` })
}

export function createAgent(payload: AgentInput): Promise<AgentDetail> {
  return request<AgentDetail>({ method: 'POST', url: '/agent/agents', data: payload })
}

export function updateAgent(agentId: EntityId, payload: AgentInput): Promise<AgentDetail> {
  return request<AgentDetail>({ method: 'PUT', url: `/agent/agents/${agentId}`, data: payload })
}

export function deleteAgent(agentId: EntityId): Promise<void> {
  return request<void>({ method: 'DELETE', url: `/agent/agents/${agentId}` })
}

export function listModels(enabledOnly = true): Promise<ModelOption[]> {
  return request<ModelOption[]>({
    method: 'GET',
    url: '/agent/models/options',
    params: { enabledOnly },
  }).then((models) => models ?? [])
}

export function getAgentOverviewStats(
  spaceId: EntityId,
  signal?: AbortSignal,
): Promise<AgentOverviewStats> {
  return request<AgentOverviewStats>({
    method: 'GET',
    url: '/agent/overview/stats',
    params: { spaceId },
    signal,
  })
}

export function listAgentSkills(agentId: EntityId): Promise<AgentSkillBinding[]> {
  return request<AgentSkillBinding[]>({
    method: 'GET',
    url: `/agent/agents/${agentId}/skills`,
  }).then((bindings) => bindings ?? [])
}

export function replaceAgentSkills(
  agentId: EntityId,
  skillVersionIds: EntityId[],
): Promise<AgentSkillBinding[]> {
  return request<AgentSkillBinding[]>({
    method: 'PUT',
    url: `/agent/agents/${agentId}/skills`,
    data: { skillVersionIds },
  }).then((bindings) => bindings ?? [])
}

export function listAgentMcpBindings(agentId: EntityId): Promise<AgentMcpBinding[]> {
  return request<AgentMcpBinding[]>({
    method: 'GET',
    url: `/agent/agents/${agentId}/mcp-bindings`,
  }).then((bindings) => bindings ?? [])
}

export function replaceAgentMcpBindings(
  agentId: EntityId,
  bindings: AgentMcpBindingInput[],
): Promise<AgentMcpBinding[]> {
  return request<AgentMcpBinding[]>({
    method: 'PUT',
    url: `/agent/agents/${agentId}/mcp-bindings`,
    data: { bindings },
  }).then((result) => result ?? [])
}

export function toAgentUpdateInput(agent: AgentDetail, status?: AgentStatus): AgentInput {
  return {
    name: agent.name,
    description: agent.description || undefined,
    systemPrompt: agent.systemPrompt,
    modelId: agent.modelId,
    skillSelectionMode: agent.skillSelectionMode,
    skillRouterModelId: agent.skillRouterModelId || undefined,
    externalMcpEnabled: agent.externalMcpEnabled,
    tokenBudget: agent.tokenBudget || undefined,
    documentScope: agent.documentScope || undefined,
    toolWhitelist: agent.toolWhitelist,
    maxIterations: agent.maxIterations,
    executionTimeoutSeconds: agent.executionTimeoutSeconds,
    status: (status ?? agent.status) === 'ENABLED' ? 1 : 0,
  }
}
