import { describe, expect, it } from 'vitest'
import { businessTimeMillis, formatBusinessTime } from './date-time'

describe('business time display', () => {
  it('keeps legacy Shanghai local time independent from browser timezone', () => {
    expect(formatBusinessTime('2026-10-23T15:01:02.123456')).toBe('2026-10-23 15:01:02 +08:00')
  })
  it('converts actual offsets and UTC across day boundaries', () => {
    expect(formatBusinessTime('2026-10-22T20:01:02Z')).toBe('2026-10-23 04:01:02 +08:00')
    expect(formatBusinessTime('2026-10-23T00:01:02-05:00')).toBe('2026-10-23 13:01:02 +08:00')
  })
  it('preserves unknown and impossible calendar values instead of normalizing them', () => {
    for (const value of [
      '时间未知',
      '2026-02-30T00:00:00',
      '2026-10-23T25:00:00',
      '2026-13-01T00:00:00',
    ]) {
      expect(formatBusinessTime(value)).toBe(value)
    }
    expect(formatBusinessTime('2024-02-29T00:00:00')).toBe('2024-02-29 00:00:00 +08:00')
  })
  it('keeps missing states distinct', () => {
    expect(formatBusinessTime(null)).toBe('—')
    expect(formatBusinessTime(undefined, '尚未开始')).toBe('尚未开始')
    expect(formatBusinessTime('', '尚未结束')).toBe('尚未结束')
  })
  it('compares mixed local and UTC timeline timestamps using the same instant', () => {
    expect(businessTimeMillis('2026-10-23T08:00:00')).toBe(
      businessTimeMillis('2026-10-23T00:00:00Z'),
    )
    expect(Number.isNaN(businessTimeMillis('未知'))).toBe(true)
  })
})
