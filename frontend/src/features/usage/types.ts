import type { PageResult } from '@/features/document/types'
import type { TaskStatus } from '@/features/task/types'
import type { EntityId } from '@/features/workspace/types'

export interface UsageSummary {
  hasData: boolean
  inputTokens: number | null
  outputTokens: number | null
  tokens: number | null
  estimatedCost: number | null
  executedTasks: number
  toolCalls: number
  inputTokensEstimated: boolean
  outputTokensEstimated: boolean
  hasIncompleteData: boolean
}

export interface DailyUsage {
  usageDate: string
  hasData: boolean
  inputTokens: number | null
  outputTokens: number | null
  tokens: number | null
  estimatedCost: number | null
  inputTokensEstimated: boolean
  outputTokensEstimated: boolean
  hasIncompleteData: boolean
}

export type ToolSource = 'WORKBENCH_MCP' | 'EXTERNAL_MCP' | 'SKILL_LOCAL'

export interface ToolSourceCount {
  source: ToolSource
  calls: number
}

export interface UsageDashboard {
  startDate: string
  endDate: string
  calculatedAt: string
  timeZone: string
  summary: UsageSummary
  previousSummary: UsageSummary
  trend: DailyUsage[]
  toolSources: ToolSourceCount[]
  monthlyUsedTokens: number
  monthlyTokenBudget: number | null
}

export interface UsageDashboardQuery {
  spaceId: EntityId
  startDate: string
  endDate: string
  agentId?: EntityId
  modelId?: EntityId
  status?: TaskStatus
}

export interface AuditLogItem {
  id: EntityId
  spaceId: EntityId
  taskId: EntityId | null
  actorType: 1 | 2
  actorId: EntityId
  actorName: string
  action: string
  actionName: string
  targetType: string
  targetTypeName: string
  targetId: EntityId | null
  detail: string | null
  traceId: string | null
  createdAt: string
}

export type AuditLogPage = PageResult<AuditLogItem>
