import { describe, expect, it } from 'vitest'
import type { ExecutionArtifact, TraceSpan } from './engineering-types'
import { artifactPreview } from './artifact-preview'
import {
  criticalTracePath,
  formatTraceDuration,
  jaegerTraceUrl,
  micros,
  spanHasRetry,
  traceRows,
} from './trace-view'

function span(id: string, parent: string | null, start: number, duration: number): TraceSpan {
  return {
    spanId: id,
    parentSpanId: parent,
    name: id,
    service: 'agent-service',
    category: 'GEN_AI',
    kind: 'CLIENT',
    status: 'OK',
    startTimeMicros: start,
    durationMicros: duration,
    attributes: [],
  }
}
describe('Trace projection calculations', () => {
  it('parses microsecond strings without losing IDs or treating them as milliseconds', () => {
    expect(micros('1791000000123456')).toBe(1791000000123456)
    expect(micros('9007199254740993')).toBeNull()
    expect(formatTraceDuration('1234567')).toBe('1.235 s')
    expect(formatTraceDuration(0)).toBe('0 ms')
    expect(formatTraceDuration(null)).toBe('不可用')
  })
  it('orders parent branches and retains dangling parents without recursive overflow', () => {
    const spans = Array.from({ length: 2000 }, (_, i) =>
      span(String(i), i ? String(i - 1) : null, i, 2000 - i),
    )
    const rows = traceRows([...spans].reverse())
    expect(rows).toHaveLength(2000)
    expect(rows.at(-1)?.depth).toBe(1999)
    const orphan = traceRows([span('orphan', 'absent', 0, 1)])
    expect(orphan[0]?.missingParent).toBe(true)
  })
  it('bounds malformed cycles and does not duplicate nodes', () => {
    const rows = traceRows([span('a', 'b', 0, 5), span('b', 'a', 1, 2)])
    expect(rows.map((row) => row.span.spanId)).toEqual(['a', 'b'])
    expect(criticalTracePath(rows).size).toBeLessThanOrEqual(2)
  })
  it('deducts overlapping child intervals when comparing linked paths', () => {
    const spans = [
      span('root', null, 0, 100),
      span('nested', 'root', 0, 100),
      span('a', 'nested', 0, 60),
      span('b', 'nested', 0, 60),
      span('longer', 'root', 20, 80),
    ]
    expect([...criticalTracePath(traceRows(spans))]).toEqual(['root', 'nested', 'a'])
    // nested 的有效链路为 40 + 60 = 100，重复嵌套不能变成 220。
    spans[1]!.durationMicros = 80
    spans[4]!.durationMicros = 90
    expect([...criticalTracePath(traceRows(spans))]).toEqual(['root', 'longer'])
  })
  it('recognizes retries only from the stable controlled attribute', () => {
    const node = span('a', null, 0, 0)
    node.attributes = [{ key: 'agentdoc.retry.count', valueType: 'LONG', value: '2' }]
    expect(spanHasRetry(node)).toBe(true)
    node.attributes[0]!.value = '0'
    expect(spanHasRetry(node)).toBe(false)
  })
  it('requires explicit isolation confirmation, task read and a valid trace for Jaeger', () => {
    const id = 'a'.repeat(32),
      base = 'https://traces.example.test/jaeger'
    expect(jaegerTraceUrl(base, true, true, id)).toBe(`${base}/trace/${id}`)
    expect(jaegerTraceUrl(base, false, true, id)).toBeNull()
    expect(jaegerTraceUrl(base, true, false, id)).toBeNull()
    expect(jaegerTraceUrl(base, true, true, '../x')).toBeNull()
    expect(jaegerTraceUrl('https://user:secret@example.test', true, true, id)).toBeNull()
    expect(jaegerTraceUrl('javascript:alert(1)', true, true, id)).toBeNull()
  })
})
describe('candidate artifact preview', () => {
  const artifact: ExecutionArtifact = {
    id: '1',
    taskId: '2',
    executionId: '3',
    sourceTaskId: null,
    sequenceNo: 1,
    sourceToolCallId: null,
    artifactType: 'CHANGE_PROPOSAL',
    schemaVersion: 1,
    payloadJson: '{"proposal":{"changes":[{"newText":"secret"}]},"token":"credential"}',
    payloadSha256: 'hash',
    createdAt: '2026-10-03',
  }
  it('exposes only known structural summaries, never arbitrary JSON contents', () => {
    expect(artifactPreview(artifact)).toBe('已捕获 1 处候选变更。')
    expect(artifactPreview({ ...artifact, artifactType: 'DRAFT_CHANGES' })).toBe(
      '已捕获 1 处候选变更。',
    )
    expect(artifactPreview({ ...artifact, payloadJson: '{"changes":["secret"]}' })).toBe(
      '候选产物已捕获，暂无结构摘要。',
    )
    expect(
      artifactPreview({
        ...artifact,
        artifactType: 'RESULT_SUMMARY',
        payloadJson: '{"summary":"<script>secret</script>"}',
      }),
    ).toContain('字符')
    expect(artifactPreview({ ...artifact, payloadJson: '{}' })).not.toContain('credential')
  })
  it('degrades invalid JSON and unknown schemas instead of guessing the shape', () => {
    expect(artifactPreview({ ...artifact, payloadJson: 'invalid' })).toBe('产物结构不可用')
    expect(artifactPreview({ ...artifact, schemaVersion: 2 })).toContain('此版本暂无结构摘要')
  })
})
