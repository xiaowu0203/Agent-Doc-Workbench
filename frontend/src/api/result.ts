export const API_SUCCESS_CODE = 0

export const AUTH_ERROR_CODES = new Set([40100, 40101, 41004])

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export function isAuthErrorCode(code: number): boolean {
  return AUTH_ERROR_CODES.has(code)
}
