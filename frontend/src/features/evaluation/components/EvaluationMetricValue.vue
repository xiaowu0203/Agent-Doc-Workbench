<template>
  <span v-if="missingReason" class="metric-value--missing">缺失 · {{ missingReason }}</span>
  <span v-else-if="numericValue !== null && numericValue !== undefined">
    {{ `${numericValue}${unit ? ` ${unit}` : ''}` }}
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
withDefaults(
  defineProps<{
    numericValue?: number | null
    booleanValue?: boolean | null
    stringValue?: string | null
    unit?: string | null
    missingReason?: string | null
  }>(),
  { numericValue: null, booleanValue: null, stringValue: null, unit: null, missingReason: null },
)
</script>

<style scoped>
.metric-value--missing {
  color: var(--adw-text-secondary);
}
</style>
