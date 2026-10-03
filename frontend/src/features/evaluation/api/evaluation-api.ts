import { request } from '@/api/client'
import type { EntityId } from '@/features/workspace/types'
import type { EvaluationTaskLink } from '@/features/task/engineering-types'
import type {
  DatasetCaseBinding,
  DatasetCaseBindingsRequest,
  DatasetVersion,
  DatasetVersionCreateRequest,
  EvaluationCaseAttemptHistory,
  EvaluationDataset,
  EvaluationDatasetCreateRequest,
  EvaluationFeedback,
  EvaluationFeedbackCreateRequest,
  EvaluationPage,
  EvaluationPagination,
  EvaluationResourceSearch,
  EvaluationResultDetail,
  EvaluationRetryRequest,
  EvaluationRun,
  EvaluationRunCreateRequest,
  EvaluationRunResumeRequest,
  EvaluationRunSearch,
  EvaluationRunSummary,
  EvaluationTestCase,
  EvaluationTestCaseCreateRequest,
  EvaluationVersionSearch,
  Evaluator,
  EvaluatorCreateRequest,
  EvaluatorVersion,
  EvaluatorVersionCreateRequest,
  Experiment,
  ExperimentCreateRequest,
  ExperimentDecisionRequest,
  ExperimentPreflight,
  ExperimentReport,
  ExperimentReportRecalculateRequest,
  ExperimentReportRevision,
  ExperimentSearch,
  ExperimentStartRequest,
  ExperimentSummary,
  ExperimentVariant,
  MetricComparisonInput,
  MetricComparisonQueryRequest,
  MetricSearch,
  StandardMetric,
  TestCaseEvaluatorBinding,
  TestCaseEvaluatorBindingsRequest,
  TestCaseVersion,
  TestCaseVersionCreateRequest,
} from '@/features/evaluation/types'

export function getEvaluationTaskLink(
  spaceId: EntityId,
  taskId: EntityId,
  signal?: AbortSignal,
): Promise<EvaluationTaskLink | null> {
  return request<EvaluationTaskLink | null>({
    method: 'GET',
    url: `/evaluation/task-links/${taskId}`,
    params: { spaceId },
    signal,
  })
}

export function searchDatasets(
  payload: EvaluationResourceSearch,
  signal?: AbortSignal,
): Promise<EvaluationPage<EvaluationDataset>> {
  return request<EvaluationPage<EvaluationDataset>>({
    method: 'POST',
    url: '/evaluation/datasets/search',
    data: { ...payload, pageNum: payload.pageNum ?? 1, pageSize: payload.pageSize ?? 10 },
    signal,
  })
}

export function getDataset(id: EntityId, signal?: AbortSignal): Promise<EvaluationDataset> {
  return request<EvaluationDataset>({
    method: 'GET',
    url: `/evaluation/datasets/${id}`,
    signal,
  })
}

export function createDataset(
  payload: EvaluationDatasetCreateRequest,
  signal?: AbortSignal,
): Promise<EvaluationDataset> {
  return request<EvaluationDataset>({
    method: 'POST',
    url: '/evaluation/datasets',
    data: payload,
    signal,
  })
}

export function archiveDataset(id: EntityId, signal?: AbortSignal): Promise<EvaluationDataset> {
  return request<EvaluationDataset>({
    method: 'PUT',
    url: `/evaluation/datasets/${id}/archive`,
    signal,
  })
}

export function searchDatasetVersions(
  payload: EvaluationVersionSearch,
  signal?: AbortSignal,
): Promise<EvaluationPage<DatasetVersion>> {
  return request<EvaluationPage<DatasetVersion>>({
    method: 'POST',
    url: '/evaluation/dataset-versions/search',
    data: { ...payload, pageNum: payload.pageNum ?? 1, pageSize: payload.pageSize ?? 10 },
    signal,
  })
}

