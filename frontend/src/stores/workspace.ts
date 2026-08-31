import { defineStore } from 'pinia'

import { getEffectivePermissions, listMySpaces } from '@/features/workspace/api/workspace-api'
import type { EffectivePermission, Space } from '@/features/workspace/types'
import type { SpacePermissionCode } from '@/shared/constants/permissions'

interface WorkspaceState {
  spaces: Space[]
  currentSpaceId: number | null
  permissionsBySpace: Record<number, EffectivePermission | undefined>
}

export const useWorkspaceStore = defineStore('workspace', {
  state: (): WorkspaceState => ({
    spaces: [],
    currentSpaceId: null,
    permissionsBySpace: {},
  }),
  getters: {
    currentSpace: (state): Space | null =>
      state.spaces.find((space) => space.id === state.currentSpaceId) ?? null,
    currentPermissions: (state): EffectivePermission | null =>
      state.currentSpaceId === null
        ? null
        : (state.permissionsBySpace[state.currentSpaceId] ?? null),
  },
  actions: {
    hasPermission(permission: SpacePermissionCode): boolean {
      return this.currentPermissions?.permissions.includes(permission) ?? false
    },
    setCurrentSpace(spaceId: number | null) {
      this.currentSpaceId = spaceId
    },
    setEffectivePermissions(permission: EffectivePermission) {
      this.permissionsBySpace[permission.spaceId] = permission
    },
    invalidatePermissions(spaceId: number) {
      delete this.permissionsBySpace[spaceId]
    },
    async loadSpaces() {
      this.spaces = await listMySpaces()
      return this.spaces
    },
    async ensurePermissions(spaceId: number, force = false) {
      if (!force && this.permissionsBySpace[spaceId]) {
        return this.permissionsBySpace[spaceId]
      }
      const permission = await getEffectivePermissions(spaceId)
      this.setEffectivePermissions(permission)
      return permission
    },
    clearWorkspace() {
      this.spaces = []
      this.currentSpaceId = null
      this.permissionsBySpace = {}
    },
  },
})
