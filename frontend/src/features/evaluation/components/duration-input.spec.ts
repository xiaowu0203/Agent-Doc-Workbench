import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DurationInput from './DurationInput.vue'
describe('时间窗口精确换算', () => {
  it('以可读天数显示，切换单位不改变冻结秒数，零窗口仍有效', async () => {
    const wrapper = mount(DurationInput, {
      props: {
        label: '窗口',
        modelValue: 604800,
        'onUpdate:modelValue': (seconds: number) => wrapper.setProps({ modelValue: seconds }),
      },
    })
    expect(wrapper.get('input').element).toHaveProperty('value', '7')
    await wrapper.get('select').setValue('3600')
    expect(wrapper.get('input').element).toHaveProperty('value', '168')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    await wrapper.get('input').setValue('2')
    expect(wrapper.props('modelValue')).toBe(7200)
    await wrapper.get('input').setValue('0')
    expect(wrapper.props('modelValue')).toBe(0)
    wrapper.unmount()
  })
  it('非整除单位不可选，非法小数不取整为合法协议值', async () => {
    const wrapper = mount(DurationInput, { props: { label: '窗口', modelValue: 1 } })
    expect(wrapper.get('option[value="86400"]').attributes('disabled')).toBeDefined()
    await wrapper.get('input').setValue('0.5')
    expect(wrapper.emitted('update:modelValue')?.[0]?.[0]).toBeNaN()
    expect(wrapper.props('modelValue')).toBe(1)
    wrapper.unmount()
  })
})
