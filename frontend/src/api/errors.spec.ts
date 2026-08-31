import { describe, expect, it } from 'vitest'

import { ApiError, normalizeApiError, unwrapApiResult } from './errors'

describe('API error helpers', () => {
  it('unwraps successful API results', () => {
    expect(unwrapApiResult({ code: 0, message: 'success', data: { id: 1 } })).toEqual({ id: 1 })
  })

  it('throws a typed error for business failures returned with HTTP 200', () => {
    expect(() =>
      unwrapApiResult({ code: 40300, message: '无权限访问', data: null }, 200),
    ).toThrowError(ApiError)

    try {
      unwrapApiResult({ code: 40900, message: '资源状态冲突', data: null }, 200)
    } catch (error) {
      expect(error).toMatchObject({ code: 40900, status: 200, message: '资源状态冲突' })
    }
  })

  it('normalizes unknown errors', () => {
    const error = normalizeApiError(new Error('boom'))
    expect(error).toBeInstanceOf(ApiError)
    expect(error.message).toBe('boom')
  })
})
