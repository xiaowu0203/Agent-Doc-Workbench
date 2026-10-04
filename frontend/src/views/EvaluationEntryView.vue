<template>
  <section class="evaluation-entry" :aria-label="sectionLabel">
    <h2>{{ sectionLabel }}{{ resourceId ? '详情' : '' }}</h2>
    <p v-if="resourceId" class="evaluation-entry__identity">资源 ID：{{ resourceId }}</p>
    <p v-if="sourceTaskId" class="evaluation-entry__identity">
      已选择来源任务 #{{ sourceTaskId }}。创建测试用例前需再次核验回放资格。
      <RouterLink
        v-if="canReadTask"
        :to="`/spaces/${route.params.spaceId}/tasks/${sourceTaskId}?tab=evidence`"
        >返回执行证据</RouterLink
      >
    </p>
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
import { RouterLink, useRoute } from 'vue-router'

import { EVALUATION_SECTIONS, type EvaluationSection } from '@/features/evaluation/navigation'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

const props = defineProps<{ section: EvaluationSection; resourceId?: string }>()
const route = useRoute(),
  workspace = useWorkspaceStore()
const sourceTaskId = computed(() =>
  props.section === 'test-cases' &&
  !props.resourceId &&
  typeof route.query.sourceTaskId === 'string' &&
  /^[1-9]\d{0,18}$/.test(route.query.sourceTaskId)
    ? route.query.sourceTaskId
    : null,
)
const canReadTask = computed(
  () =>
    String(workspace.currentSpaceId) === String(route.params.spaceId) &&
    workspace.hasPermission(SPACE_PERMISSIONS.TASK_READ),
)
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
