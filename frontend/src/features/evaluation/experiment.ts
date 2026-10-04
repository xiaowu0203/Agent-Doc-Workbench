import type { ExperimentStatus, ExperimentReportMetricValue } from './types'

export const EXPERIMENT_STATUSES: ExperimentStatus[] = [
  'CREATED',
  'STARTING',
  'RUNNING',
  'PAUSED',
  'CANCEL_PENDING',
  'COMPLETED',
  'COMPLETED_WITH_ERRORS',
  'CANCELED',
  'FAILED',
]
export const MAX_PROMPT_CANDIDATES = 19
export function activeExperiment(status: ExperimentStatus) {
  return ['STARTING', 'RUNNING', 'CANCEL_PENDING'].includes(status)
}
export function completedExperiment(status: ExperimentStatus) {
  return status === 'COMPLETED' || status === 'COMPLETED_WITH_ERRORS'
}
export function metricText(value: ExperimentReportMetricValue) {
  if (value.missingReason !== null) return `缺失：${value.missingReason}`
  if (value.numericValue !== null) return String(value.numericValue)
  if (value.booleanValue !== null) return String(value.booleanValue)
  if (value.stringValue !== null) return value.stringValue === '' ? '空字符串' : value.stringValue
  return '未提供值'
}
export function validBudget(value: number | undefined, minimum: number) {
  return value !== undefined && Number.isSafeInteger(value) && value >= Math.max(1, minimum)
}
