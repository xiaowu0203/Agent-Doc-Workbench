import type { OnlineAction, OnlineExperiment, OnlinePreflight } from './types'

export const ONLINE_STATUSES = ['CREATED', 'ACTIVE', 'PAUSED', 'STOPPING', 'STOPPED'] as const
export const ONLINE_POLL_INTERVAL = 5000
export const ONLINE_POLL_MAX_DELAY = 30000
export const ONLINE_DESCRIPTIVE_NOTICE =
  '本实验为描述性观察；比例区间仅显示估计不确定性，不构成 Prompt 优劣的统计证明。两组共享部分运行资源，延迟差不能直接归因为 Prompt。'
export const ONLINE_EMERGENCY_NOTICE =
  '紧急熔断会关闭准入并请求取消，远端调用可能尚未停止，已产生费用和业务变更无法撤销。'
export const ONLINE_FALLBACK_NOTICE =
  '新任务使用当前生产 Agent 配置，与本实验冻结 baseline 可能不同。'
export const ONLINE_EVALUATOR_KEYS = [
  'online-original-text-assertion',
  'online-document-change-validator',
] as const

export function onlineFact(value: unknown): string {
  return value === null || value === undefined || value === '' ? '未知' : String(value)
}
export function onlineStatus(value: string): string {
  return (
    (
      {
        CREATED: '草案',
        ACTIVE: '运行中',
        PAUSED: '已暂停',
        STOPPING: '停止中',
        STOPPED: '已停止',
      } as Record<string, string>
    )[value] ?? `未知状态（${value}）`
  )
}
export function onlineActionLabel(value: OnlineAction): string {
  return {
    start: '启动实验',
    pause: '暂停分配',
    resume: '恢复实验',
    stop: '结束实验',
    'emergency-stop': '紧急熔断',
  }[value]
}
export function validOnlineId(value: string, allowZero = false): boolean {
  return (
    typeof value === 'string' &&
    value.length <= 19 &&
    /^(0|[1-9]\d*)$/.test(value) &&
    (allowZero || value !== '0') &&
    BigInt(value) <= 9223372036854775807n
  )
}
export function onlineStateVersion(value: string | number): string {
  if (typeof value === 'number' && (!Number.isSafeInteger(value) || value < 0))
    throw new Error('状态版本不可安全读取，请刷新')
  const text = String(value)
  if (!validOnlineId(text, true)) throw new Error('状态版本不可安全读取，请刷新')
  return text
}
export function onlineIdentity(value: OnlineExperiment): string {
  return `${value.spaceId}/${value.id}/${value.manifestHash}/${onlineStateVersion(value.stateVersion)}/${value.status}`
}
export function preflightCurrent(
  experiment: OnlineExperiment,
  proof: OnlinePreflight | null,
  now = Date.now(),
): boolean {
  return (
    !!proof &&
    proof.experimentId === experiment.id &&
    proof.manifestHash === experiment.manifestHash &&
    !!proof.proofHash &&
    Number.isFinite(Date.parse(proof.expiresAt)) &&
    Date.parse(proof.expiresAt) > now &&
    proof.startable &&
    proof.draftEligible &&
    !proof.issues.some((item) => item.severity === 'ERROR')
  )
}
export function onlineActionAvailable(
  experiment: OnlineExperiment,
  action: OnlineAction,
  proof: OnlinePreflight | null,
  now = Date.now(),
): boolean {
  if (
    experiment.manifestSchemaVersion !== 2 ||
    !experiment.manifestHash ||
    !ONLINE_STATUSES.includes(experiment.status as (typeof ONLINE_STATUSES)[number])
  )
    return false
  if (action === 'start')
    return experiment.status === 'CREATED' && preflightCurrent(experiment, proof, now)
  if (action === 'resume')
    return experiment.status === 'PAUSED' && preflightCurrent(experiment, proof, now)
  if (action === 'pause') return experiment.status === 'ACTIVE'
  if (action === 'stop') return ['CREATED', 'ACTIVE', 'PAUSED'].includes(experiment.status)
  return ['ACTIVE', 'PAUSED', 'STOPPING'].includes(experiment.status)
}
export function onlineIssue(code: string): string {
  return (
    (
      {
        ONLINE_EXECUTION_NOT_READY: '部署验收尚未完成，启动入口关闭',
        SPACE_SLOT_OCCUPIED: '空间已有运行或停止中的实验，请先处理原实验',
        INSUFFICIENT_EVIDENCE: '样本或证据不足',
        SCOPE_DRIFT: '文档范围或访问权限发生变化',
        DEPENDENCY_DRIFT: '冻结依赖发生变化',
        SRM_DETECTED: '文档分配比例异常',
        CONFIRMED_SAFETY_EVENT: '存在已确认安全事件',
        TASK_CANCEL_UNCONFIRMED: '远端取消尚未确认',
        UNSUPPORTED_ONLINE_SCHEMA: '历史协议仅供查看',
        ENGINEERING_MODE: '当前为工程验证模式，不用于候选质量比较',
        DOCUMENT_SAMPLE_INSUFFICIENT: '每组独立参与文档不足 30 个',
        PRIMARY_COVERAGE_INSUFFICIENT: '每组主规则有效覆盖不足 80%',
        BLINDED_QUALITY_COVERAGE_INSUFFICIENT: '每组有效盲态原始质量覆盖不足 80%',
        RANGE_SRM_NOT_ENOUGH_UNITS: '范围文档太少，分配诊断尚未启用',
        RUNTIME_SRM_NOT_ENOUGH_UNITS: '参与文档太少，分配诊断尚未启用',
        RANGE_SRM_DETECTED: '范围分配比例异常',
        RUNTIME_SRM_DETECTED: '实际参与文档的分配比例异常',
        DESCRIPTIVE_NOT_CAUSAL: '描述性观察不构成因果证明',
        FIRST_ACCEPTED_ASSIGNMENT_PER_DOCUMENT: '每文档首个已接受任务作为主分析单位',
        LATEST_ATTEMPT_PER_FROZEN_RULE: '每条冻结规则采用最新尝试并保留失败与缺失',
        UNKNOWN_IS_NOT_ZERO: '未知和缺失不等于零',
        CURRENT_OBSERVATION_NOT_EVENT_TIME_RECONSTRUCTION:
          '报告记录截止时观察到的事实，不重建完整事件时间线',
        TRACE_NOT_AUTHORITATIVE:
          'Trace 可能未采样或已过期；缺失不表示未调用，执行快照和账本仍为权威',
      } as Record<string, string>
    )[code] ?? code
  )
}
