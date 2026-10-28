/** 通用交付等级说明 v1；与公开原始质量评分契约保持一致。 */
export const ORIGINAL_QUALITY_LEVELS = [
  { score: 0, label: '完全未达成' },
  { score: 1, label: '大量错误' },
  { score: 2, label: '部分达成' },
  { score: 3, label: '基本达成且需小改' },
  { score: 4, label: '完整达成' },
] as const
