<template>
  <el-tag :type="presentation.type" :title="status || undefined" :aria-label="accessibleLabel">
    {{ presentation.label }}
  </el-tag>
</template>

<script setup lang="ts">
import { ElTag } from 'element-plus'
import { computed } from 'vue'
import { evaluationStatus } from '../status'

const props = defineProps<{
  domain: 'version' | 'run' | 'attempt' | 'result' | 'experiment' | 'decision' | 'feedback'
  status: string | null
}>()

const presentation = computed(() => evaluationStatus(props.domain, props.status))
const accessibleLabel = computed(() =>
  props.status ? `${presentation.value.label} (${props.status})` : presentation.value.label,
)
</script>
