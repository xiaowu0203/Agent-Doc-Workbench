<template>
  <section class="evaluation-entry" :aria-label="sectionLabel">
    <h2>{{ sectionLabel }}{{ resourceId ? '详情' : '' }}</h2>
    <p v-if="resourceId" class="evaluation-entry__identity">资源 ID：{{ resourceId }}</p>
    <div class="evaluation-entry__notice" role="status">
      <el-icon :size="28"><InfoFilled /></el-icon>
      <strong>资源查询与操作暂未开放</strong>
      <p>可通过页内导航切换评估目录、评估运行和离线实验。</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { InfoFilled } from '@element-plus/icons-vue'
import { ElIcon } from 'element-plus'
import { computed } from 'vue'

import { EVALUATION_SECTIONS, type EvaluationSection } from '@/features/evaluation/navigation'

const props = defineProps<{ section: EvaluationSection; resourceId?: string }>()
const sectionLabel = computed(
  () => EVALUATION_SECTIONS.find((item) => item.key === props.section)!.label,
)
</script>

<style scoped>
.evaluation-entry h2 {
  margin: 0;
  font-size: var(--adw-font-size-subtitle);
}
.evaluation-entry__identity {
  color: var(--adw-text-secondary);
  overflow-wrap: anywhere;
}
.evaluation-entry__notice {
  display: flex;
  min-height: 250px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: var(--adw-space-3);
  color: var(--adw-text-secondary);
  text-align: center;
}
.evaluation-entry__notice strong {
  color: var(--adw-text-primary);
}
.evaluation-entry__notice p {
  margin: 0;
  line-height: 1.6;
}
</style>
