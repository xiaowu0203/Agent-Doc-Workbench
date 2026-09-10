import { request } from '@/api/client'
import type {
  ChangePasswordRequest,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  User,
} from '@/features/auth/types'

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

export function refresh(): Promise<LoginResponse> {
  return request<LoginResponse>(
    {
      url: '/auth/refresh',
      method: 'POST',
    },
    { retryAfterRefresh: false },
  )
}

export function register(credentials: RegisterRequest): Promise<User> {
  return request<User>(
    {
      url: '/auth/register',
      method: 'POST',
      data: credentials,
    },
    { retryAfterRefresh: false },
  )
}

export function logout(): Promise<void> {
  return request<void>(
    {
      url: '/auth/logout',
      method: 'POST',
    },
    { retryAfterRefresh: false },
  )
}

export function changePassword(credentials: ChangePasswordRequest): Promise<void> {
  return request<void>({
    url: '/auth/password',
    method: 'PUT',
    data: credentials,
  })
}
