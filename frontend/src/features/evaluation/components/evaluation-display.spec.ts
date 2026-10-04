import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import EvaluationMetricValue from './EvaluationMetricValue.vue'
import EvaluationStatusTag from './EvaluationStatusTag.vue'

type MetricProps = InstanceType<typeof EvaluationMetricValue>['$props']

describe('evaluation display', () => {
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
    [{ missingReason: 'NO_EFFECTIVE_METRIC' }, '缺失 · NO_EFFECTIVE_METRIC'],
    [{ numericValue: null, booleanValue: null, stringValue: null }, '不可用'],
  ])('preserves typed values and explicit absence: %j', (props, expected) => {
    expect(mount(EvaluationMetricValue, { props }).text()).toBe(expected)
  })
})
