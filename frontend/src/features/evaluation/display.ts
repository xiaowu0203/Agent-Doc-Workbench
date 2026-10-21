/** 仅用于展示，计算、比较与报告身份始终使用冻结原值。 */
export function formatMetricNumber(value: unknown, unit?: string | null): string {
  if (value === null || value === undefined || value === '') return '未知'
  const raw = String(value)
  if (!/^-?\d+(\.\d+)?([eE][+-]?\d+)?$/.test(raw)) return raw
  if (typeof value === 'string' && /^-?\d+(\.\d+)?$/.test(raw)) {
    const integer = BigInt(raw.split('.')[0]!)
    if (integer > BigInt(Number.MAX_SAFE_INTEGER) || integer < BigInt(Number.MIN_SAFE_INTEGER)) {
      return /^-?\d+$/.test(raw) ? raw.replace(/\B(?=(\d{3})+(?!\d))/g, ',') : raw
    }
  }
  const number = Number(raw)
  if (!Number.isFinite(number)) return raw
  if (Math.abs(number) > Number.MAX_SAFE_INTEGER) {
    return /^-?\d+$/.test(raw) ? raw.replace(/\B(?=(\d{3})+(?!\d))/g, ',') : raw
  }
  const displayed =
    unit === 'ratio' || unit === 'percentage-point'
      ? number * 100
      : unit === 'millisecond'
        ? number / 1000
        : number
  const digits = unit && /^[A-Z]{3}$/.test(unit) ? 6 : 2
  const formatted =
    displayed !== 0 && Math.abs(displayed) < 10 ** -digits
      ? displayed.toExponential(2)
      : displayed.toLocaleString('zh-CN', { maximumFractionDigits: digits })
  const suffix =
    unit === 'ratio'
      ? '%'
      : unit === 'percentage-point'
        ? ' 个百分点'
        : unit === 'millisecond'
          ? ' 秒'
          : unit && !['count', 'token'].includes(unit)
            ? ` ${unit}`
            : ''
  return `${formatted}${suffix}`
}

const metricLabels: Record<string, string> = {
  'evaluation.artifact-contract.valid': '产物结构有效性',
  'evaluation.isolation-invariant.success': '隔离约束校验',
  'evaluation.task-terminal.success': '任务终态校验',
  'evaluation.document-change.accuracy': '文档变更准确率',
  'evaluation.document-change.valid': '文档变更有效性',
  'execution.cost': '执行费用',
  'execution.latency': '执行耗时',
  'execution.total-tokens': '总 Token',
  'execution.input-tokens': '输入 Token',
  'execution.output-tokens': '输出 Token',
  'execution.cached-input-tokens': '缓存输入 Token',
  'execution.retry-count': '重试次数',
  'execution.success': '执行成功',
}

export function metricLabel(key: string): string {
  return metricLabels[key] || key
}
