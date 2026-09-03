import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it } from 'vitest'

import { installRouterGuards } from './guards'

import pinia from '@/stores'
import { useAuthStore } from '@/stores/auth'
import { PLATFORM_ROLES } from '@/shared/constants/platform-roles'

function createTestRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div>home</div>' } },
      { path: '/forbidden', component: { template: '<div>forbidden</div>' } },
      {
        path: '/system/models',
        component: { template: '<div>models</div>' },
        meta: { requiresAuth: true, platformRole: PLATFORM_ROLES.SUPER_ADMIN },
      },
    ],
  })
  installRouterGuards(router)
  return router
}

beforeEach(() => {
  useAuthStore(pinia).$reset()
})

describe('platform route guard', () => {
  it('rejects authenticated users without the required platform role', async () => {
    useAuthStore(pinia).setSession({
      accessToken: 'token',
      user: { id: 1, username: 'member', nickname: null, email: null, avatarUrl: null },
      platformRoles: [],
    })
    const router = createTestRouter()

    await router.push('/system/models')

    expect(router.currentRoute.value.path).toBe('/forbidden')
  })

  it('allows platform super administrators', async () => {
    useAuthStore(pinia).setSession({
      accessToken: 'token',
      user: { id: 1, username: 'admin', nickname: null, email: null, avatarUrl: null },
      platformRoles: [PLATFORM_ROLES.SUPER_ADMIN],
    })
    const router = createTestRouter()

    await router.push('/system/models')

    expect(router.currentRoute.value.path).toBe('/system/models')
  })
})
