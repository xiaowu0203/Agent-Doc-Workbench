import { request, requestRaw } from '@/api/client'
import type { TaskStatus } from '@/features/task/types'
import type { AuditLogPage, UsageDashboard, UsageDashboardQuery } from '@/features/usage/types'
import type { EntityId } from '@/features/workspace/types'

export function exportUsageRecords(payload: {
  spaceId: EntityId
  agentId?: EntityId
  modelId?: EntityId
  status?: TaskStatus
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

export function queryAuditLogs(
  payload: {
    spaceId: EntityId
    createdFrom: string
    createdTo: string
    pageNum: number
    pageSize: number
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
