import type { DailyUsage } from '@/features/usage/types'

export function buildUsageChartData(trend: DailyUsage[]) {
  const max = Math.max(
    1,
    ...trend.map((item) => item.tokens ?? (item.inputTokens ?? 0) + (item.outputTokens ?? 0)),
  )
  return {
    max,
    dates: trend.map((item) => item.usageDate.slice(5)),
    inputTokens: trend.map((item) => item.inputTokens ?? 0),
    outputTokens: trend.map((item) => item.outputTokens ?? 0),
    totalTokens: trend.map((item) => item.tokens),
  }
}