export function getDatasetVersion(id: EntityId, signal?: AbortSignal): Promise<DatasetVersion> {
  return request<DatasetVersion>({
    method: 'GET',
    url: `/evaluation/dataset-versions/${id}`,
    signal,
  })
}

export function createDatasetVersion(
  payload: DatasetVersionCreateRequest,
  signal?: AbortSignal,
): Promise<DatasetVersion> {
  return request<DatasetVersion>({
    method: 'POST',
    url: '/evaluation/dataset-versions',
    data: payload,
    signal,
  })
}

export function publishDatasetVersion(id: EntityId, signal?: AbortSignal): Promise<DatasetVersion> {
  return request<DatasetVersion>({
    method: 'PUT',
    url: `/evaluation/dataset-versions/${id}/publish`,
    signal,
  })
}

export function archiveDatasetVersion(id: EntityId, signal?: AbortSignal): Promise<DatasetVersion> {
  return request<DatasetVersion>({
    method: 'PUT',
    url: `/evaluation/dataset-versions/${id}/archive`,
    signal,
  })
}

export function searchTestCases(
  payload: EvaluationResourceSearch,
  signal?: AbortSignal,
): Promise<EvaluationPage<EvaluationTestCase>> {
  return request<EvaluationPage<EvaluationTestCase>>({
    method: 'POST',
    url: '/evaluation/test-cases/search',
    data: { ...payload, pageNum: payload.pageNum ?? 1, pageSize: payload.pageSize ?? 10 },
    signal,
  })
}

export function getTestCase(id: EntityId, signal?: AbortSignal): Promise<EvaluationTestCase> {
  return request<EvaluationTestCase>({
    method: 'GET',
    url: `/evaluation/test-cases/${id}`,
    signal,
  })
}

export function createTestCase(
  payload: EvaluationTestCaseCreateRequest,
  signal?: AbortSignal,
): Promise<EvaluationTestCase> {
  return request<EvaluationTestCase>({
    method: 'POST',
    url: '/evaluation/test-cases',
    data: payload,
    signal,
  })
}

export function archiveTestCase(id: EntityId, signal?: AbortSignal): Promise<EvaluationTestCase> {
  return request<EvaluationTestCase>({
    method: 'PUT',
    url: `/evaluation/test-cases/${id}/archive`,
    signal,
  })
}

export function searchTestCaseVersions(
  payload: EvaluationVersionSearch,
  signal?: AbortSignal,
): Promise<EvaluationPage<TestCaseVersion>> {
  return request<EvaluationPage<TestCaseVersion>>({
    method: 'POST',
    url: '/evaluation/test-case-versions/search',
    data: { ...payload, pageNum: payload.pageNum ?? 1, pageSize: payload.pageSize ?? 10 },
    signal,
  })
}

export function getTestCaseVersion(id: EntityId, signal?: AbortSignal): Promise<TestCaseVersion> {
  return request<TestCaseVersion>({
    method: 'GET',
    url: `/evaluation/test-case-versions/${id}`,
    signal,
  })
}

export function createTestCaseVersion(
  payload: TestCaseVersionCreateRequest,
  signal?: AbortSignal,
): Promise<TestCaseVersion> {
  return request<TestCaseVersion>({
    method: 'POST',
    url: '/evaluation/test-case-versions',
    data: payload,
    signal,
  })
}

export function publishTestCaseVersion(
  id: EntityId,
  signal?: AbortSignal,
): Promise<TestCaseVersion> {
  return request<TestCaseVersion>({
    method: 'PUT',
    url: `/evaluation/test-case-versions/${id}/publish`,
    signal,
  })
}

export function archiveTestCaseVersion(
  id: EntityId,
  signal?: AbortSignal,
): Promise<TestCaseVersion> {
  return request<TestCaseVersion>({
    method: 'PUT',
    url: `/evaluation/test-case-versions/${id}/archive`,
    signal,
  })
}

