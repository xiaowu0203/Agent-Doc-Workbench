import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import EvaluationMetricValue from './EvaluationMetricValue.vue'
import EvaluationStatusTag from './EvaluationStatusTag.vue'
import { formatMetricNumber } from '../display'

type MetricProps = InstanceType<typeof EvaluationMetricValue>['$props']

describe('evaluation display', () => {
  it.each([
    [null, 'ratio', '未知'],
    ['0.000000000000', 'ratio', '0%'],
    ['0.666666666667', 'ratio', '66.67%'],
    ['12500.000000000000', 'millisecond', '12.5 秒'],
    ['0.00000000001', 'CNY', '1.00e-11 CNY'],
    ['9007199254740993', 'token', '9,007,199,254,740,993'],
    ['-0.1', 'percentage-point', '-10 个百分点'],
  ])('格式化 %s，保留未知、零值、小额和安全整数边界', (value, unit, expected) => {
    expect(formatMetricNumber(value, unit)).toBe(expected)
  })
  it.each([
    ['run', 'COMPLETED_WITH_ERRORS', '完成但有错误', 'warning'],
    ['experiment', 'CANCEL_PENDING', '取消中', 'warning'],
    ['attempt', 'REPLAY_FAILED', '回放失败', 'danger'],
    ['result', 'FAILED', '未通过', 'danger'],
    ['result', 'ERROR', '评估异常', 'danger'],
    ['version', 'ARCHIVED', '已归档', 'info'],
    ['decision', 'INSUFFICIENT_EVIDENCE', '证据不足', 'warning'],
  ] as const)('distinguishes %s %s', (domain, status, label, type) => {
    const wrapper = mount(EvaluationStatusTag, { props: { domain, status } })
    expect(wrapper.text()).toBe(label)
    expect(wrapper.find('.el-tag').classes()).toContain(`el-tag--${type}`)
    expect(wrapper.attributes('aria-label')).toContain(status)
  })

  it('keeps unknown or mismatched statuses neutral without claiming success', async () => {
    const wrapper = mount(EvaluationStatusTag, { props: { domain: 'run', status: 'FUTURE_STATE' } })
    expect(wrapper.text()).toBe('FUTURE_STATE')
    expect(wrapper.find('.el-tag').classes()).toContain('el-tag--info')
    await wrapper.setProps({ domain: 'version', status: 'COMPLETED' })
    expect(wrapper.text()).toBe('COMPLETED')
    expect(wrapper.find('.el-tag').classes()).toContain('el-tag--info')
    await wrapper.setProps({ status: null })
    expect(wrapper.text()).toBe('未提供状态')
  })

  it.each<[MetricProps, string]>([
    [{ numericValue: 0, unit: 'USD' }, '0 USD'],
    [{ booleanValue: false }, 'false'],
    [{ stringValue: '' }, '空字符串'],
    [{ missingReason: 'NO_EFFECTIVE_METRIC' }, '无有效结果'],
    [{ numericValue: null, booleanValue: null, stringValue: null }, '不可用'],
  ])('preserves typed values and explicit absence: %j', (props, expected) => {
    expect(mount(EvaluationMetricValue, { props }).text()).toContain(expected)
  })
})
