import type { EvaluationAttemptStatus, EvaluationRunStatus } from './types'

export const RUN_STATUSES: EvaluationRunStatus[] = [
  'CREATED',
  'DISPATCHING',
  'RUNNING',
  'PAUSED',
  'CANCEL_PENDING',
  'COMPLETED',
  'COMPLETED_WITH_ERRORS',
  'CANCELED',
  'FAILED',
]
export const RUN_POLL_INTERVAL = 5000
export const RUN_POLL_MAX_DELAY = 30000
export const WORKER_TTL_DEFAULT = 3600
export const WORKER_TTL_MIN = 300
export const WORKER_TTL_MAX = 86400

export function activeRun(status: EvaluationRunStatus) {
  return ['CREATED', 'DISPATCHING', 'RUNNING', 'CANCEL_PENDING'].includes(status)
}
export function activeAttempt(status: EvaluationAttemptStatus) {
  return ['CREATED', 'REPLAY_CREATED', 'REPLAY_RUNNING', 'EVALUATING', 'CANCEL_PENDING'].includes(
    status,
  )
}
export function validWorkerTtl(value: number) {
  return Number.isInteger(value) && value >= WORKER_TTL_MIN && value <= WORKER_TTL_MAX
}
export function pauseExplanation(reason: string | null) {
  const explanations: Record<string, string> = {
    DISPATCH_UNAVAILABLE: '任务下发不可用。确认任务服务健康后恢复运行。',
    AUTHORIZATION_EXPIRED: '执行授权已过期或不可用。恢复时请重新确认授权有效期。',
    RECONCILIATION_BLOCKED: '对账受阻。请检查依赖服务与执行证据后恢复。',
  }
  return reason
    ? `${reason}：${explanations[reason] || '请检查运行与任务状态后恢复。'}`
    : '未提供暂停原因'
}