export function searchEvaluators(
  payload: EvaluationResourceSearch,
  signal?: AbortSignal,
): Promise<EvaluationPage<Evaluator>> {
  return request<EvaluationPage<Evaluator>>({
    method: 'POST',
    url: '/evaluation/evaluators/search',
    data: { ...payload, pageNum: payload.pageNum ?? 1, pageSize: payload.pageSize ?? 10 },
    signal,
  })
}

export function getEvaluator(id: EntityId, signal?: AbortSignal): Promise<Evaluator> {
  return request<Evaluator>({
    method: 'GET',
    url: `/evaluation/evaluators/${id}`,
    signal,
  })
}

export function createEvaluator(
  payload: EvaluatorCreateRequest,
  signal?: AbortSignal,
): Promise<Evaluator> {
  return request<Evaluator>({
    method: 'POST',
    url: '/evaluation/evaluators',
    data: payload,
    signal,
  })
}

export function archiveEvaluator(id: EntityId, signal?: AbortSignal): Promise<Evaluator> {
  return request<Evaluator>({
    method: 'PUT',
    url: `/evaluation/evaluators/${id}/archive`,
    signal,
  })
}

export function searchEvaluatorVersions(
  payload: EvaluationVersionSearch,
  signal?: AbortSignal,
): Promise<EvaluationPage<EvaluatorVersion>> {
  return request<EvaluationPage<EvaluatorVersion>>({
    method: 'POST',
    url: '/evaluation/evaluator-versions/search',
    data: { ...payload, pageNum: payload.pageNum ?? 1, pageSize: payload.pageSize ?? 10 },
    signal,
  })
}

export function getEvaluatorVersion(id: EntityId, signal?: AbortSignal): Promise<EvaluatorVersion> {
  return request<EvaluatorVersion>({
    method: 'GET',
    url: `/evaluation/evaluator-versions/${id}`,
    signal,
  })
}

export function createEvaluatorVersion(
  payload: EvaluatorVersionCreateRequest,
  signal?: AbortSignal,
): Promise<EvaluatorVersion> {
  return request<EvaluatorVersion>({
    method: 'POST',
    url: '/evaluation/evaluator-versions',
    data: payload,
    signal,
  })
}

export function publishEvaluatorVersion(
  id: EntityId,
  signal?: AbortSignal,
): Promise<EvaluatorVersion> {
  return request<EvaluatorVersion>({
    method: 'PUT',
    url: `/evaluation/evaluator-versions/${id}/publish`,
    signal,
  })
}

export function archiveEvaluatorVersion(
  id: EntityId,
  signal?: AbortSignal,
): Promise<EvaluatorVersion> {
  return request<EvaluatorVersion>({
    method: 'PUT',
    url: `/evaluation/evaluator-versions/${id}/archive`,
    signal,
  })
}

export function getDatasetCases(id: EntityId, signal?: AbortSignal): Promise<DatasetCaseBinding[]> {
  return request<DatasetCaseBinding[]>({
    method: 'GET',
    url: `/evaluation/dataset-versions/${id}/cases`,
    signal,
  })
}

export function replaceDatasetCases(
  id: EntityId,
  payload: DatasetCaseBindingsRequest,
  signal?: AbortSignal,
): Promise<DatasetVersion> {
  return request<DatasetVersion>({
    method: 'PUT',
    url: `/evaluation/dataset-versions/${id}/cases`,
    data: payload,
    signal,
  })
}

export function getTestCaseEvaluators(
  id: EntityId,
  signal?: AbortSignal,
): Promise<TestCaseEvaluatorBinding[]> {
  return request<TestCaseEvaluatorBinding[]>({
    method: 'GET',
    url: `/evaluation/test-case-versions/${id}/evaluators`,
    signal,
  })
}

export function replaceTestCaseEvaluators(
  id: EntityId,
  payload: TestCaseEvaluatorBindingsRequest,
  signal?: AbortSignal,
): Promise<TestCaseVersion> {
  return request<TestCaseVersion>({
    method: 'PUT',
    url: `/evaluation/test-case-versions/${id}/evaluators`,
    data: payload,
    signal,
  })
}

