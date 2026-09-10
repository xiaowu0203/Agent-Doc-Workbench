import type { EntityId } from '@/features/workspace/types'

export type ChangeRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'MERGED' | 'RETURNED'
export type ChangeRequestResolutionType = 'ALL' | 'PARTIAL' | 'EDITED'
export type ChangeRequestActorType = 'HUMAN' | 'AGENT'

export interface ChangeRequestListItem {
  id: EntityId
  documentId: EntityId
  documentTitle: string | null
  status: ChangeRequestStatus
  summary: string | null
  sourceTaskId: EntityId | null
  taskNo: string | null
  taskName: string | null
  agentId: EntityId | null
  agentName: string | null
  assignedReviewerId: EntityId | null
  assignedReviewerName: string | null
  revisionNo: number
  createdAt: string
  updatedAt: string
}

export interface ChangeRequestPage {
  records: ChangeRequestListItem[]
  total: number
  pageNum: number
  pageSize: number
}

export interface ChangeRequestStats {
  pendingCount: number
  pendingCountAsOfYesterday: number
  assignedToMeCount: number
  unassignedCount: number
}

export interface ChangeRequestComment {
  id: EntityId
  changeKey: string | null
  authorId: EntityId
  authorName: string | null
  content: string
  createdAt: string
}

export interface ChangeRequestAudit {
  id: EntityId
  actorType: ChangeRequestActorType
  actorId: EntityId
  actorName: string | null
  action: string
  detail: string | null
  traceId: string | null
  createdAt: string
}

export interface ChangeRequestDetail extends ChangeRequestListItem {
  spaceId: EntityId
  requestType: 'FORMAL' | 'DRAFT'
  changes: Array<{ op: 'REPLACE' | 'APPEND'; oldText: string | null; newText: string }>
  baseVersion: number
  baseVersionCreatedAt: string | null
  currentVersion: number
  expectedVersion: number | null
  baseContent: string
  proposedContent: string
  resolvedContent: string | null
  conflicted: boolean
  tokensUsed: number | null
  tokensEstimated: boolean | null
  taskResultSummary: string | null
  proposedBy: EntityId
  proposedActorType: ChangeRequestActorType
  proposedByName: string | null
  triggeredBy: EntityId | null
  triggeredByName: string | null
  reviewComment: string | null
  reviewedBy: EntityId | null
  reviewedByName: string | null
  reviewedAt: string | null
  resolutionType: ChangeRequestResolutionType | null
  acceptedChangeKeys: string[]
  mergedBy: EntityId | null
  mergedByName: string | null
  mergedAt: string | null
  mergedVersion: number | null
  parentRequestId: EntityId | null
  reworkTaskId: EntityId | null
  comments: ChangeRequestComment[]
  auditTrail: ChangeRequestAudit[]
}

export interface BatchChangeRequestResult {
  succeededIds: EntityId[]
  failures: Array<{ id: EntityId; code: number; message: string }>
}

export interface ChangeRequestDecision {
  resolutionType: ChangeRequestResolutionType
  reviewComment?: string
  acceptedChangeKeys?: string[]
  resolvedContent?: string
}
