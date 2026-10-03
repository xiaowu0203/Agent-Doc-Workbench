export type CatalogSection = 'test-cases' | 'datasets' | 'evaluators'

/** 与首版内置评估器契约对应的最小示例；发布时由后端校验。 */
export const EVALUATOR_GUIDES = [
  { key: 'task-terminal-status', label: '任务终态', example: '{}' },
  {
    key: 'artifact-contract',
    label: '产物结构',
    example: '{"minCount":1,"maxCount":100,"schemaVersion":1,"requiredTypes":["RESULT_SUMMARY"]}',
  },
  {
    key: 'text-assertion',
    label: '文本断言',
    example: '{"assertions":[{"field":"resultSummary","operator":"CONTAINS","value":"预期文本"}]}',
  },
  {
    key: 'document-change-validator',
    label: '文档候选变更',
    example: '{"requiredArtifactType":"DRAFT_CHANGES"}',
  },
  { key: 'isolation-invariant', label: '隔离约束', example: '{}' },
  { key: 'audit-ledger-integrity', label: '审计与账本完整性', example: '{}' },
] as const

export function formatJson(value: string): string {
  return JSON.stringify(JSON.parse(value), null, 2)
}

export function replayReason(reason: string | null): string {
  const actions: Record<string, string> = {
    SOURCE_NOT_TERMINAL: '等待来源执行结束后重新选择。',
    UNSUPPORTED_SOURCE_LINEAGE: '请选择原始、重跑或审批返工的 LIVE 任务。',
    LINEAGE_ROOT_MISSING: '来源血缘不完整，请选择另一条任务。',
    INPUT_SNAPSHOT_MISSING: '来源没有冻结输入，请使用具备完整快照的新任务。',
    INPUT_SNAPSHOT_UNSUPPORTED: '来源输入快照版本不受支持，请选择另一条任务。',
    INPUT_SNAPSHOT_INVALID: '来源输入快照校验失败，请检查执行证据。',
    DOCUMENT_SNAPSHOT_INVALID: '冻结文档校验失败，请检查来源文档版本。',
    AGENT_EXECUTION_MISSING: '来源缺少执行记录，请选择已完成的任务。',
    MULTIPLE_AGENT_EXECUTION: '来源执行身份不唯一，请检查执行记录。',
    EXECUTION_SNAPSHOT_UNSUPPORTED: '执行快照版本不受支持，请选择另一条任务。',
    EXECUTION_SNAPSHOT_INVALID: '执行快照校验失败，请检查执行证据。',
    EXTERNAL_MCP_UNSUPPORTED: '来源包含外部 MCP，请选择符合隔离准入的来源。',
  }
  return `${reason || '资格未通过'}：${actions[reason || ''] || '检查来源执行证据，或选择另一条来源后重新核验。'}`
}
