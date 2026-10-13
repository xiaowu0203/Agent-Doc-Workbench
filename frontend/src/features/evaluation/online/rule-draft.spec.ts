import { describe, expect, it } from 'vitest'
import { newOnlineRule, onlineDecimal, onlineExpected, onlineRuleFromDraft } from './rule-draft'
import type { EvaluatorVersion } from '../types'

describe('冻结规则载荷', () => {
  it('规范化精确十进制，不经浮点转换', () => {
    expect(onlineDecimal('0.00000000000000000000000000100')).toBe('0.000000000000000000000000001')
    expect(onlineDecimal('-0.00')).toBe('0')
    for (const value of ['1e-3', '01', '+1', 'NaN', ' 1', '.1'])
      expect(() => onlineDecimal(value)).toThrow()
  })
  it('每个文档都有期望，覆盖只影响指定文档', () => {
    const draft = newOnlineRule(1)
    draft.version = {
      id: '9007199254740993',
      evaluatorKey: 'online-original-text-assertion',
    } as EvaluatorVersion
    draft.expected = JSON.stringify({
      assertions: [{ field: 'originalText', operator: 'CONTAINS', value: '共同' }],
    })
    draft.overrides['42'] = JSON.stringify({
      assertions: [{ field: 'originalText', operator: 'EXACT', value: '覆盖' }],
    })
    draft.threshold = '0.7500'
    draft.role = 'PRIMARY'
    const result = onlineRuleFromDraft(draft, ['41', '42'])
    expect(result).toMatchObject({
      role: 'PRIMARY',
      threshold: '0.75',
      valueType: 'NUMBER',
      evidenceTarget: 'ORIGINAL_TEXT',
    })
    expect(result.expectedBindings.map((item) => item.expectedJson)).toEqual([
      JSON.parse(draft.expected),
      JSON.parse(draft.overrides['42']!),
    ])
  })
  it.each([
    ['task-terminal-status', 'TASK_FACTS'],
    ['audit-ledger-integrity', 'AUDIT_FACTS'],
    ['online-document-change-validator', 'ORIGINAL_CHANGE'],
  ])('布尔规则 %s 使用对应证据契约', (key, evidenceTarget) => {
    const draft = newOnlineRule(2)
    draft.version = { id: '81', evaluatorKey: key } as EvaluatorVersion
    if (key === 'online-document-change-validator')
      draft.expected = '{"requiredOutputType":"CHANGE_REQUEST"}'
    expect(onlineRuleFromDraft(draft, ['41'])).toMatchObject({
      threshold: true,
      valueType: 'BOOLEAN',
      evidenceTarget,
    })
    draft.threshold = '1'
    expect(() => onlineRuleFromDraft(draft, ['41'])).toThrow()
  })
  it('拒绝额外期望字段、错目标、离线规则和错误正文摘要', () => {
    expect(() => onlineExpected('task-terminal-status', { success: true })).toThrow()
    expect(() =>
      onlineExpected('online-original-text-assertion', {
        assertions: [{ field: 'input', operator: 'EXACT', value: 'a' }],
      }),
    ).toThrow()
    expect(() =>
      onlineExpected('online-document-change-validator', {
        requiredOutputType: 'DRAFT_DOCUMENT',
        targetContentSha256: 'bad',
      }),
    ).toThrow()
    const draft = newOnlineRule(1)
    draft.version = { id: '1', evaluatorKey: 'offline-rule' } as EvaluatorVersion
    expect(() => onlineRuleFromDraft(draft, ['41'])).toThrow()
  })
})
