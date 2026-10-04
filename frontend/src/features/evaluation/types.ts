import type { EntityId } from '@/features/workspace/types'

export type EvaluationAttemptStatus =
  | 'CREATED'
  | 'REPLAY_CREATED'
  | 'REPLAY_RUNNING'
  | 'EVALUATING'
  | 'COMPLETED'
  | 'REPLAY_FAILED'
  | 'EVALUATOR_FAILED'
  | 'CANCEL_PENDING'
  | 'CANCELED'

export type EvaluationEvidenceType =
  | 'TASK'
  | 'AGENT_EXECUTION'
  | 'EXECUTION_ARTIFACT'
  | 'TOKEN_LEDGER'
  | 'TRACE'
  | 'CHANGE_REQUEST'
  | 'DOCUMENT_VERSION'
  | 'AUDIT_LOG'

export type EvaluationFeedbackLabel = 'ACCEPTED' | 'REJECTED' | 'NEEDS_CHANGES' | 'NOT_APPLICABLE'

export type EvaluationFeedbackSourceType = 'MANUAL' | 'CHANGE_REQUEST'

export type EvaluationMetricDirection = 'HIGHER_IS_BETTER' | 'LOWER_IS_BETTER' | 'NEUTRAL'

export type EvaluationMetricSelection = 'EFFECTIVE' | 'HISTORY'

export type EvaluationMetricSource = 'EVALUATOR' | 'EXECUTION'

export type EvaluationMetricValueType = 'NUMBER' | 'BOOLEAN' | 'STRING'

export type EvaluationPauseReason =
  'DISPATCH_UNAVAILABLE' | 'AUTHORIZATION_EXPIRED' | 'RECONCILIATION_BLOCKED'

export type EvaluationResultStatus = 'PASSED' | 'FAILED' | 'ERROR' | 'SKIPPED'

export type EvaluationRunStatus =
  | 'CREATED'
  | 'DISPATCHING'
  | 'RUNNING'
  | 'PAUSED'
  | 'CANCEL_PENDING'
  | 'COMPLETED'
  | 'COMPLETED_WITH_ERRORS'
  | 'CANCELED'
  | 'FAILED'

export type EvaluationVersionStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export type ExperimentDecision = 'ACCEPTED' | 'REJECTED' | 'INSUFFICIENT_EVIDENCE'

export type ExperimentReportCompatibility =
  | 'SUPPORTED'
  | 'SCHEMA_MISSING'
  | 'SCHEMA_INVALID'
  | 'SCHEMA_UNSUPPORTED'
  | 'PAYLOAD_INVALID'
  | 'CONTENT_HASH_MISMATCH'

export type ExperimentStatus =
  | 'CREATED'
  | 'STARTING'
  | 'RUNNING'
  | 'PAUSED'
  | 'CANCEL_PENDING'
  | 'COMPLETED'
  | 'COMPLETED_WITH_ERRORS'
  | 'CANCELED'
  | 'FAILED'

export type ExperimentVariantRole = 'BASELINE' | 'CANDIDATE'

export type ExperimentVariantType = 'PROMPT'

export interface EvaluationPage<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface EvaluationPagination {
  pageNum?: number
  pageSize?: number
}

export interface EvaluationResourceSearch extends EvaluationPagination {
  spaceId: EntityId
  keyword?: string
  archived?: boolean
}

export interface EvaluationVersionSearch extends EvaluationPagination {
  spaceId: EntityId
  parentId?: EntityId
  status?: EvaluationVersionStatus
}

export interface EvaluationRunSearch extends EvaluationPagination {
  spaceId: EntityId
  status?: EvaluationRunStatus
  datasetVersionId?: EntityId
  singleTestCaseVersionId?: EntityId
  experimentVariantId?: EntityId
  createdFrom?: string
  createdTo?: string
  startedFrom?: string
  startedTo?: string
}

export interface ExperimentSearch extends EvaluationPagination {
  spaceId: EntityId
  status?: ExperimentStatus
  datasetVersionId?: EntityId
  createdFrom?: string
  createdTo?: string
  startedFrom?: string
  startedTo?: string
}

