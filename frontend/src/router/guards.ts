import type { Router } from 'vue-router'

import type { SpacePermissionCode } from '@/shared/constants/permissions'
import pinia from '@/stores'
import { useAuthStore } from '@/stores/auth'
import { useWorkspaceStore } from '@/stores/workspace'

function parseSpaceId(value: unknown): string | null {
  const rawValue = Array.isArray(value) ? value[0] : value
  const spaceId = String(rawValue ?? '').trim()
  return /^[1-9]\d*$/.test(spaceId) ? spaceId : null
}

export function installRouterGuards(router: Router): void {
  router.beforeEach(async (to) => {
    const authStore = useAuthStore(pinia)
    const workspaceStore = useWorkspaceStore(pinia)

    if (to.meta.requiresAuth && !authStore.isAuthenticated) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }

    if (to.meta.guestOnly && authStore.isAuthenticated) {
      return { path: '/' }
    }

    if (to.meta.platformRole && !authStore.platformRoles.includes(to.meta.platformRole)) {
      return { path: '/forbidden' }
    }

    if (!to.meta.requiresSpace) {
      return true
    }

    const spaceId = parseSpaceId(to.params.spaceId)
    if (spaceId === null) {
      return { path: '/' }
    }

    if (workspaceStore.spaces.length === 0) {
      await workspaceStore.loadSpaces()
    }

    if (!workspaceStore.spaces.some((space) => String(space.id) === spaceId)) {
      return { path: '/' }
    }

    await workspaceStore.ensurePermissions(spaceId)

    const permission = to.meta.permission as SpacePermissionCode | undefined
    const effectivePermissions = workspaceStore.permissionsBySpace[spaceId]
    if (permission && !effectivePermissions?.permissions.includes(permission)) {
      return { path: '/forbidden' }
    }

    workspaceStore.setCurrentSpace(spaceId)
    return true
  })
}
