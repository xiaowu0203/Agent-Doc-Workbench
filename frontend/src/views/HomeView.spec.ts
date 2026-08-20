import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'

import HomeView from './HomeView.vue'

describe('HomeView', () => {
  it('renders the workbench title', () => {
    const wrapper = mount(HomeView, {
      global: {
        plugins: [createPinia()],
      },
    })

    expect(wrapper.text()).toContain('Agent-Doc-Workbench')
  })
})