export interface MetricSearch extends EvaluationPagination {
  spaceId: EntityId
  runIds?: EntityId[]
  testCaseVersionIds?: EntityId[]
  metricKeys?: string[]
  sources?: EvaluationMetricSource[]
  selection?: EvaluationMetricSelection
}

export interface MetricComparisonQueryRequest {
  spaceId: EntityId
  runIds: EntityId[]
  testCaseVersionIds?: EntityId[] | null
  metricKeys?: string[] | null
}

export interface MetricComparisonInput {
  rows: MetricComparisonRow[]
}

export interface MetricComparisonRow {
  testCaseVersionId: EntityId | null
  metricKey: string
  contractVersion: number | null
  valueType: EvaluationMetricValueType
  unit: string | null
  direction: EvaluationMetricDirection | null
  source: EvaluationMetricSource | null
  values: MetricComparisonValue[]
}

export interface MetricComparisonValue {
  runId: EntityId | null
  metric: StandardMetric | null
  missingReason: string | null
}

export interface MetricEvidenceReference {
  id: EntityId
  evidenceType: EvaluationEvidenceType
  businessId: string | null
  contentHash: string | null
  summary: string | null
  locatorJson: string | null
}

export interface StandardMetric {
  id: EntityId
  spaceId: EntityId
  runId: EntityId | null
  caseRunId: EntityId | null
  caseAttemptId: EntityId | null
  testCaseVersionId: EntityId | null
  evaluationResultId: EntityId | null
  evaluatorVersionId: EntityId | null
  contractVersion: number | null
  source: EvaluationMetricSource | null
  producerId: EntityId | null
  metricKey: string
  valueType: EvaluationMetricValueType
  numericValue: number | null
  booleanValue: boolean | null
  stringValue: string | null
  unit: string | null
  direction: EvaluationMetricDirection | null
  createdAt: string | null
  evidence: MetricEvidenceReference[]
}

export interface DatasetCaseBindingRequest {
  testCaseVersionId: EntityId
  sortOrder: number
  enabled?: boolean | null
}

export interface DatasetCaseBindingsRequest {
  cases: DatasetCaseBindingRequest[]
}

export interface DatasetVersionCreateRequest {
  datasetId: EntityId
}

export interface EvaluationDatasetCreateRequest {
  spaceId: EntityId
  name: string
  description?: string | null
}

export interface EvaluationFeedbackCreateRequest {
  spaceId: EntityId
  caseRunId?: EntityId | null
  taskId?: EntityId | null
  executionId?: EntityId | null
  label: EvaluationFeedbackLabel
  score?: number | null
  comment?: string | null
}

export interface EvaluationRetryRequest {
  evaluatorVersionIds?: EntityId[] | null
  workerCapabilityTtlSeconds: number
}

export interface EvaluationRunCreateRequest {
  spaceId: EntityId
  datasetVersionId?: EntityId | null
  singleTestCaseVersionId?: EntityId | null
  workerCapabilityTtlSeconds: number
}

export interface EvaluationRunResumeRequest {
  workerCapabilityTtlSeconds: number
}

export interface EvaluationTestCaseCreateRequest {
  spaceId: EntityId
  name: string
  description?: string | null
}

export interface EvaluatorCreateRequest {
  spaceId: EntityId
  name: string
  evaluatorKey: string
  description?: string | null
}

export interface EvaluatorVersionCreateRequest {
  evaluatorId: EntityId
  configSchemaVersion: number
  configJson: string
  resultSchemaVersion: number
}

export type EvaluatorVersionUpdateRequest = Omit<EvaluatorVersionCreateRequest, 'evaluatorId'>
export type TestCaseVersionUpdateRequest = Omit<
  TestCaseVersionCreateRequest,
  'testCaseId' | 'sourceTaskId'
>

export interface ExperimentCreateRequest {
  spaceId: EntityId
  clientRequestKey: string
  datasetVersionId: EntityId
  candidateVariants: PromptCandidateCreateRequest[]
}

export interface ExperimentDecisionRequest {
  reportRevision: number
  decision: ExperimentDecision
  reason: string
}

export interface ExperimentReportRecalculateRequest {
  clientRequestKey: string
}

