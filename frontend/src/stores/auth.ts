import { defineStore } from 'pinia'

import { login as requestLogin } from '@/features/auth/api/auth-api'
import type { AuthSession, LoginRequest, User } from '@/features/auth/types'

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
    isPlatformSuperAdmin: (state) => state.platformRoles.includes('PLATFORM_SUPER_ADMIN'),
  },
  actions: {
    async login(credentials: LoginRequest) {
      const session = await requestLogin(credentials)
      this.setSession(session)
    },
    setSession(session: AuthSession) {
      this.accessToken = session.accessToken
      this.user = session.user
      this.platformRoles = session.platformRoles ?? []
      this.initialized = true
    },
    clearSession() {
      this.accessToken = null
      this.user = null
      this.platformRoles = []
      this.initialized = true
    },
  },
})
