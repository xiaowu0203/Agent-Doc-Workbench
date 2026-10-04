<template>
  <section class="evaluation-workbench">
    <PageHeader :title="currentGroup.label" :description="currentGroup.description">
      <template #breadcrumb
        ><span class="evaluation-workbench__breadcrumb">洞察 / 评估与实验</span></template
      >
    </PageHeader>

    <nav class="evaluation-workbench__tabs surface-card" aria-label="评估工作台导航">
      <RouterLink
        v-for="group in groups"
        :key="group.key"
        :to="sectionPath(group.section)"
        :class="{ 'is-active': currentGroup.key === group.key }"
        :aria-current="currentGroup.key === group.key ? 'page' : undefined"
        >{{ group.label }}</RouterLink
      >
    </nav>

    <div
      class="evaluation-workbench__body"
      :class="{ 'evaluation-workbench__body--catalog': currentGroup.key === 'catalog' }"
    >
      <aside
        v-if="currentGroup.key === 'catalog'"
        class="evaluation-workbench__catalog surface-card"
      >
        <nav aria-label="评估目录资源">
          <RouterLink
            v-for="section in catalogSections"
            :key="section.key"
            :to="sectionPath(section.key)"
            :class="{ 'is-active': route.meta.evaluationSection === section.key }"
            :aria-current="route.meta.evaluationSection === section.key ? 'page' : undefined"
            >{{ section.label }}</RouterLink
          >
        </nav>
        <div class="evaluation-workbench__rules">
          <strong>版本规则</strong>
          <p>DRAFT 可配置，发布后冻结。归档版本不再用于新的运行，历史结果仍可查看。</p>
        </div>
      </aside>
      <main class="evaluation-workbench__content surface-card"><RouterView /></main>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'

import { EVALUATION_SECTIONS, type EvaluationSection } from '@/features/evaluation/navigation'
import PageHeader from '@/shared/components/PageHeader.vue'

const route = useRoute()
const groups = [
  {
    key: 'catalog',
    label: '评估目录',
    section: 'test-cases',
    description: '用版本化测试资产，把真实任务沉淀为可复现的质量基线。',
  },
  {
    key: 'runs',
    label: '评估运行',
    section: 'runs',
    description: '通过隔离回放验证用例，按尝试、指标和证据查看评估结果。',
  },
  {
    key: 'experiments',
    label: '离线实验',
    section: 'experiments',
    description: '比较 baseline 与 candidate，基于冻结报告和证据作出人工结论。',
  },
] as const
const catalogSections = EVALUATION_SECTIONS.filter((section) => section.group === 'catalog')
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
  gap: var(--adw-space-5);
}
.evaluation-workbench__breadcrumb {
  display: block;
  margin-bottom: var(--adw-space-2);
  color: var(--adw-text-secondary);
  font-size: var(--adw-font-size-caption);
}
.evaluation-workbench__tabs {
  display: flex;
  gap: var(--adw-space-2);
  padding: 0 var(--adw-space-2);
  overflow-x: auto;
}
.evaluation-workbench__tabs a {
  flex-shrink: 0;
  padding: var(--adw-space-3) var(--adw-space-4);
  border-bottom: 2px solid transparent;
  color: var(--adw-text-secondary);
  text-decoration: none;
}
.evaluation-workbench__tabs a.is-active {
  border-bottom-color: var(--adw-color-primary);
  color: var(--adw-color-primary);
  font-weight: 600;
}
.evaluation-workbench__body {
  display: grid;
  gap: var(--adw-space-4);
}
.evaluation-workbench__body--catalog {
  grid-template-columns: 250px minmax(0, 1fr);
}
.evaluation-workbench__catalog {
  padding: var(--adw-space-3);
}
.evaluation-workbench__catalog nav {
  display: grid;
  gap: var(--adw-space-2);
}
.evaluation-workbench__catalog a {
  padding: var(--adw-space-4);
  border-radius: var(--adw-radius-sm);
  color: var(--adw-text-primary);
  text-decoration: none;
}
.evaluation-workbench__catalog a.is-active {
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
  font-weight: 600;
}
.evaluation-workbench__rules {
  margin-top: var(--adw-space-5);
  padding: var(--adw-space-4);
  border-left: 3px solid var(--adw-color-primary);
  border-radius: var(--adw-radius-sm);
  background: var(--adw-color-primary-soft);
  font-size: var(--adw-font-size-caption);
  line-height: 1.7;
}
.evaluation-workbench__rules p {
  margin: var(--adw-space-1) 0 0;
}
.evaluation-workbench__content {
  min-width: 0;
  min-height: 360px;
  padding: var(--adw-space-6);
}
.evaluation-workbench a:focus-visible {
  outline: 2px solid var(--adw-color-primary);
  outline-offset: -2px;
}
@media (max-width: 900px) {
  .evaluation-workbench__body--catalog {
    grid-template-columns: minmax(0, 1fr);
  }
  .evaluation-workbench__catalog nav {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .evaluation-workbench__catalog a {
    padding: var(--adw-space-3);
    text-align: center;
  }
  .evaluation-workbench__rules {
    margin-top: var(--adw-space-3);
  }
  .evaluation-workbench__content {
    padding: var(--adw-space-4);
  }
}
</style>
