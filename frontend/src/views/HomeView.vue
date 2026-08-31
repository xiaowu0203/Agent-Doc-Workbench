<template>
  <section class="foundation-page">
    <PageHeader
      title="Phase 6 前端基础层"
      description="统一布局、请求、会话、权限与视觉规范已经进入可复用基线。"
    >
      <template #actions>
        <el-tag type="success" effect="plain">Foundation Ready</el-tag>
      </template>
    </PageHeader>

    <div class="foundation-grid">
      <article v-for="item in foundations" :key="item.title" class="foundation-card surface-card">
        <div class="foundation-card__icon">
          <el-icon :size="22"><component :is="item.icon" /></el-icon>
        </div>
        <div>
          <h2>{{ item.title }}</h2>
          <p>{{ item.description }}</p>
        </div>
      </article>
    </div>

    <el-card class="foundation-next" shadow="never">
      <template #header>
        <div class="foundation-next__header">
          <strong>下一交付切片</strong>
          <span>v{{ appStore.version }}</span>
        </div>
      </template>
      <p>登录页将直接复用本次建立的会话桥接、错误模型、设计令牌和路由守卫。</p>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { Connection, Key, Lock, Monitor } from '@element-plus/icons-vue'
import { ElCard, ElIcon, ElTag } from 'element-plus'

import PageHeader from '@/shared/components/PageHeader.vue'

import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
const foundations = [
  {
    title: '应用布局',
    description: '统一侧栏、顶栏、内容容器和页面标题结构。',
    icon: Monitor,
  },
  {
    title: '请求与会话',
    description: '统一 Result 解包、错误语义、认证头和刷新并发控制。',
    icon: Connection,
  },
  {
    title: '空间权限',
    description: '权限标识符、空间缓存、路由守卫和组件权限门使用同一来源。',
    icon: Lock,
  },
  {
    title: '安全基线',
    description: 'Access Token 只保留在内存，会话存储策略不散落到页面。',
    icon: Key,
  },
]
</script>

<style scoped>
.foundation-page {
  display: grid;
  gap: var(--adw-space-7);
}

.foundation-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--adw-space-5);
}

.foundation-card {
  display: flex;
  min-height: 154px;
  gap: var(--adw-space-4);
  padding: var(--adw-space-5);
}

.foundation-card__icon {
  display: inline-flex;
  width: 44px;
  height: 44px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: var(--adw-color-primary);
  background: #edf3ff;
}

.foundation-card h2 {
  margin: 2px 0 var(--adw-space-2);
  color: var(--adw-text-primary);
  font-size: var(--adw-font-size-subtitle);
}

.foundation-card p,
.foundation-next p {
  margin: 0;
  color: var(--adw-text-secondary);
  line-height: 1.7;
}

.foundation-next__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.foundation-next__header span {
  color: var(--adw-text-tertiary);
  font-size: var(--adw-font-size-caption);
}

@media (max-width: 1100px) {
  .foundation-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .foundation-grid {
    grid-template-columns: 1fr;
  }
}
</style>