export function searchEvaluationRuns(
  payload: EvaluationRunSearch,
  signal?: AbortSignal,
): Promise<EvaluationPage<EvaluationRunSummary>> {
  return request<EvaluationPage<EvaluationRunSummary>>({
    method: 'POST',
    url: '/evaluation/runs/search',
    data: { ...payload, pageNum: payload.pageNum ?? 1, pageSize: payload.pageSize ?? 10 },
    signal,
  })
}

export function getEvaluationRun(id: EntityId, signal?: AbortSignal): Promise<EvaluationRun> {
  return request<EvaluationRun>({
    method: 'GET',
    url: `/evaluation/runs/${id}`,
    signal,
  })
}

export function createEvaluationRun(
  payload: EvaluationRunCreateRequest,
  signal?: AbortSignal,
): Promise<EvaluationRun> {
  return request<EvaluationRun>({
    method: 'POST',
    url: '/evaluation/runs',
    data: payload,
    signal,
  })
}

export function resumeEvaluationRun(
  id: EntityId,
  payload: EvaluationRunResumeRequest,
  signal?: AbortSignal,
): Promise<EvaluationRun> {
  return request<EvaluationRun>({
    method: 'PUT',
    url: `/evaluation/runs/${id}/resume`,
    data: payload,
    signal,
  })
}

export function cancelEvaluationRun(id: EntityId, signal?: AbortSignal): Promise<EvaluationRun> {
  return request<EvaluationRun>({
    method: 'PUT',
    url: `/evaluation/runs/${id}/cancel`,
    signal,
  })
}

export function searchCaseAttempts(
  id: EntityId,
  payload: EvaluationPagination,
  signal?: AbortSignal,
): Promise<EvaluationPage<EvaluationCaseAttemptHistory>> {
  return request<EvaluationPage<EvaluationCaseAttemptHistory>>({
    method: 'POST',
    url: `/evaluation/case-runs/${id}/attempts/search`,
    data: { ...payload, pageNum: payload.pageNum ?? 1, pageSize: payload.pageSize ?? 10 },
    signal,
  })
}

export function retryCaseReplay(
  id: EntityId,
  payload: EvaluationRunResumeRequest,
  signal?: AbortSignal,
): Promise<EvaluationRun> {
  return request<EvaluationRun>({
    method: 'POST',
    url: `/evaluation/case-runs/${id}/retry-replay`,
    data: payload,
    signal,
  })
}

export function retryCaseEvaluation(
  id: EntityId,
  payload: EvaluationRetryRequest,
  signal?: AbortSignal,
): Promise<EvaluationRun> {
  return request<EvaluationRun>({
    method: 'POST',
    url: `/evaluation/case-attempts/${id}/retry-evaluation`,
    data: payload,
    signal,
  })
}

export function getEvaluationResult(
  id: EntityId,
  signal?: AbortSignal,
): Promise<EvaluationResultDetail> {
  return request<EvaluationResultDetail>({
    method: 'GET',
    url: `/evaluation/results/${id}`,
    signal,
  })
}

export function searchEvaluationMetrics(
  payload: MetricSearch,
  signal?: AbortSignal,
): Promise<EvaluationPage<StandardMetric>> {
  return request<EvaluationPage<StandardMetric>>({
    method: 'POST',
    url: '/evaluation/metrics/search',
    data: { ...payload, pageNum: payload.pageNum ?? 1, pageSize: payload.pageSize ?? 10 },
    signal,
  })
}

export function getMetricComparisonInput(
  payload: MetricComparisonQueryRequest,
  signal?: AbortSignal,
): Promise<MetricComparisonInput> {
  return request<MetricComparisonInput>({
    method: 'POST',
    url: '/evaluation/metrics/compare-input',
    data: payload,
    signal,
  })
}

