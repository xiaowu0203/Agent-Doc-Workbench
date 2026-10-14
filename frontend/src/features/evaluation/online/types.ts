import type { EvaluationPage } from '../types'

/** 线上身份、计数和预算允许超过 JavaScript 安全整数，禁止经 Number 转换。 */
export type OnlineCounter = string | number | null
export type OnlineStatus = 'CREATED' | 'ACTIVE' | 'PAUSED' | 'STOPPING' | 'STOPPED'
export type OnlineAction = 'start' | 'pause' | 'resume' | 'stop' | 'emergency-stop'
export interface OnlineSummary {
  id: string
  spaceId: string
  agentId: string
  name: string
  status: string
  stateVersion: string | number
  manifestSchemaVersion: number | null
  manifestHash: string | null
  candidateWeightBps: number | null
  analysisMode: string | null
  documentCount: number | null
  participatingDocumentCount: OnlineCounter
  assignedTaskCount: number | null
  authorizedTokenBudget: string | null
  reservedTokenBudget: string | null
  consumedTokens: string | null
  baselineSlotCount: number | null
  candidateSlotCount: number | null
  unknownTaskCount: number | null
  reasonCodes: string[]
  lastReconciledAt: string | null
  createdBy: string
  createdAt: string | null
  updatedAt: string | null
}
export interface OnlineExperiment extends OnlineSummary {
  baseline: { id: string; hash: string; nonPromptHash: string; schemaVersion: number } | null
  candidate: { id: string; hash: string; nonPromptHash: string; schemaVersion: number } | null
  dependencyHash: string | null
  analysisPlan: {
    mode: string
    mdeAbsoluteRatio: string
    mdeReason: string
    humanQualityRequired: boolean
  } | null
  budgetPlan: Record<string, unknown> | null
  windowPlan: Record<string, unknown> | null
  protectionPlan: Record<string, unknown> | null
  activeSlot: number | null
  startedAt: string | null
  assignmentDeadline: string | null
  observationDeadline: string | null
  decision: string | null
}
export interface OnlinePreflight {
  experimentId: string
  manifestHash: string
  checkedAt: string
  expiresAt: string
  proofHash: string
  draftEligible: boolean
  startable: boolean
  baselineDocumentCount: number
  candidateDocumentCount: number
  srm: { status: string; pValue: string | null }
  plannedUpperTokenBudget: string
  historicalEstimate: string | null
  issues: { code: string; severity: string; documentId: string | null; ruleKey: string | null }[]
}
export interface OnlineAssignment {
  id: string
  experimentId: string
  spaceId: string
  taskId: string
  documentId: string
  variant: string
  acceptedSequence: OnlineCounter
  reservedTokenBudget: string | null
  consumedTokens: string | null
  taskConfirmationStatus: string | null
  dispatchStatus: string | null
  slotStatus: string | null
  cancelStatus: string | null
  settlementStatus: string | null
  executionId: string | null
  reasonCode: string | null
  factStatus: string | null
  createdAt: string | null
}
export interface OnlineRule {
  ruleKey: string
  role: 'PRIMARY' | 'AUXILIARY' | 'GUARDRAIL'
  evaluatorVersionId: string
  metricKey: string
  valueType: 'NUMBER' | 'BOOLEAN' | 'STRING'
  unit: string
  comparison: 'EQ' | 'GE' | 'LE'
  threshold: string | boolean
  evidenceTarget: 'ORIGINAL_TEXT' | 'ORIGINAL_CHANGE' | 'TASK_FACTS' | 'AUDIT_FACTS'
  expectedBindings: { documentId: string; expectedJson: Record<string, unknown> }[]
}
export interface OnlineCreateRequest {
  clientRequestKey: string
  spaceId: string
  agentId: string
  name: string
  documentIds: string[]
  candidateAgentPrompt: string
  candidateWeightBps: number
  authorizedTokenBudget: string
  maxTaskCount: number
  perTaskTokenLimit: string
  assignmentWindowSeconds: number
  completionObservationSeconds: number
  analysisPlan: {
    mode: 'ENGINEERING' | 'DESCRIPTIVE'
    mdeAbsoluteRatio: string
    mdeReason: string
    humanQualityRequired: boolean
  }
  rules: OnlineRule[]
}
export interface OnlineStateRequest {
  clientRequestKey: string
  manifestHash: string
  expectedStateVersion: string
  reason: string
}
export interface OnlineStartRequest extends Omit<OnlineStateRequest, 'reason'> {
  preflightProofHash: string
  liveSideEffectsAcknowledged: boolean
  retentionAcknowledged: boolean
  sharedResourcesAcknowledged: boolean
  emergencyCancellationAcknowledged: boolean
}
export interface OnlineActionResult {
  experimentId: string
  status: string
  stateVersion: string
  manifestHash: string
  reasonCode: string | null
}
export type OnlinePage<T> = EvaluationPage<T>

