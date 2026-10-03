import type { EntityId } from '@/features/workspace/types'
import type { TaskLineageType } from './types'

export type TraceAvailability =
  | 'AVAILABLE'
  | 'NO_TRACE'
  | 'INVALID_TRACE_ID'
  | 'NOT_CONFIGURED'
  | 'NOT_FOUND_OR_NOT_SAMPLED'
  | 'RETENTION_WINDOW_ELAPSED'
  | 'BACKEND_UNAVAILABLE'
  | 'PAYLOAD_INVALID'
  | 'PAYLOAD_TOO_LARGE'
  | 'UNRELATED_TRACE'

// API 客户端保留长整数为字符串；Unix 微秒时间不能按 ID 或毫秒解释。
export type TraceMicroseconds = number | string

export interface TraceAttribute {
  key: string
  valueType: 'STRING' | 'LONG' | 'BOOLEAN'
  value: string
}

export interface TraceSpan {
  spanId: string
  parentSpanId: string | null
  name: string
  service: string
  category:
    | 'AGENT'
    | 'GEN_AI'
    | 'SKILL'
    | 'MCP'
    | 'TOOL'
    | 'DATABASE'
    | 'REDIS'
    | 'MESSAGING'
    | 'A2A'
    | 'GATEWAY'
    | 'HTTP'
    | 'TASK'
    | 'TECHNICAL'
  kind: string
  status: 'OK' | 'ERROR' | 'CANCELED' | 'UNKNOWN'
  startTimeMicros: TraceMicroseconds
  durationMicros: TraceMicroseconds
  attributes: TraceAttribute[]
}

export interface TaskTraceView {
  taskId: EntityId
  spaceId: EntityId
  traceId: string | null
  availabilityCode: TraceAvailability
  partial: boolean
  truncated: boolean
  startTimeMicros: TraceMicroseconds | null
  endTimeMicros: TraceMicroseconds | null
  durationMicros: TraceMicroseconds | null
  spanCount: number | null
  errorCount: number | null
  canceledCount: number | null
  retryCount: number | string | null
  services: Array<{
    service: string
    spanCount: number
    errorCount: number
    totalDurationMicros: TraceMicroseconds
  }>
  spans: TraceSpan[]
}

export interface ReplayEligibility {
  replayable: boolean
  reasonCode: string | null
  sourceTaskId: EntityId
  sourceExecutionId: EntityId | null
  rootTaskId: EntityId | null
  sourceLineage: TaskLineageType | null
  replayDepth: number | null
  inputSnapshotSchemaVersion: number | null
  inputSnapshotHash: string | null
  executionSnapshotSchemaVersion: number | null
  executionSnapshotHash: string | null
}

export interface ExecutionArtifact {
  id: EntityId
  taskId: EntityId
  executionId: EntityId
  sourceTaskId: EntityId | null
  sequenceNo: number
  sourceToolCallId: EntityId | null
  artifactType: 'CHANGE_PROPOSAL' | 'DRAFT_CHANGES' | 'RESULT_SUMMARY'
  schemaVersion: number
  payloadJson: string
  payloadSha256: string
  createdAt: string
}

export interface EvaluationTaskLink {
  taskId: EntityId
  spaceId: EntityId
  sourceTaskId: EntityId | null
  testCaseId: EntityId
  testCaseVersionId: EntityId
  caseRunId: EntityId
  caseAttemptId: EntityId
  runId: EntityId
  experimentId: EntityId | null
  variantKey: string | null
}
