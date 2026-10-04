export const EVALUATION_SECTIONS = [
  { key: 'test-cases', label: '测试用例', group: 'catalog' },
  { key: 'datasets', label: '数据集', group: 'catalog' },
  { key: 'evaluators', label: '评估器', group: 'catalog' },
  { key: 'runs', label: '评估运行', group: 'runs' },
  { key: 'experiments', label: '离线实验', group: 'experiments' },
] as const

export type EvaluationSection = (typeof EVALUATION_SECTIONS)[number]['key']
