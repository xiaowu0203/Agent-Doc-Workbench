import { request, requestRaw } from '@/api/client'
import type { TaskStatus, TaskExecutionMode } from '@/features/task/types'
import type {
  AuditLogPage,
  UsageDashboard,
  UsageDashboardQuery,
  UsageToolCallSearchItem,
} from '@/features/usage/types'
import type { PageResult } from '@/features/document/types'
import type { EntityId } from '@/features/workspace/types'

export function exportUsageRecords(payload: {
  spaceId: EntityId
  agentId?: EntityId
  modelId?: EntityId
  status?: TaskStatus
  executionMode?: TaskExecutionMode
  onlineParticipation?: boolean
  startedFrom?: string
  startedTo?: string
}): Promise<Blob> {
  return requestRaw<Blob>({
    method: 'POST',
    url: '/task/tasks/export',
    data: payload,
    responseType: 'blob',
  }).then((response) => response.data)
}

export function queryUsageDashboard(
  payload: UsageDashboardQuery,
  signal?: AbortSignal,
): Promise<UsageDashboard> {
  return request<UsageDashboard>({
    method: 'POST',
    url: '/task/token-usage/dashboard/query',
    data: payload,
    signal,
  })
}

export function searchUsageToolCalls(
  payload: Omit<UsageDashboardQuery, 'startDate' | 'endDate'> & {
    startAt: string
    endAt: string
    pageNum: number
    pageSize: number
  },
  signal?: AbortSignal,
): Promise<PageResult<UsageToolCallSearchItem>> {
  return request({ method: 'POST', url: '/task/tasks/tool-calls/search', data: payload, signal })
}

export function queryAuditLogs(
  payload: {
    spaceId: EntityId
    createdFrom?: string
    createdTo?: string
    pageNum: number
    pageSize: number
    targetType?: string
    targetId?: EntityId
  },
  signal?: AbortSignal,
): Promise<AuditLogPage> {
  return request<AuditLogPage>({
    method: 'POST',
    url: '/task/audit-logs/query',
    data: payload,
    signal,
  })
}