export interface ExperimentStartRequest {
  authorizedTokenBudget: number
  workerCapabilityTtlSeconds: number
}

export interface PromptCandidateCreateRequest {
  variantKey: string
  agentPrompt: string
}

export interface TestCaseEvaluatorBindingRequest {
  evaluatorVersionId: EntityId
  expectedJson?: string | null
  sortOrder: number
}

export interface TestCaseEvaluatorBindingsRequest {
  evaluators: TestCaseEvaluatorBindingRequest[]
}

export interface TestCaseVersionCreateRequest {
  testCaseId: EntityId
  sourceTaskId: EntityId
  expectedSchemaVersion: number
  expectedJson: string
  sourceType: string
  sanitizationNote?: string | null
}

export interface DatasetCaseBinding {
  id: EntityId
  testCaseVersionId: EntityId | null
  testCaseId: EntityId | null
  testCaseName: string | null
  versionNo: number
  status: EvaluationVersionStatus
  sortOrder: number
  enabled: boolean
}

export interface DatasetVersion {
  id: EntityId
  datasetId: EntityId | null
  spaceId: EntityId
  versionNo: number
  status: EvaluationVersionStatus
  contentHash: string | null
  publishedAt: string | null
  createdBy: EntityId | null
}

export interface EvaluationCaseAttemptHistory {
  id: EntityId
  attemptNo: number
  currentCaseAttempt: boolean
  replayTaskId: EntityId | null
  executionTaskId: EntityId | null
  status: EvaluationAttemptStatus
  failureStage: string | null
  failureCode: string | null
  failureMessage: string | null
  startedAt: string | null
  finishedAt: string | null
  updatedAt: string | null
  results: EvaluationResultSummary[]
}

export interface EvaluationCaseRun {
  id: EntityId
  testCaseVersionId: EntityId | null
  status: EvaluationAttemptStatus
  currentAttemptId: EntityId | null
  executionTaskId: EntityId | null
  attemptNo: number
  feedback: EvaluationFeedback[]
}

export interface EvaluationDataset {
  id: EntityId
  spaceId: EntityId
  name: string
  description: string | null
  archived: boolean
  createdBy: EntityId | null
  createdAt: string | null
}

export interface EvaluationFeedback {
  id: EntityId
  spaceId: EntityId
  runId: EntityId | null
  caseRunId: EntityId | null
  taskId: EntityId | null
  executionId: EntityId | null
  sourceType: EvaluationFeedbackSourceType | null
  sourceBusinessId: string | null
  sourceHash: string | null
  label: EvaluationFeedbackLabel | null
  score: number | null
  comment: string | null
  factsJson: string | null
  createdBy: EntityId | null
  createdAt: string | null
}

export interface EvaluationResultDetail {
  id: EntityId
  spaceId: EntityId
  runId: EntityId | null
  caseAttemptId: EntityId | null
  evaluatorVersionId: EntityId | null
  evaluationAttemptNo: number
  status: EvaluationResultStatus
  score: number | null
  summaryCode: string | null
  detailsJson: string | null
  implementationVersion: string | null
  traceId: string | null
  spanId: string | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string | null
  metrics: StandardMetric[]
  evidence: MetricEvidenceReference[]
  feedback: EvaluationFeedback[]
}

export interface EvaluationResultSummary {
  id: EntityId
  evaluatorVersionId: EntityId | null
  evaluatorName: string | null
  evaluatorVersionNo: number | null
  evaluationAttemptNo: number
  currentEvaluationResultAttempt: boolean
  status: EvaluationResultStatus
  score: number | null
  summaryCode: string | null
  traceId: string | null
  spanId: string | null
  startedAt: string | null
  finishedAt: string | null
}

export interface EvaluationRunSummary {
  id: EntityId
  spaceId: EntityId
  datasetVersionId: EntityId | null
  datasetName: string | null
  datasetVersionNo: number | null
  singleTestCaseVersionId: EntityId | null
  testCaseName: string | null
  testCaseVersionNo: number | null
  experimentVariantId: EntityId | null
  status: EvaluationRunStatus
  pauseReason: EvaluationPauseReason | null
  cancelRequested: boolean | null
  caseCount: number
  completedCaseCount: number | null
  errorCaseCount: number | null
  createdBy: EntityId | null
  createdAt: string | null
  startedAt: string | null
  finishedAt: string | null
  updatedAt: string | null
}

