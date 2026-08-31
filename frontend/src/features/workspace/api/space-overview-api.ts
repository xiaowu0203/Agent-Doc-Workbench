import { request } from '@/api/client'
import type { EntityId } from '@/features/workspace/types'

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface RecentDocument {
  id: EntityId
  title: string
  docType: 'FORMAL' | 'DRAFT'
  updatedAt: string | null
  updatedByName: string | null
}

export interface DocumentStats {
  totalCount: number
  countAsOfLastMonth: number
}

export interface TaskStats {
  totalCount: number
  countAsOfYesterday: number
}

export interface PendingChangeStats {
  pendingCount: number
  pendingCountAsOfYesterday: number
}

export interface MonthlyTokenBudget {
  usedTokens: number
  tokenBudget: number | null
}

export interface TaskSummary {
  id: EntityId
  agentId: EntityId
  documentId: EntityId
  name: string
  status:
    | 'PENDING'
    | 'RUNNING'
    | 'COMPLETED'
    | 'TERMINATED'
    | 'FAILED'
    | 'DISPATCHED'
    | 'WAITING_INPUT'
    | 'WAITING_AUTH'
    | 'CANCELING'
  tokensUsed: number
  createdAt: string
}

export interface TaskActivitySummary {
  id: EntityId
  name: string
  agentId: EntityId
  status: TaskSummary['status']
  operatorName: string | null
  activityAt: string | null
}

export interface ChangeRequestSummary {
  id: EntityId
  documentId: EntityId
  documentTitle: string
  requestType: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'MERGED' | 'RETURNED'
  createdAt: string
}

export interface TokenUsageToday {
  spaceId: EntityId
  inputTokens: number | null
  outputTokens: number | null
  tokens: number | null
  inputTokensEstimated: boolean
  outputTokensEstimated: boolean
}

export interface AgentOverviewStats {
  activeAgentCount: number | null
  activeSkillCount: number | null
  enabledMcpCount: number | null
}

export function listRecentDocuments(
  spaceId: EntityId,
  signal?: AbortSignal,
  pageSize = 5,
  pageNum = 1,
): Promise<PageResult<RecentDocument>> {
  return request<PageResult<RecentDocument>>({
    method: 'POST',
    url: '/document/documents/recent/query',
    data: {
      spaceId,
      pageParam: { pageNum, pageSize },
    },
    signal,
  }).then(
    (page) =>
      page ?? {
        records: [],
        total: 0,
        pageNum,
        pageSize,
      },
  )
}

export function getDocumentStats(
  spaceId: EntityId,
  signal?: AbortSignal,
): Promise<DocumentStats> {
  return request<DocumentStats>({
    method: 'GET',
    url: `/document/spaces/${spaceId}/document-stats`,
    signal,
  }).then(
    (stats) => stats ?? { totalCount: 0, countAsOfLastMonth: 0 },
  )
}

export function listTaskActivities(
  spaceId: EntityId,
  signal?: AbortSignal,
  pageSize = 3,
): Promise<PageResult<TaskActivitySummary>> {
  return request<PageResult<TaskActivitySummary>>({
    method: 'POST',
    url: '/task/tasks/activity/query',
    data: {
      spaceId,
      pageParam: { pageNum: 1, pageSize },
    },
    signal,
  }).then(
    (page) =>
      page ?? {
        records: [],
        total: 0,
        pageNum: 1,
        pageSize,
      },
  )
}

export function getTaskStats(spaceId: EntityId, signal?: AbortSignal): Promise<TaskStats> {
  return request<TaskStats>({
    method: 'GET',
    url: '/task/tasks/stats',
    params: { spaceId },
    signal,
  }).then((stats) => stats ?? { totalCount: 0, countAsOfYesterday: 0 })
}

export function listPendingChanges(
  spaceId: EntityId,
  signal?: AbortSignal,
): Promise<PageResult<ChangeRequestSummary>> {
  return request<PageResult<ChangeRequestSummary>>({
    method: 'POST',
    url: '/task/change-requests/query',
    data: {
      spaceId,
      status: 'PENDING',
      pageParam: { pageNum: 1, pageSize: 3 },
    },
    signal,
  }).then(
    (page) =>
      page ?? {
        records: [],
        total: 0,
        pageNum: 1,
        pageSize: 3,
      },
  )
}

export function getPendingChangeStats(
  spaceId: EntityId,
  signal?: AbortSignal,
): Promise<PendingChangeStats> {
  return request<PendingChangeStats>({
    method: 'GET',
    url: '/task/change-requests/stats',
    params: { spaceId },
    signal,
  }).then(
    (stats) => stats ?? { pendingCount: 0, pendingCountAsOfYesterday: 0 },
  )
}

export function getTodayTokenUsage(
  spaceId: EntityId,
  signal?: AbortSignal,
): Promise<TokenUsageToday> {
  return request<TokenUsageToday>({
    method: 'GET',
    url: '/task/token-usage/today',
    params: { spaceId },
    signal,
  })
}

export function getMonthlyTokenBudget(
  spaceId: EntityId,
  signal?: AbortSignal,
): Promise<MonthlyTokenBudget> {
  return request<MonthlyTokenBudget>({
    method: 'GET',
    url: '/task/token-usage/monthly',
    params: { spaceId },
    signal,
  }).then((stats) => stats ?? { usedTokens: 0, tokenBudget: null })
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
  }).then(
    (stats) =>
      stats ?? {
        activeAgentCount: null,
        activeSkillCount: null,
        enabledMcpCount: null,
      },
  )
}
