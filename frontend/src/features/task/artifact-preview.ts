import type { ExecutionArtifact } from './engineering-types'

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
