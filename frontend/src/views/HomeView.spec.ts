import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'

import HomeView from './HomeView.vue'

import { useWorkspaceStore } from '@/stores/workspace'

describe('HomeView', () => {
  it('opens the first available space', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const workspaceStore = useWorkspaceStore()
    vi.spyOn(workspaceStore, 'loadSpaces').mockResolvedValue([
      {
        id: 7,
        name: '产品研发空间',
        description: null,
        ownerId: 1,
        tokenBudget: null,
        status: 'ACTIVE',
        role: null,
        platformSuperAdmin: false,
        createdAt: '2026-08-31T00:00:00Z',
      },
    ])
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: HomeView },
        { path: '/spaces/:spaceId/overview', component: { template: '<div />' } },
      ],
    })
    await router.push('/')
    await router.isReady()

    mount(HomeView, { global: { plugins: [pinia, router] } })
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/spaces/7/overview')
  })
})
