<template>
  <div v-if="loading" class="data-state" role="status">
    <el-icon class="is-loading" :size="24"><Loading /></el-icon>
    <span>{{ loadingText }}</span>
  </div>
  <div v-else-if="error" class="data-state data-state--error" role="alert">
    <el-icon :size="24"><WarningFilled /></el-icon>
    <span>{{ error }}</span>
    <el-button v-if="retryable" type="primary" link @click="$emit('retry')">重新加载</el-button>
  </div>
  <div v-else-if="empty" class="data-state">
    <el-icon :size="26"><Document /></el-icon>
    <span>{{ emptyText }}</span>
  </div>
  <slot v-else />
</template>

<script setup lang="ts">
import { Document, Loading, WarningFilled } from '@element-plus/icons-vue'
import { ElButton, ElIcon } from 'element-plus'

withDefaults(
  defineProps<{
    loading?: boolean
    error?: string
    empty?: boolean
    retryable?: boolean
    loadingText?: string
    emptyText?: string
  }>(),
  {
    loading: false,
    error: '',
    empty: false,
    retryable: true,
    loadingText: '正在加载',
    emptyText: '暂无数据',
  },
)

defineEmits<{
  retry: []
}>()
</script>

<style scoped>
.data-state {
  display: flex;
  min-height: 180px;
  align-items: center;
  justify-content: center;
  gap: var(--adw-space-3);
  color: var(--adw-text-secondary);
}

.data-state--error {
  color: var(--adw-color-danger);
}
</style>
