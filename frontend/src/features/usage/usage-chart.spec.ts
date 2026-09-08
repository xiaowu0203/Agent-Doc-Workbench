import { describe, expect, it } from 'vitest'

import type { DailyUsage } from '@/features/usage/types'
import { buildUsageChartData } from '@/features/usage/usage-chart'

function usage(date: string, input: number | null, output: number | null): DailyUsage {
  return {
    usageDate: date,
    hasData: input !== null || output !== null,
    inputTokens: input,
    outputTokens: output,
    tokens: input === null || output === null ? null : input + output,
    estimatedCost: 0,
    inputTokensEstimated: false,
    outputTokensEstimated: false,
    hasIncompleteData: input === null || output === null,
  }
}

describe('buildUsageChartData', () => {
  it('converts daily usage into ECharts category and series data', () => {
    const result = buildUsageChartData([usage('2026-09-01', 60, 40), usage('2026-09-02', 20, 30)])

    expect(result.max).toBe(100)
    expect(result.dates).toEqual(['09-01', '09-02'])
    expect(result.inputTokens).toEqual([60, 20])
    expect(result.outputTokens).toEqual([40, 30])
    expect(result.totalTokens).toEqual([100, 50])
  })

  it('keeps empty and incomplete days on a safe zero baseline', () => {
    const result = buildUsageChartData([
      usage('2026-09-01', null, null),
      usage('2026-09-02', null, 10),
    ])

    expect(result.max).toBe(10)
    expect(result.inputTokens).toEqual([0, 0])
    expect(result.outputTokens).toEqual([0, 10])
    expect(result.totalTokens).toEqual([null, null])
  })
})