export interface EvaluationRun {
  id: EntityId
  spaceId: EntityId
  datasetVersionId: EntityId | null
  singleTestCaseVersionId: EntityId | null
  status: EvaluationRunStatus
  pauseReason: EvaluationPauseReason | null
  cancelRequested: boolean | null
  caseCount: number
  reconciliationFailureCount: number | null
  startedAt: string | null
  finishedAt: string | null
  cases: EvaluationCaseRun[]
}

export interface EvaluationTestCase {
  id: EntityId
  spaceId: EntityId
  name: string
  description: string | null
  archived: boolean
  createdBy: EntityId | null
}

export interface EvaluatorVersion {
  id: EntityId
  evaluatorId: EntityId | null
  spaceId: EntityId
  versionNo: number
  status: EvaluationVersionStatus
  evaluatorKey: string | null
  configSchemaVersion: number | null
  configJson: string | null
  resultSchemaVersion: number | null
  implementationVersion: string | null
  contentHash: string | null
  publishedAt: string | null
  createdBy: EntityId | null
}

export interface Evaluator {
  id: EntityId
  spaceId: EntityId
  name: string
  evaluatorKey: string | null
  description: string | null
  archived: boolean
  createdBy: EntityId | null
}

export interface ExperimentPreflightIssue {
  testCaseVersionId: EntityId | null
  code: string | null
  detailReasonCode: string | null
}

export interface ExperimentPreflight {
  experimentId: EntityId
  caseCount: number
  variantCount: number
  plannedTaskCount: number
  plannedTokenBudget: number
  eligible: boolean
  issues: ExperimentPreflightIssue[]
}

export interface ExperimentReportRevision {
  experimentId: EntityId
  revision: number
  schemaVersion: number | null
  contentHash: string | null
  generatedBy: EntityId | null
  generatedAt: string | null
}

export interface ExperimentReportSelectedRecordIds {
  runIds: EntityId[]
  attemptIds: EntityId[]
  resultIds: EntityId[]
  metricIds: EntityId[]
  evidenceIds: EntityId[]
  feedbackIds: EntityId[]
}

export interface ExperimentReportContent {
  expectedCaseCount: number
  variantCount: number
  cases: ExperimentReportCaseSelection[]
  cells: ExperimentReportCell[]
  summaries: ExperimentReportVariantSummary[]
  comparisons: ExperimentReportPairComparison[]
  metricEvidence: ExperimentReportMetricEvidence[]
  feedback: ExperimentReportFeedback[]
  feedbackCoverage: ExperimentReportFeedbackCoverage[]
  authorizedTokenBudget: number | null
  actualTokenUsage: number | null
  budgetOverrun: number | null
}

export interface ExperimentReportCaseSelection {
  variantKey: string
  runId: EntityId | null
  testCaseVersionId: EntityId | null
  caseRunId: EntityId | null
  attemptId: EntityId | null
  taskId: EntityId | null
  attemptStatus: EvaluationAttemptStatus | null
  failureCode: string | null
}

export interface ExperimentReportCell {
  value: ExperimentReportMetricValue
  evidenceIds: EntityId[]
}

export interface ExperimentReportMetricValue {
  testCaseVersionId: EntityId | null
  metricKey: string
  evaluatorVersionId: EntityId | null
  variantKey: string
  runId: EntityId | null
  metricId: EntityId | null
  numericValue: number | null
  booleanValue: boolean | null
  stringValue: string | null
  unit: string | null
  missingReason: string | null
}

export interface ExperimentReportVariantSummary {
  variantKey: string
  metricKey: string
  expectedCount: number
  validCount: number
  missingCount: number
  trueCount: number
  falseCount: number
  numericTotalsByUnit: Record<string, number>
  numericMeansByUnit: Record<string, number>
  missingReasons: Record<string, number>
}

export interface ExperimentReportPairComparison {
  candidateVariantKey: string | null
  metricKey: string
  pairedCount: number
  incomparableCurrencyCount: number
  improvedCount: number
  worsenedCount: number
  unchangedCount: number
  meanCandidateMinusBaseline: number | null
  meanCandidateMinusBaselineByCurrency: Record<string, number>
}

