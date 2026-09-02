import { request } from '@/api/client'
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
