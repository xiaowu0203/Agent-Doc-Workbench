import { request } from '@/api/client'
import type { EntityId } from '@/features/workspace/types'

export interface CreateTaskRequest {
  agentId: EntityId
  documentId: EntityId
  name: string
  instruction: string
  tokenBudget: number | null
}

export interface CreatedTask {
  id: EntityId
}

export function createTask(payload: CreateTaskRequest, signal?: AbortSignal): Promise<CreatedTask> {
  return request<CreatedTask>({
    method: 'POST',
    url: '/task/tasks',
    data: payload,
    signal,
  })
}
