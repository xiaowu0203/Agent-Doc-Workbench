export interface User {
  id: string | number
  username: string
  nickname: string | null
  email: string | null
  avatarUrl: string | null
}

export interface AuthSession {
  accessToken: string
  refreshToken?: string
  user: User
  platformRoles?: string[]
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  nickname?: string
  email?: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export interface LoginResponse extends AuthSession {
  refreshToken: string
  tokenType: string
  expiresIn: number
}
