import type { ExecutionArtifact } from './engineering-types'

export interface ArtifactChangePreview {
  operation: string | null
  oldText: string | null
  newText: string
}
export type ArtifactContentPreview =
  | { kind: 'summary'; summary: string }
  | {
      kind: 'changes'
      baseVersion: string
      summary: string | null
      changes: (ArtifactChangePreview | null)[]
    }
  | { kind: 'unavailable'; reason: string }

/** 仅投影 schema 1 的业务字段。未知字段、工具参数和原始 JSON 不进入视图。 */
export function artifactContent(artifact: ExecutionArtifact): ArtifactContentPreview {
  const unavailable = (reason: string): ArtifactContentPreview => ({ kind: 'unavailable', reason })
  if (artifact.schemaVersion !== 1)
    return unavailable('此产物 schema 尚不支持正文预览，保留原始身份供核对。')
  try {
    const value: unknown = JSON.parse(artifact.payloadJson)
    if (!value || typeof value !== 'object' || Array.isArray(value))
      return unavailable('产物结构无效，无法读取正文。')
    const record = value as Record<string, unknown>
    if (artifact.artifactType === 'RESULT_SUMMARY')
      return typeof record.summary === 'string'
        ? { kind: 'summary', summary: record.summary }
        : unavailable('来源未提供结果摘要正文。')
    if (!['CHANGE_PROPOSAL', 'DRAFT_CHANGES'].includes(artifact.artifactType))
      return unavailable('此产物类型暂无正文预览。')
    if (!record.proposal || typeof record.proposal !== 'object' || Array.isArray(record.proposal))
      return unavailable('来源未提供有效的候选变更结构。')
    const proposal = record.proposal as Record<string, unknown>,
      base = proposal.baseVersion
    if (!(
      (typeof base === 'number' && Number.isSafeInteger(base) && base >= 0) ||
      (typeof base === 'string' && /^(0|[1-9]\d*)$/.test(base))
    ))
      return unavailable('来源缺少有效的冻结基线版本，不能核对变更正文。')
    if (!Array.isArray(proposal.changes)) return unavailable('来源未提供候选变更列表。')
    return {
      kind: 'changes',
      baseVersion: String(base),
      summary: typeof proposal.summary === 'string' ? proposal.summary : null,
      changes: proposal.changes.map((value: unknown) => {
        if (!value || typeof value !== 'object' || Array.isArray(value)) return null
        const row = value as Record<string, unknown>
        if (
          typeof row.newText !== 'string' ||
          !(row.oldText == null || typeof row.oldText === 'string')
        )
          return null
        return {
          operation: typeof row.op === 'string' ? row.op : null,
          oldText: typeof row.oldText === 'string' ? row.oldText : null,
          newText: row.newText,
        }
      }),
    }
  } catch {
    return unavailable('产物 JSON 无法解析，未展开原始载荷。')
  }
}

/** 只展示已知结构的数量和大小，不把候选正文、任意 JSON 字段或凭证渲染到诊断页。 */
export function artifactPreview(artifact: ExecutionArtifact): string {
  if (artifact.schemaVersion !== 1) return '此版本暂无结构摘要，原始产物保持不变。'
  try {
    const payload: unknown = JSON.parse(artifact.payloadJson)
    if (!payload || typeof payload !== 'object' || Array.isArray(payload)) return '产物结构不可用'
    const record = payload as Record<string, unknown>
    if (artifact.artifactType === 'RESULT_SUMMARY') {
      return typeof record.summary === 'string'
        ? `结果摘要已捕获，共 ${record.summary.length} 字符。`
        : '结果摘要未提供'
    }
    if (
      (artifact.artifactType === 'DRAFT_CHANGES' || artifact.artifactType === 'CHANGE_PROPOSAL') &&
      record.proposal &&
      typeof record.proposal === 'object' &&
      !Array.isArray(record.proposal)
    ) {
      const proposal = record.proposal as Record<string, unknown>
      if (Array.isArray(proposal.changes)) return `已捕获 ${proposal.changes.length} 处候选变更。`
    }
    return '候选产物已捕获，暂无结构摘要。'
  } catch {
    return '产物结构不可用'
  }
}
