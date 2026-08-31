export interface User {
  id: number
  username: string
  nickname: string | null
  email: string | null
  avatarUrl: string | null
}

export interface AuthSession {
  accessToken: string
  user: User
  platformRoles?: string[]
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse extends AuthSession {
  refreshToken: string
  tokenType: string
  expiresIn: number
}