export type OnlineReviewTarget = 'TEXT' | 'CHANGE_SET'
export type OnlineBlindness = 'BLINDED' | 'NON_BLINDED' | 'UNKNOWN'
export interface OnlineReviewAssignment {
  assignmentId: string
  documentId: string
}
export interface OnlineOriginalContext {
  assignmentId: string
  taskId: string
  executionId: string
  documentId: string
  baseVersion: string | number
  inputHash: string
  inputContent: string
  originalEvidenceId?: string
  originalEvidenceHash?: string
  originalText?: string
  originalSetId?: string
  originalSetHash?: string
  originals?: {
    evidenceId: string
    evidenceType: string
    evidenceHash: string
    baseVersion: string | number
    outputVersion: string | number | null
    structureValid: boolean
    originalChanges: string | null
    originalContent: string | null
  }[]
}
export interface OnlineReviewContext {
  assignmentId: string
  knowledgeStatus: OnlineBlindness
  blindedEligible: boolean
  goal: { taskId: string; inputSnapshotHash: string; instruction: string }
  originalContext: OnlineOriginalContext
}
export interface OnlineQualityRequest {
  spaceId: string
  taskId: string
  executionId: string
  label: 'ACCEPTED' | 'REJECTED' | 'NEEDS_CHANGES' | 'NOT_APPLICABLE'
  score: number
  comment: string | null
  onlineQuality: {
    assignmentId: string
    originalEvidenceType: 'ORIGINAL_TEXT' | 'ORIGINAL_CHANGE_SET'
    originalEvidenceId: string
    originalEvidenceHash: string
    rubricScore: number
    blindness: OnlineBlindness
    supersedesFeedbackId: string | null
    reasonCodes: string[]
    seriousSafetyEvent: boolean
    safetyReason: string | null
  }
}
export interface OnlineQualityResult {
  id: string | number
  spaceId: string | number
  taskId: string | number
  executionId: string | number
  onlineQuality: OnlineQualityRequest['onlineQuality'] & { declaredBlindness: OnlineBlindness }
}

export interface OnlinePromptComparison {
  experimentId: string
  spaceId: string
  agentId: string
  schemaVersion: number
  baseline: NonNullable<OnlineExperiment['baseline']>
  candidate: NonNullable<OnlineExperiment['candidate']>
  dependencyHash: string
  baselineSystemPrompt: string
  candidateSystemPrompt: string
}
export interface OnlineQualityHistoryItem {
  id: string
  sourceHash: string
  supersedesFeedbackId: string | null
  rubricScore: number
  label: OnlineQualityRequest['label']
  blindness: OnlineBlindness
  declaredBlindness: OnlineBlindness
  ownFeedback: boolean
  comment: string | null
  reasonCodes: string[]
  seriousSafetyEvent: boolean
  safetyReason: string | null
  createdAt: string | null
}
export interface OnlineQualityHistory {
  schemaVersion: number
  spaceId: string
  experimentId: string
  assignmentId: string
  taskId: string
  executionId: string
  originalEvidenceType: string
  originalEvidenceId: string
  originalEvidenceHash: string
  currentTailFeedbackId: string | null
  currentTailBlindness: OnlineBlindness | null
  lastOwnFeedback: OnlineQualityHistoryItem | null
  history: OnlinePage<OnlineQualityHistoryItem>
}

export interface OnlineReportPayload {
  reportType: 'ONLINE_V1'
  schemaVersion: 2
  manifestHash: string
  assignmentCutoffSequence: string
  observationCutoffAt: string
  groups: Record<'BASELINE' | 'CANDIDATE', Record<string, unknown>>
  approvalFeedback: {
    groups: Record<'BASELINE' | 'CANDIDATE', Record<string, unknown>>
    unit: 'LATEST_IMPORTED_CHANGE_REQUEST'
    unrecordedMeaning: 'PENDING_OR_NOT_APPLICABLE_OR_NOT_IMPORTED'
  }
  actualWindows: Record<string, string | null>
  analysisPlan: OnlineExperiment['analysisPlan']
  primaryDifference: {
    candidateMinusBaselineRatio: string | null
    mdeAbsoluteRatio: string
    interpretation: string
  }
  srmDiagnostics: Record<'range' | 'runtime', { status: string; pValue: string | null }>
  ruleOutcomes: Record<string, Record<string, unknown>>
  taskRuleOutcomes: Record<string, Record<string, unknown>>
  budgetByCurrency: Record<string, string>
  feedbackStrata: Record<string, string | number>
  exceptions: string[]
  limitations: string[]
  decisionEligibility: {
    comparisonAllowed: boolean
    reasonCodes: string[]
    confirmedSafetyFeedbackIds: string[]
    safetyRejectedAllowed: boolean
  }
}
export interface OnlineReport {
  id: string
  experimentId: string
  revision: number
  reportType: string
  schemaVersion: number
  reportHash: string
  inputHash: string
  state: string
  report: { domain: string; schemaVersion: number; payload: OnlineReportPayload } | null
}
export interface OnlineRecalculateRequest {
  clientRequestKey: string
  assignmentCutoffSequence: string
  observationCutoffAt: string
}
export interface OnlineDecisionRequest {
  clientRequestKey: string
  reportRevision: number
  reportHash: string
  decision: 'ACCEPTED' | 'REJECTED' | 'INSUFFICIENT_EVIDENCE'
  reason: string
  safetyFeedbackId?: string
}