export function createEvaluationFeedback(
  payload: EvaluationFeedbackCreateRequest,
  signal?: AbortSignal,
): Promise<EvaluationFeedback> {
  return request<EvaluationFeedback>({
    method: 'POST',
    url: '/evaluation/feedback',
    data: payload,
    signal,
  })
}

export function importChangeRequestFeedback(
  id: EntityId,
  signal?: AbortSignal,
): Promise<EvaluationFeedback> {
  return request<EvaluationFeedback>({
    method: 'POST',
    url: `/evaluation/feedback/import-change-request/${id}`,
    signal,
  })
}

export function searchExperiments(
  payload: ExperimentSearch,
  signal?: AbortSignal,
): Promise<EvaluationPage<ExperimentSummary>> {
  return request<EvaluationPage<ExperimentSummary>>({
    method: 'POST',
    url: '/evaluation/experiments/search',
    data: { ...payload, pageNum: payload.pageNum ?? 1, pageSize: payload.pageSize ?? 10 },
    signal,
  })
}

export function getExperiment(id: EntityId, signal?: AbortSignal): Promise<Experiment> {
  return request<Experiment>({
    method: 'GET',
    url: `/evaluation/experiments/${id}`,
    signal,
  })
}

export function createExperiment(
  payload: ExperimentCreateRequest,
  signal?: AbortSignal,
): Promise<Experiment> {
  return request<Experiment>({
    method: 'POST',
    url: '/evaluation/experiments',
    data: payload,
    signal,
  })
}

export function getExperimentVariants(
  id: EntityId,
  signal?: AbortSignal,
): Promise<ExperimentVariant[]> {
  return request<ExperimentVariant[]>({
    method: 'GET',
    url: `/evaluation/experiments/${id}/variants`,
    signal,
  })
}

export function getExperimentPreflight(
  id: EntityId,
  signal?: AbortSignal,
): Promise<ExperimentPreflight> {
  return request<ExperimentPreflight>({
    method: 'GET',
    url: `/evaluation/experiments/${id}/preflight`,
    signal,
  })
}

export function startExperiment(
  id: EntityId,
  payload: ExperimentStartRequest,
  signal?: AbortSignal,
): Promise<Experiment> {
  return request<Experiment>({
    method: 'POST',
    url: `/evaluation/experiments/${id}/start`,
    data: payload,
    signal,
  })
}

export function cancelExperiment(id: EntityId, signal?: AbortSignal): Promise<Experiment> {
  return request<Experiment>({
    method: 'PUT',
    url: `/evaluation/experiments/${id}/cancel`,
    signal,
  })
}

export function getExperimentReportRevisions(
  id: EntityId,
  signal?: AbortSignal,
): Promise<ExperimentReportRevision[]> {
  return request<ExperimentReportRevision[]>({
    method: 'GET',
    url: `/evaluation/experiments/${id}/reports`,
    signal,
  })
}

export function getExperimentReport(
  id: EntityId,
  revision: number,
  signal?: AbortSignal,
): Promise<ExperimentReport> {
  return request<ExperimentReport>({
    method: 'GET',
    url: `/evaluation/experiments/${id}/reports/${revision}`,
    signal,
  })
}

export function recalculateExperimentReport(
  id: EntityId,
  payload: ExperimentReportRecalculateRequest,
  signal?: AbortSignal,
): Promise<ExperimentReport> {
  return request<ExperimentReport>({
    method: 'POST',
    url: `/evaluation/experiments/${id}/reports/recalculate`,
    data: payload,
    signal,
  })
}

export function submitExperimentDecision(
  id: EntityId,
  payload: ExperimentDecisionRequest,
  signal?: AbortSignal,
): Promise<void> {
  return request<void>({
    method: 'POST',
    url: `/evaluation/experiments/${id}/decision`,
    data: payload,
    signal,
  })
}
