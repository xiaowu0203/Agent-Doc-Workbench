import type { DocumentType, PageResult } from '@/features/document/types'
import type { SkillSelectionMode } from '@/features/agent/types'
import type { EntityId } from '@/features/workspace/types'

export type TaskStatus =
  | 'PENDING'
  | 'DISPATCHED'
  | 'RUNNING'
  | 'WAITING_INPUT'
  | 'WAITING_AUTH'
  | 'CANCELING'
  | 'COMPLETED'
  | 'TERMINATED'
  | 'FAILED'
export type TaskReadScope = 'FULL' | 'RANGES'

export interface TaskFocusRegion {
  start: number
  length: number
  textPreview: string | null
  instruction: string | null
}

export interface TaskListItem {
  id: EntityId
  taskNo: string
  spaceId: EntityId
  name: string
  status: TaskStatus
  agentId: EntityId
  agentName: string | null
  documentId: EntityId
  documentTitle: string | null
  documentType: DocumentType
  tokenBudget: number | null
  tokensUsed: number | null
  createdBy: EntityId | null
  creatorName: string | null
  startTime: string | null
  endTime: string | null
  createdAt: string
}

export type TaskPage = PageResult<TaskListItem>

export interface AgentTaskOption {
  id: EntityId
  name: string
  modelDisplayName: string | null
  skillSelectionMode: SkillSelectionMode
  tokenBudget: number | null
  executionTimeoutSeconds: number
  skillCount: number
  mcpCount: number
}

export interface TaskCreateOptions {
  spaceId: EntityId
  documentId: EntityId
  documentType: DocumentType
  documentVersion: number
  documentLength: number
  spaceTokenBudget: number | null
  agents: AgentTaskOption[]
}

export interface CreateTaskRequest {
  spaceId: EntityId
  agentId: EntityId
  documentId: EntityId
  name: string
  instruction: string
  tokenBudget: number | null
  readScope: TaskReadScope
  focusRegions: TaskFocusRegion[]
}

export interface CreatedTask {
  id: EntityId
  taskNo: string
}

export interface TaskDetail {
  id: EntityId
  taskNo: string
  spaceId: EntityId
  agentId: EntityId
  documentId: EntityId
  documentType: DocumentType
  name: string
  instruction: string
  status: TaskStatus
  tokenBudget: number | null
  readScope: TaskReadScope
  focusRegions: TaskFocusRegion[]
  tokensUsed: number | null
  startTime: string | null
  dispatchedAt: string | null
  lastHeartbeatAt: string | null
  endTime: string | null
  retryCount: number
  errorMessage: string | null
  resultSummary: string | null
  createdBy: EntityId | null
  createdAt: string
}

export interface TaskDraft extends Omit<
  CreateTaskRequest,
  'agentId' | 'documentId' | 'name' | 'instruction'
> {
  id: EntityId
  agentId: EntityId | null
  documentId: EntityId | null
  name: string | null
  instruction: string | null
  createdAt: string
  updatedAt: string
}
