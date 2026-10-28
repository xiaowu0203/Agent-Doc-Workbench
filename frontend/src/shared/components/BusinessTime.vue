<template>
  <span class="business-time">
    <time :title="value || undefined">{{ formatBusinessTime(value, emptyText) }}</time>
    <details v-if="value && /\.\d+/.test(value)">
      <summary>原始精度</summary>
      <span>{{ value }}</span>
    </details>
  </span>
</template>
<script setup lang="ts">
import { formatBusinessTime } from '@/shared/utils/date-time'
withDefaults(defineProps<{ value?: string | null; emptyText?: string }>(), {
  value: null,
  emptyText: '—',
})
</script>
<style scoped>
.business-time { display: inline; }
details { display: inline; margin-left: 0.35em; }
summary { display: inline; cursor: pointer; text-decoration: underline; }
details span { overflow-wrap: anywhere; }
</style>
