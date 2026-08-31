import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'

import HomeView from './HomeView.vue'

describe('HomeView', () => {
  it('renders the Phase 6 foundation summary', () => {
    const wrapper = mount(HomeView, {
      global: {
        plugins: [createPinia()],
      },
    })

    expect(wrapper.text()).toContain('Phase 6 前端基础层')
    expect(wrapper.text()).toContain('请求与会话')
  })
})
