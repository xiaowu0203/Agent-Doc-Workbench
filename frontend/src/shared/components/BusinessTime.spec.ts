import { mount } from '@vue/test-utils'
import { expect, it } from 'vitest'
import BusinessTime from './BusinessTime.vue'

it('时间精度可展开查看原始值，缺少小数时不显示展开入口', async () => {
  const wrapper = mount(BusinessTime, { props: { value: '2026-10-23T08:13:12.407Z' } })
  expect(wrapper.get('time').text()).toBe('2026-10-23 16:13:12 +08:00')
  expect(wrapper.get('summary').text()).toBe('原始精度')
  expect(wrapper.get('details').text()).toContain('2026-10-23T08:13:12.407Z')
  await wrapper.setProps({ value: '2026-10-23T08:13:12Z' })
  expect(wrapper.find('details').exists()).toBe(false)
})