export interface ExperimentReportMetricEvidence {
  metricId: EntityId | null
  evidenceId: EntityId | null
  evidenceType: EvaluationEvidenceType
  businessId: string | null
  contentHash: string | null
}

export interface ExperimentReportFeedback {
  id: EntityId
  variantKey: string
  testCaseVersionId: EntityId | null
  sourceType: EvaluationFeedbackSourceType | null
  sourceBusinessId: string | null
  label: EvaluationFeedbackLabel | null
  score: number | null
}

export interface ExperimentReportFeedbackCoverage {
  variantKey: string
  sourceType: EvaluationFeedbackSourceType | null
  coveredCaseCount: number
}

export interface ExperimentReport {
  experimentId: EntityId
  revision: number
  schemaVersion: number | null
  manifestHash: string | null
  calculationInputHash: string | null
  selectedRecordIds: ExperimentReportSelectedRecordIds | null
  report: ExperimentReportContent | null
  contentHash: string | null
  generatedBy: EntityId | null
  generatedAt: string | null
  compatible: boolean
  compatibilityCode: ExperimentReportCompatibility
}

export interface ExperimentSummary {
  id: EntityId
  spaceId: EntityId
  datasetVersionId: EntityId | null
  datasetName: string | null
  datasetVersionNo: number | null
  status: ExperimentStatus
  variantCount: number
  linkedRunCount: number | null
  authorizedTokenBudget: number | null
  failureCode: string | null
  decision: ExperimentDecision | null
  decisionReportRevision: number | null
  createdBy: EntityId | null
  createdAt: string | null
  startedAt: string | null
  finishedAt: string | null
  updatedAt: string | null
}

export interface ExperimentVariant {
  id: EntityId
  experimentId: EntityId
  variantKey: string
  role: ExperimentVariantRole
  type: ExperimentVariantType
  candidateConfigRef: EntityId | null
  sourceSnapshotSchemaVersion: number | null
  sourceSnapshotHash: string | null
  candidateSnapshotSchemaVersion: number | null
  candidateSnapshotHash: string | null
  promptDiffFieldPaths: string[]
  snapshotWithoutPromptHash: string | null
  evaluationRunId: EntityId | null
}

export interface Experiment {
  id: EntityId
  spaceId: EntityId
  datasetVersionId: EntityId | null
  status: ExperimentStatus
  manifestSchemaVersion: number | null
  manifestHash: string | null
  authorizedTokenBudget: number | null
  actualTokenUsage: number | null
  budgetOverrun: number | null
  failureCode: string | null
  failureMessage: string | null
  createdBy: EntityId | null
  startedBy: EntityId | null
  startedAt: string | null
  cancelRequestedBy: EntityId | null
  cancelRequestedAt: string | null
  decision: ExperimentDecision | null
  decisionReason: string | null
  decisionReportRevision: number | null
  decidedBy: EntityId | null
  decidedAt: string | null
  finishedAt: string | null
  variants: ExperimentVariant[]
}

export interface TestCaseEvaluatorBinding {
  id: EntityId
  evaluatorVersionId: EntityId | null
  evaluatorId: EntityId | null
  evaluatorName: string | null
  versionNo: number
  status: EvaluationVersionStatus
  evaluatorKey: string | null
  sortOrder: number
  expectedJson: string | null
}

export interface TestCaseVersion {
  id: EntityId
  testCaseId: EntityId | null
  spaceId: EntityId
  versionNo: number
  status: EvaluationVersionStatus
  sourceTaskId: EntityId | null
  sourceExecutionId: EntityId | null
  sourceInputSchemaVersion: number | null
  sourceInputHash: string | null
  sourceExecutionSchemaVersion: number | null
  sourceExecutionHash: string | null
  documentVersionSnapshot: number | null
  documentContentSha256: string | null
  expectedSchemaVersion: number | null
  expectedJson: string | null
  sourceType: string | null
  sanitizationNote: string | null
  contentHash: string | null
  publishedAt: string | null
  createdBy: EntityId | null
}
