import type { OnlineExperiment, OnlineReport, OnlineReportPayload } from './types'
import { validOnlineId } from './presentation'

/** 只消费服务器校验过且属于当前清单的线上报告；未知协议不能降级为可信正文。 */
export function onlineReportPayload(
  value: OnlineReport | null,
  experiment: OnlineExperiment,
): OnlineReportPayload | null {
  const envelope = value?.report,
    payload = envelope?.payload
  if (
    !value ||
    value.experimentId !== experiment.id ||
    !validOnlineId(value.id) ||
    !Number.isInteger(value.revision) ||
    value.revision < 1 ||
    value.reportType !== 'ONLINE_V1' ||
    value.schemaVersion !== 2 ||
    !['SUPPORTED', 'SUMMARY_VERIFIED'].includes(value.state) ||
    !/^[0-9a-f]{64}$/.test(value.reportHash) ||
    !/^[0-9a-f]{64}$/.test(value.inputHash) ||
    envelope?.domain !== 'online.report' ||
    envelope.schemaVersion !== 2 ||
    payload?.reportType !== 'ONLINE_V1' ||
    payload.schemaVersion !== 2 ||
    payload.manifestHash !== experiment.manifestHash ||
    !payload.groups?.BASELINE ||
    !payload.groups.CANDIDATE ||
    typeof payload.decisionEligibility?.comparisonAllowed !== 'boolean' ||
    !Array.isArray(payload.decisionEligibility.reasonCodes) ||
    !Array.isArray(payload.decisionEligibility.confirmedSafetyFeedbackIds)
  )
    return null
  return payload
}
export function onlineReportFact(value: unknown): string {
  return (typeof value === 'string' && value !== '') ||
    (typeof value === 'number' && Number.isFinite(value)) ||
    typeof value === 'boolean'
    ? String(value)
    : '未知'
}
export function reportObject(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {}
}
