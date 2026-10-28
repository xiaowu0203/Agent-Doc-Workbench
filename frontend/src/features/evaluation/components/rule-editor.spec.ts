import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import EvaluatorConfigEditor from './EvaluatorConfigEditor.vue'

function editor(key: string, json = '{}', readonly = false) {
  const wrapper = mount(EvaluatorConfigEditor, {
    props: {
      evaluatorKey: key,
      modelValue: json,
      label: '规则配置',
      readonly,
      'onUpdate:modelValue': (value: string) => wrapper.setProps({ modelValue: value }),
    },
  })
  return wrapper
}
describe('固定评估器字段表单', () => {
  it('保留省略字段与零值，切换 JSON 不改写配置', async () => {
    const wrapper = editor('artifact-contract')
    await wrapper.get('[aria-label="最少产物数"]').setValue('0')
    expect(JSON.parse(wrapper.props('modelValue'))).toEqual({ minCount: 0 })
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '编辑 JSON')!
      .trigger('click')
    expect(wrapper.get('textarea').element).toHaveProperty('value', '{\n  "minCount": 0\n}')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '使用字段表单')!
      .trigger('click')
    expect(JSON.parse(wrapper.props('modelValue'))).toEqual({ minCount: 0 })
    wrapper.unmount()
  })
  it('按契约增加文本断言，删除最后一项恢复省略，不写入空数组', async () => {
    const wrapper = editor('text-assertion')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '添加文本断言')!
      .trigger('click')
    await wrapper.get('[aria-label="文本断言1内容"]').setValue('结果')
    expect(JSON.parse(wrapper.props('modelValue'))).toEqual({
      assertions: [{ field: 'resultSummary', operator: 'CONTAINS', value: '结果' }],
    })
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '移除断言 1')!
      .trigger('click')
    expect(wrapper.props('modelValue')).toBe('{}')
    wrapper.unmount()
  })
  it.each([
    '{"future":{"counter":9007199254740993},"minCount":1}',
    '{"assertions":[{"field":"other","operator":"EXACT","value":"x"}]}',
    '{broken',
  ])('不能无损转换时保留原始 JSON %s', (json) => {
    const wrapper = editor(
      json.includes('assertions') ? 'text-assertion' : 'artifact-contract',
      json,
    )
    expect(wrapper.get('textarea').element).toHaveProperty('value', json)
    expect(wrapper.text()).toContain('无法无损编辑')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    wrapper.unmount()
  })
  it('历史只读配置不允许通过字段事件改写', async () => {
    const wrapper = editor(
      'document-change-validator',
      '{"requiredArtifactType":"DRAFT_CHANGES"}',
      true,
    )
    await wrapper.get('input').setValue('CHANGE_PROPOSAL')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    wrapper.unmount()
  })
})
