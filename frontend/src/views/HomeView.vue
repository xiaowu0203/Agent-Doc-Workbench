<template>
  <main class="home-page">
    <section class="home-shell">
      <div class="home-header">
        <div>
          <p class="eyebrow">DOCUMENT COLLABORATION WORKBENCH</p>
          <h1>{{ appStore.name }}</h1>
          <p class="subtitle">面向个人与小团队的 Agent 活文档协作工作台。</p>
        </div>
        <el-tag type="success" effect="plain">Phase 0 · Ready</el-tag>
      </div>

      <el-card class="welcome-card" shadow="never">
        <template #header>
          <div class="card-title">
            <span>工程基建已就绪</span>
            <span class="version">v{{ appStore.version }}</span>
          </div>
        </template>
        <p>
          前端脚手架已连接 Pinia、Vue Router 与 Element
          Plus，后续页面将围绕文档、任务和审批流程展开。
        </p>
        <div class="stack-list">
          <el-tag v-for="item in stack" :key="item" effect="plain">{{ item }}</el-tag>
        </div>
      </el-card>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ElCard, ElTag } from 'element-plus'

import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
const stack = ['Vue 3', 'TypeScript strict', 'Pinia', 'Vue Router', 'Element Plus', 'Vitest']
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  padding: 64px 24px;
  background: #f5f7fa;
}

.home-shell {
  width: min(960px, 100%);
  margin: 0 auto;
}

.home-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 28px;
}

.eyebrow {
  margin: 0 0 10px;
  color: #909399;
  font-size: 12px;
  font-weight: 700;
}

h1 {
  margin: 0;
  color: #1f2937;
  font-size: 48px;
  line-height: 1.15;
}

.subtitle {
  margin: 14px 0 0;
  color: #606266;
  font-size: 16px;
}

.welcome-card {
  border-color: #e4e7ed;
}

.card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  color: #303133;
  font-weight: 700;
}

.version {
  color: #909399;
  font-size: 13px;
  font-weight: 400;
}

.welcome-card p {
  margin: 0;
  color: #606266;
  line-height: 1.7;
}

.stack-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 24px;
}

@media (max-width: 640px) {
  .home-page {
    padding: 32px 16px;
  }

  .home-header {
    flex-direction: column;
    gap: 16px;
  }

  h1 {
    font-size: 34px;
  }
}
</style>
