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
