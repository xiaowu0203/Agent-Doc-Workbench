import { defineStore } from 'pinia'

import {
  createSpace as createSpaceRequest,
  deleteSpace as deleteSpaceRequest,
  getEffectivePermissions,
  listMySpaces,
} from '@/features/workspace/api/workspace-api'
import type {
  CreateSpaceRequest,
  EffectivePermission,
  EntityId,
  Space,
} from '@/features/workspace/types'
import type { SpacePermissionCode } from '@/shared/constants/permissions'

interface WorkspaceState {
  spaces: Space[]
  currentSpaceId: EntityId | null
  permissionsBySpace: Record<string, EffectivePermission | undefined>
}

export const useWorkspaceStore = defineStore('workspace', {
  state: (): WorkspaceState => ({
    spaces: [],
    currentSpaceId: null,
    permissionsBySpace: {},
  }),
  getters: {
    currentSpace: (state): Space | null =>
      state.spaces.find((space) => String(space.id) === String(state.currentSpaceId)) ?? null,
    currentPermissions: (state): EffectivePermission | null =>
      state.currentSpaceId === null
        ? null
        : (state.permissionsBySpace[state.currentSpaceId] ?? null),
  },
  actions: {
    hasPermission(permission: SpacePermissionCode): boolean {
      return this.currentPermissions?.permissions.includes(permission) ?? false
    },
    setCurrentSpace(spaceId: EntityId | null) {
      this.currentSpaceId = spaceId
    },
    setEffectivePermissions(permission: EffectivePermission) {
      this.permissionsBySpace[String(permission.spaceId)] = permission
    },
    invalidatePermissions(spaceId: EntityId) {
      delete this.permissionsBySpace[String(spaceId)]
    },
    async loadSpaces() {
      this.spaces = await listMySpaces()
      return this.spaces
    },
    async createSpace(payload: CreateSpaceRequest) {
      const created = await createSpaceRequest(payload)
      await this.loadSpaces()
      const space = this.spaces.find((item) => String(item.id) === String(created.id)) ?? created
      this.setCurrentSpace(space.id)
      await this.ensurePermissions(space.id, true)
      return space
    },
    async deleteSpace(spaceId: EntityId) {
      await deleteSpaceRequest(spaceId)
      delete this.permissionsBySpace[String(spaceId)]
      if (String(this.currentSpaceId) === String(spaceId)) this.setCurrentSpace(null)
      await this.loadSpaces()
    },
    async ensurePermissions(spaceId: EntityId, force = false) {
      const key = String(spaceId)
      if (!force && this.permissionsBySpace[key]) {
        return this.permissionsBySpace[key]
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
