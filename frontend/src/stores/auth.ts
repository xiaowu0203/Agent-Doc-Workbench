import { defineStore } from 'pinia'

import {
  changePassword as requestChangePassword,
  login as requestLogin,
  logout as requestLogout,
  refresh as requestRefresh,
} from '@/features/auth/api/auth-api'
import type { AuthSession, ChangePasswordRequest, LoginRequest, User } from '@/features/auth/types'
import { PLATFORM_ROLES } from '@/shared/constants/platform-roles'

interface AuthState {
  accessToken: string | null
  user: User | null
  platformRoles: string[]
  initialized: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    accessToken: null,
    user: null,
    platformRoles: [],
    initialized: false,
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.accessToken && state.user),
    isPlatformSuperAdmin: (state) => state.platformRoles.includes(PLATFORM_ROLES.SUPER_ADMIN),
  },
  actions: {
    async login(credentials: LoginRequest) {
      const session = await requestLogin(credentials)
      this.setSession(session)
    },
    async changePassword(credentials: ChangePasswordRequest) {
      await requestChangePassword(credentials)
    },
    async logout() {
      try {
        await requestLogout()
      } finally {
        this.clearSession()
      }
    },
    setSession(session: AuthSession) {
      this.accessToken = session.accessToken
      this.user = session.user
      this.platformRoles = session.platformRoles ?? []
      this.initialized = true
    },
    async restoreSession(): Promise<boolean> {
      try {
        const session = await requestRefresh()
        this.setSession(session)
        return true
      } catch {
        this.clearSession()
        return false
      }
    },
    async refreshAccessToken(): Promise<string | null> {
      const session = await requestRefresh()
      this.setSession(session)
      return this.accessToken
    },
    clearSession() {
      this.accessToken = null
      this.user = null
      this.platformRoles = []
      this.initialized = true
    },
  },
})
