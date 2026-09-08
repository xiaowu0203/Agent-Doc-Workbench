import { request } from '@/api/client'
import type { AuditLogPage, UsageDashboard, UsageDashboardQuery } from '@/features/usage/types'
import type { EntityId } from '@/features/workspace/types'

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
