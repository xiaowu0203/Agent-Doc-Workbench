<template>
  <details v-if="missingReason" class="metric-value--missing">
    <summary>{{ missing?.[0] || '缺失：原因尚未识别' }}</summary>
    <span>{{ missing?.[1] || '保留服务端原因码，不推断为零值或规则通过。' }}</span>
    <small>原因码：{{ missingReason }}</small>
  </details>
  <span
    v-else-if="numericValue !== null && numericValue !== undefined"
    :title="`原值：${numericValue}${unit ? ` ${unit}` : ''}`"
  >
    {{ formatMetricNumber(numericValue, unit) }}
  </span>
  <span v-else-if="booleanValue !== null && booleanValue !== undefined">{{
    String(booleanValue)
  }}</span>
  <span v-else-if="stringValue !== null && stringValue !== undefined">
    {{ stringValue === '' ? '空字符串' : stringValue }}
  </span>
  <span v-else class="metric-value--missing">不可用</span>
</template>

<script setup lang="ts">
import { formatMetricNumber } from '../display'
import { computed } from 'vue'
const props = withDefaults(
  defineProps<{
    numericValue?: number | null
    booleanValue?: boolean | null
    stringValue?: string | null
    unit?: string | null
    missingReason?: string | null
  }>(),
  { numericValue: null, booleanValue: null, stringValue: null, unit: null, missingReason: null },
)
const reasons: Record<string, [string, string]> = {
  VALUE_MISSING: ['数据不可用', '此指标未返回数值、布尔值或文本值，不能按零值解释。'],
  NO_EFFECTIVE_METRIC: ['无有效结果', '当前范围没有可用于比较的有效指标，缺失不计为通过。'],
  EVIDENCE_MISSING: ['证据缺失', '缺少本次评价需要的冻结证据，不能据此判断质量。'],
  NOT_APPLICABLE: ['不适用', '此指标不适用于当前评价对象，不计为零分。'],
  REPLAY_FAILED: ['回放失败', '执行回放失败，未得到可评价的结果。'],
  EVALUATOR_ERROR: ['评估异常', '评估器未正常产生结果，不能将异常当作规则未通过。'],
}
const missing = computed(() => reasons[props.missingReason || ''])
</script>

<style scoped>
.metric-value--missing {
  color: var(--adw-text-secondary);
}
.metric-value--missing small {
  display: block;
  overflow-wrap: anywhere;
}
</style>
