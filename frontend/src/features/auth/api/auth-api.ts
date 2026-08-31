import { request } from '@/api/client'
import type { LoginRequest, LoginResponse } from '@/features/auth/types'

export function login(credentials: LoginRequest): Promise<LoginResponse> {
  return request<LoginResponse>(
    {
      url: '/auth/login',
      method: 'POST',
      data: credentials,
    },
    { retryAfterRefresh: false },
  )
}

export function refresh(refreshToken: string): Promise<LoginResponse> {
  return request<LoginResponse>(
    {
      url: '/auth/refresh',
      method: 'POST',
      data: { refreshToken },
    },
    { retryAfterRefresh: false },
  )
}
