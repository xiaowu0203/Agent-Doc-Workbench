import type { EvaluatorVersion } from '../types'
import type { OnlineRule } from './types'

export const ONLINE_MAX_RULES = 50
export const ONLINE_LIVE_RULE_KEYS = [
  'online-original-text-assertion',
  'online-document-change-validator',
  'task-terminal-status',
  'audit-ledger-integrity',
] as const
export interface OnlineRuleDraft {
  ruleKey: string
  role: OnlineRule['role']
  version: EvaluatorVersion | null
  versionName: string
  accuracy: boolean
  comparison: OnlineRule['comparison']
  threshold: string
  expected: string
  overrides: Record<string, string>
  documentId: string
}
export function newOnlineRule(index: number): OnlineRuleDraft {
  return {
    ruleKey: `rule-${index}`,
    role: 'AUXILIARY',
    version: null,
    versionName: '',
    accuracy: false,
    comparison: 'EQ',
    threshold: 'true',
    expected: '{}',
    overrides: {},
    documentId: '',
  }
}
export function onlineDecimal(text: string): string {
  if (text.length > 64 || !/^-?(0|[1-9]\d*)(\.\d+)?$/.test(text))
    throw new Error('阈值须为无指数的规范十进制文本')
  const [integer, fraction] = text.split('.')
  const digits = fraction?.replace(/0+$/, '')
  const value = digits ? `${integer}.${digits}` : integer!
  return value === '-0' ? '0' : value
}
export function onlineExpected(key: string, value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value))
    throw new Error('每个文档的期望须为对象')
  const expected = value as Record<string, unknown>
  const allowed =
    key === ONLINE_LIVE_RULE_KEYS[0]
      ? ['assertions']
      : key === ONLINE_LIVE_RULE_KEYS[1]
        ? ['requiredOutputType', 'targetContentSha256']
        : []
  if (
    !ONLINE_LIVE_RULE_KEYS.some((item) => item === key) ||
    Object.keys(expected).some((name) => !allowed.includes(name))
  )
    throw new Error('期望字段不符合所选评估器契约')
  if (key === ONLINE_LIVE_RULE_KEYS[0]) {
    const assertions = expected.assertions
    if (
      !Array.isArray(assertions) ||
      !assertions.length ||
      assertions.length > 50 ||
      assertions.some(
        (item) =>
          !item ||
          Object.keys(item).some((name) => !['field', 'operator', 'value'].includes(name)) ||
          item.field !== 'originalText' ||
          !['CONTAINS', 'NOT_CONTAINS', 'EXACT', 'SHA256'].includes(item.operator) ||
          typeof item.value !== 'string' ||
          [...item.value].length > 2000 ||
          (item.operator === 'SHA256' && !/^[0-9a-f]{64}$/.test(item.value)),
      )
    )
      throw new Error('原始文本期望须包含1～50条有效断言')
  } else if (
    key === ONLINE_LIVE_RULE_KEYS[1] &&
    (!['CHANGE_REQUEST', 'DRAFT_DOCUMENT'].includes(String(expected.requiredOutputType)) ||
      ('targetContentSha256' in expected &&
        (typeof expected.targetContentSha256 !== 'string' ||
          !/^[0-9a-f]{64}$/.test(expected.targetContentSha256))))
  )
    throw new Error('原始变更期望的产物类型或正文摘要无效')
  return expected
}
export function onlineRuleFromDraft(draft: OnlineRuleDraft, documentIds: string[]): OnlineRule {
  const key = draft.version?.evaluatorKey
  if (!key || !ONLINE_LIVE_RULE_KEYS.some((item) => item === key))
    throw new Error('每条规则均须选择已发布的LIVE评估器')
  if (!/^[A-Za-z0-9._:-]{1,64}$/.test(draft.ruleKey))
    throw new Error('规则标识须为1～64位ASCII字母、数字或 . _ : -')
  const text = key === ONLINE_LIVE_RULE_KEYS[0],
    change = key === ONLINE_LIVE_RULE_KEYS[1]
  const numeric = text || (change && draft.accuracy)
  if (!numeric && !['true', 'false'].includes(draft.threshold))
    throw new Error('布尔规则阈值须为true或false')
  return {
    ruleKey: draft.ruleKey,
    role: draft.role,
    evaluatorVersionId: String(draft.version!.id),
    metricKey: text
      ? 'evaluation.online-original-text.pass-ratio'
      : change
        ? `evaluation.online-document-change.${draft.accuracy ? 'accuracy' : 'valid'}`
        : key === 'task-terminal-status'
          ? 'evaluation.task-terminal.success'
          : 'evaluation.audit-ledger-integrity.success',
    valueType: numeric ? 'NUMBER' : 'BOOLEAN',
    unit: numeric ? 'ratio' : 'boolean',
    comparison: draft.comparison,
    threshold: numeric ? onlineDecimal(draft.threshold) : draft.threshold === 'true',
    evidenceTarget: text
      ? 'ORIGINAL_TEXT'
      : change
        ? 'ORIGINAL_CHANGE'
        : key === 'task-terminal-status'
          ? 'TASK_FACTS'
          : 'AUDIT_FACTS',
    expectedBindings: documentIds.map((documentId) => ({
      documentId,
      expectedJson: onlineExpected(
        key,
        JSON.parse(draft.overrides[documentId]?.trim() || draft.expected),
      ),
    })),
  }
}
