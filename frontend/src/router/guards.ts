import type { Router } from 'vue-router'

import type { SpacePermissionCode } from '@/shared/constants/permissions'
import pinia from '@/stores'
import { useAuthStore } from '@/stores/auth'
import { useWorkspaceStore } from '@/stores/workspace'

function parseSpaceId(value: unknown): number | null {
  const rawValue = Array.isArray(value) ? value[0] : value
  const spaceId = Number(rawValue)
  return Number.isSafeInteger(spaceId) && spaceId > 0 ? spaceId : null
}

export function installRouterGuards(router: Router): void {
  router.beforeEach(async (to) => {
    const authStore = useAuthStore(pinia)
    const workspaceStore = useWorkspaceStore(pinia)

    if (to.meta.requiresAuth && !authStore.isAuthenticated) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }

    if (!to.meta.requiresSpace) {
      return true
    }

    const spaceId = parseSpaceId(to.params.spaceId)
    if (spaceId === null) {
      return { path: '/' }
    }

    workspaceStore.setCurrentSpace(spaceId)
    await workspaceStore.ensurePermissions(spaceId)

    const permission = to.meta.permission as SpacePermissionCode | undefined
    if (permission && !workspaceStore.hasPermission(permission)) {
      return { path: '/forbidden' }
    }

    return true
  })
}
