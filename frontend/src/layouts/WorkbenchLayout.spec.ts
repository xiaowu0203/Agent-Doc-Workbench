import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import WorkbenchLayout from './WorkbenchLayout.vue'

function mockViewport(matches: boolean) {
  const addEventListener = vi.fn()
  const removeEventListener = vi.fn()
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: vi.fn(() => ({ matches, addEventListener, removeEventListener })),
  })
  return { addEventListener, removeEventListener }
}

afterEach(() => vi.unstubAllGlobals())

describe('WorkbenchLayout', () => {
  it('collapses the sidebar automatically on narrow screens', async () => {
    const listeners = mockViewport(true)
    const pinia = createPinia()
    setActivePinia(pinia)
    const wrapper = mount(WorkbenchLayout, {
      global: {
        plugins: [pinia],
        stubs: {
          AppSidebar: { props: ['collapsed'], template: '<aside>{{ collapsed }}</aside>' },
          AppTopbar: true,
          RouterView: true,
        },
      },
    })

    await flushPromises()
    expect(wrapper.get('aside').text()).toBe('true')
    expect(listeners.addEventListener).toHaveBeenCalledWith('change', expect.any(Function))
    wrapper.unmount()
    expect(listeners.removeEventListener).toHaveBeenCalledWith('change', expect.any(Function))
  })
})
