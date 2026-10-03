import type { TraceMicroseconds, TraceSpan } from './engineering-types'

export function micros(value: TraceMicroseconds | null): number | null {
  if (value === null) return null
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : null
}

export function formatTraceDuration(value: TraceMicroseconds | null): string {
  const duration = micros(value)
  if (duration === null) return '不可用'
  return duration < 1_000_000
    ? `${(duration / 1000).toLocaleString('zh-CN', { maximumFractionDigits: 2 })} ms`
    : `${(duration / 1_000_000).toLocaleString('zh-CN', { maximumFractionDigits: 3 })} s`
}

export interface TraceRow {
  span: TraceSpan
  depth: number
  missingParent: boolean
}

/** 迭代展开父子关系，避免大投影递归；缺失父节点仍保留该分支。 */
export function traceRows(spans: TraceSpan[]): TraceRow[] {
  const index = new Map(spans.map((span) => [span.spanId, span]))
  const children = new Map<string, TraceSpan[]>()
  for (const span of spans) {
    if (span.parentSpanId && index.has(span.parentSpanId)) {
      const group = children.get(span.parentSpanId) ?? []
      group.push(span)
      children.set(span.parentSpanId, group)
    }
  }
  const byTime = (a: TraceSpan, b: TraceSpan) =>
    Number(a.startTimeMicros) - Number(b.startTimeMicros) || a.spanId.localeCompare(b.spanId)
  for (const group of children.values()) group.sort(byTime)
  const roots = spans
    .filter((span) => !span.parentSpanId || !index.has(span.parentSpanId))
    .sort(byTime)
  const rows: TraceRow[] = [],
    visited = new Set<string>()
  const append = (root: TraceSpan) => {
    const stack = [{ span: root, depth: 0, missingParent: Boolean(root.parentSpanId) }]
    while (stack.length) {
      const row = stack.pop()!
      if (visited.has(row.span.spanId)) continue
      visited.add(row.span.spanId)
      rows.push(row)
      for (const span of [...(children.get(row.span.spanId) ?? [])].reverse()) {
        stack.push({ span, depth: row.depth + 1, missingParent: false })
      }
    }
  }
  roots.forEach(append)
  // 服务端正常投影无循环；异常关系不使页面无限展开。
  spans
    .filter((span) => !visited.has(span.spanId))
    .sort(byTime)
    .forEach(append)
  return rows
}

/** 当前投影的最长调用链估计：父节点自身时间扣除子区间并集，避免嵌套耗时重复相加。 */
export function criticalTracePath(rows: TraceRow[]): Set<string> {
  const children = new Map<string, TraceRow[]>(),
    index = new Map(rows.map((row) => [row.span.spanId, row]))
  for (const row of rows) {
    const parent = row.span.parentSpanId ? index.get(row.span.parentSpanId) : undefined
    if (parent && row.depth > parent.depth) {
      const group = children.get(parent.span.spanId) ?? []
      group.push(row)
      children.set(parent.span.spanId, group)
    }
  }
  const scores = new Map<string, number>(),
    next = new Map<string, string>()
  for (const { span } of [...rows].reverse()) {
    const start = micros(span.startTimeMicros),
      duration = micros(span.durationMicros)
    if (start === null || duration === null) continue
    const group = children.get(span.spanId) ?? []
    const intervals = group
      .map(({ span: child }) => [
        Math.max(start, Number(child.startTimeMicros)),
        Math.min(start + duration, Number(child.startTimeMicros) + Number(child.durationMicros)),
      ])
      .filter(([a, b]) => b! > a!)
      .sort((a, b) => a[0]! - b[0]!)
    let covered = 0,
      end = start
    for (const [a, b] of intervals) {
      covered += Math.max(0, b! - Math.max(end, a!))
      end = Math.max(end, b!)
    }
    let best = 0
    for (const child of group) {
      const score = scores.get(child.span.spanId) ?? 0
      if (score > best) {
        best = score
        next.set(span.spanId, child.span.spanId)
      }
    }
    scores.set(span.spanId, Math.max(0, duration - covered) + best)
  }
  const roots = rows
    .filter((row) => row.depth === 0)
    .sort((a, b) => (scores.get(b.span.spanId) ?? 0) - (scores.get(a.span.spanId) ?? 0))
  const result = new Set<string>()
  let id: string | undefined = roots[0]?.span.spanId
  while (id && !result.has(id)) {
    result.add(id)
    id = next.get(id)
  }
  return result
}

export function spanHasRetry(span: TraceSpan): boolean {
  return span.attributes.some(
    (item) => item.key === 'agentdoc.retry.count' && Number(item.value) > 0,
  )
}

export function jaegerTraceUrl(
  base: string | undefined,
  isolationConfirmed: boolean,
  canReadTask: boolean,
  traceId: string | null,
): string | null {
  if (!base || !isolationConfirmed || !canReadTask || !traceId || !/^[a-f0-9]{32}$/i.test(traceId))
    return null
  try {
    const url = new URL(base)
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) return null
    url.pathname = `${url.pathname.replace(/\/$/, '')}/trace/${traceId}`
    url.search = ''
    url.hash = ''
    return url.toString()
  } catch {
    return null
  }
}

export const TRACE_ATTRIBUTE_KEYS = new Set([
  'run.id',
  'execution.id',
  'agentdoc.retry.count',
  'agentdoc.model.call.sequence',
  'gen_ai.usage.input_tokens',
  'gen_ai.usage.output_tokens',
  'agentdoc.skill.id',
  'agentdoc.skill.version_id',
  'agentdoc.skill.candidate_count',
  'agentdoc.skill.selected_count',
  'agentdoc.mcp.server_id',
  'agentdoc.tool.result_size_bytes',
  'agentdoc.execution.mode',
  'agentdoc.runtime.type',
  'agentdoc.tool.source',
  'agentdoc.skill.selection_mode',
  'agentdoc.operation.status',
  'gen_ai.operation.name',
  'gen_ai.provider.name',
  'gen_ai.request.model',
  'gen_ai.response.model',
  'agentdoc.tool.technical_name',
  'agentdoc.skill.technical_name',
  'agentdoc.mcp.transport',
  'agentdoc.tool.result_type',
  'agentdoc.model.stream',
  'agentdoc.mcp.external',
])
