import { request } from '@/api/client'
import type {
  OnlineAction,
  OnlineActionResult,
  OnlineAssignment,
  OnlineCreateRequest,
  OnlineExperiment,
  OnlinePage,
  OnlinePreflight,
  OnlineStartRequest,
  OnlineStateRequest,
  OnlineSummary,
  OnlineReviewAssignment,
  OnlineReviewContext,
  OnlineReviewTarget,
  OnlineQualityRequest,
  OnlineQualityResult,
  OnlineReport,
  OnlineReportRevision,
  OnlineReportRangeSuggestion,
  OnlineRecalculateRequest,
  OnlineDecisionRequest,
  OnlinePromptComparison,
  OnlineQualityHistory,
} from './types'
import type { EvaluatorVersion } from '../types'

const base = '/evaluation/online-experiments'
export function getOnlinePromptComparison(id: string, signal?: AbortSignal) {
  return request<OnlinePromptComparison>({
    method: 'GET',
    url: `${base}/${id}/prompt-comparison`,
    signal,
  })
}
export function queryPublishedOnlineVersions(
  spaceId: string,
  versionIds: string[],
  signal?: AbortSignal,
) {
  return request<EvaluatorVersion[]>({
    method: 'POST',
    url: '/evaluation/evaluator-versions/published-query',
    data: { spaceId, versionIds },
    signal,
  })
}
export function searchOnlineQualityHistory(
  id: string,
  assignmentId: string,
  target: OnlineReviewTarget,
  pageNum = 1,
  signal?: AbortSignal,
) {
  return request<OnlineQualityHistory>({
    method: 'POST',
    url: `${base}/${id}/assignments/${assignmentId}/quality-feedback-history/search`,
    data: { target, pageNum, pageSize: 10 },
    signal,
  })
}
export function searchOnlineExperiments(
  payload: {
    spaceId: string
    status?: string
    agentId?: string
    pageNum?: number
    pageSize?: number
  },
  signal?: AbortSignal,
) {
  return request<OnlinePage<OnlineSummary>>({
    method: 'POST',
    url: `${base}/search`,
    data: { ...payload, pageNum: payload.pageNum ?? 1, pageSize: payload.pageSize ?? 10 },
    signal,
  })
}
export function getOnlineExperiment(id: string, signal?: AbortSignal) {
  return request<OnlineExperiment>({ method: 'GET', url: `${base}/${id}`, signal })
}
export function createOnlineExperiment(data: OnlineCreateRequest, signal?: AbortSignal) {
  return request<OnlineExperiment>({ method: 'POST', url: base, data, signal })
}
export function getOnlinePreflight(id: string, signal?: AbortSignal) {
  return request<OnlinePreflight>({ method: 'GET', url: `${base}/${id}/preflight`, signal })
}
export function searchOnlineAssignments(
  id: string,
  payload: { pageNum?: number; pageSize?: number; documentId?: string; taskId?: string },
  signal?: AbortSignal,
) {
  return request<OnlinePage<OnlineAssignment>>({
    method: 'POST',
    url: `${base}/${id}/assignments/search`,
    data: { ...payload, pageNum: payload.pageNum ?? 1, pageSize: payload.pageSize ?? 10 },
    signal,
  })
}
export function changeOnlineState(
  id: string,
  action: OnlineAction,
  data: OnlineStateRequest | OnlineStartRequest,
  signal?: AbortSignal,
) {
  return request<OnlineActionResult>({
    method: 'POST',
    url: `${base}/${id}/${action}`,
    data,
    signal,
  })
}
export function searchOnlineReviewAssignments(id: string, pageNum = 1, signal?: AbortSignal) {
  return request<OnlinePage<OnlineReviewAssignment>>({
    method: 'POST',
    url: `${base}/${id}/review-assignments/search`,
    data: { pageNum, pageSize: 10 },
    signal,
  })
}
export function getOnlineReviewContext(
  id: string,
  assignmentId: string,
  target: OnlineReviewTarget,
  signal?: AbortSignal,
) {
  return request<OnlineReviewContext>({
    method: 'GET',
    url: `${base}/${id}/assignments/${assignmentId}/blind-review-context`,
    params: { target },
    signal,
  })
}
export function createOnlineQuality(data: OnlineQualityRequest, signal?: AbortSignal) {
  return request<OnlineQualityResult>({ method: 'POST', url: '/evaluation/feedback', data, signal })
}
export function getOnlineReportRevisions(id: string, pageNum = 1, signal?: AbortSignal) {
  return request<OnlinePage<OnlineReportRevision>>({
    method: 'GET',
    url: `${base}/${id}/reports`,
    params: { pageNum, pageSize: 10 },
    signal,
  })
}
export function getOnlineReportRangeSuggestion(id: string, signal?: AbortSignal) {
  return request<OnlineReportRangeSuggestion>({ method: 'GET', url: `${base}/${id}/reports/range-suggestion`, signal })
}
export function getOnlineReport(id: string, revision: number, full = false, signal?: AbortSignal) {
  return request<OnlineReport>({
    method: 'GET',
    url: `${base}/${id}/reports/${revision}${full ? '' : '/summary'}`,
    signal,
  })
}
export function recalculateOnlineReport(
  id: string,
  data: OnlineRecalculateRequest,
  signal?: AbortSignal,
) {
  return request<OnlineReport>({
    method: 'POST',
    url: `${base}/${id}/reports/recalculate`,
    data,
    signal,
  })
}
export function decideOnlineExperiment(
  id: string,
  data: OnlineDecisionRequest,
  signal?: AbortSignal,
) {
  return request<void>({ method: 'POST', url: `${base}/${id}/decision`, data, signal })
}
