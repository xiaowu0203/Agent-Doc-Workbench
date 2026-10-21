<template>
  <section class="evaluation-workbench">
    <PageHeader
      :title="isReview ? '独立评审' : '评估与实验'"
      :description="
        isReview ? '依据冻结输入评价原始产物，知情状态由服务端核验。' : currentGroup.description
      "
    >
      <template #breadcrumb
        ><span class="evaluation-workbench__breadcrumb"
          >洞察 / {{ isReview ? '原始产物评价' : '评估与实验' }}</span
        ></template
      >
    </PageHeader>
    <nav v-if="!isReview" class="evaluation-workbench__tabs" aria-label="评估工作台导航">
      <RouterLink
        v-for="section in EVALUATION_SECTIONS"
        :key="section.key"
        :to="sectionPath(section.key)"
        :aria-current="route.meta.evaluationSection === section.key ? 'page' : undefined"
        >{{ section.label }}</RouterLink
      >
    </nav>
    <main class="evaluation-workbench__content surface-card"><RouterView /></main>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { EVALUATION_SECTIONS, type EvaluationSection } from '@/features/evaluation/navigation'
import PageHeader from '@/shared/components/PageHeader.vue'

const route = useRoute()
const groups = [
  { key: 'catalog', description: '将真实任务沉淀为版本化测试资产，发布后冻结输入与绑定。' },
  { key: 'runs', description: '通过隔离回放验证用例，按尝试、指标和证据查看评估结果。' },
  { key: 'experiments', description: '比较基线与候选，基于冻结报告和证据作出人工结论。' },
  {
    key: 'online-experiments',
    description: '按文档固定分组，观察真实任务的原始质量、预算和运行风险。',
  },
] as const
const isReview = computed(() => route.name === 'evaluation-online-original-review')
const currentGroup = computed(() => {
  const section = EVALUATION_SECTIONS.find((item) => item.key === route.meta.evaluationSection)
  return groups.find((group) => group.key === section?.group) ?? groups[0]
})
function sectionPath(section: EvaluationSection): string {
  return `/spaces/${route.params.spaceId}/evaluation/${section}`
}
</script>

<style scoped>
.evaluation-workbench {
  display: grid;
  min-width: 0;
  gap: var(--adw-space-4);
}
.evaluation-workbench__breadcrumb {
  display: block;
  margin-bottom: var(--adw-space-2);
  color: var(--adw-text-secondary);
  font-size: var(--adw-font-size-caption);
}
.evaluation-workbench__tabs {
  display: flex;
  min-width: 0;
  overflow-x: auto;
  border-bottom: 1px solid var(--adw-border-color);
}
.evaluation-workbench__tabs a {
  flex-shrink: 0;
  padding: var(--adw-space-3) var(--adw-space-4);
  border-bottom: 2px solid transparent;
  color: var(--adw-text-secondary);
  text-decoration: none;
}
.evaluation-workbench__tabs a[aria-current='page'] {
  border-bottom-color: var(--adw-color-primary);
  color: var(--adw-color-primary);
  font-weight: 600;
}
.evaluation-workbench__content {
  min-width: 0;
  min-height: 360px;
  padding: var(--adw-space-5);
  container-type: inline-size;
}
.evaluation-workbench a:focus-visible {
  outline: 2px solid var(--adw-color-primary);
  outline-offset: -2px;
}
@media (max-width: 900px) {
  .evaluation-workbench__content {
    padding: var(--adw-space-4);
  }
}
</style>
