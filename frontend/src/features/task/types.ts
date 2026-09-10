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
  tokensEstimated: boolean
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

export interface AgentExecutionAudit {
  id: EntityId
  workbenchTaskId: EntityId
  spaceId: EntityId
  agentId: EntityId
  agentName: string | null
  agentConfigVersion: number | null
  maxIterations: number | null
  executionTimeoutSeconds: number | null
  status: string
  cancelRequested: boolean
  promptHash: string | null
  executionSnapshotHash: string | null
  model: {
    id: EntityId | null
    modelKey: string | null
    displayName: string | null
    configVersion: number | null
  }
  skill: {
    configuredMode: SkillSelectionMode | null
    effectiveMode: SkillSelectionMode | null
    instructionHash: string | null
    routerModelId: EntityId | null
    routerDurationMs: number | null
    routerFallbackReason: string | null
    routerInputHash: string | null
    routerResponseHash: string | null
    boundSkills: Array<{
      skillId: EntityId
      skillVersionId: EntityId
      versionNo: number
      name: string
      activationDescription: string | null
      packageSha256: string | null
    }>
    selectedSkillVersionIds: EntityId[]
  }
  toolDefinitions: Array<{
    name: string
    source: string
    sourceKey: string | null
    mcpServerId: EntityId | null
  }>
  externalMcps: Array<{
    serverId: EntityId
    serverKey: string
    configVersion: number
    endpointSha256: string | null
    authType: string
    toolWhitelist: string[]
  }>
  modelCalls: Array<{
    sequenceNo: number
    modelId: EntityId
    modelConfigVersion: number
    modelKey: string
    maxOutputTokens: number | null
    temperature: number | null
    streaming: boolean
    messagesSha256: string | null
    messagesSize: number | null
    responseSha256: string | null
    responseSize: number | null
    status: string
    errorType: string | null
    startedAt: string
    finishedAt: string | null
  }>
  toolCalls: Array<{
    sequenceNo: number
    toolName: string
    toolSource: string
    toolSourceKey: string | null
    mcpServerId: EntityId | null
    skillVersionId: EntityId | null
    argumentsSha256: string | null
    argumentsSize: number | null
    resultSha256: string | null
    resultSize: number | null
    status: string
    errorType: string | null
    startedAt: string
    finishedAt: string | null
  }>
  inputTokens: number
  inputTokensEstimated: boolean
  cachedInputTokens: number | null
  cachedInputTokensEstimated: boolean
  outputTokens: number
  outputTokensEstimated: boolean
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
}

export interface TaskExecutionDetail {
  task: TaskDetail
  agentName: string | null
  tokensEstimated: boolean
  execution: AgentExecutionAudit | null
  output: {
    type: 'CHANGE_REQUEST' | 'DRAFT_DOCUMENT'
    id: EntityId
    status: string | null
    documentId: EntityId
  } | null
}

export interface TaskToolCallPageItem {
  taskId: EntityId
  toolCall: AgentExecutionAudit['toolCalls'][number]
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
