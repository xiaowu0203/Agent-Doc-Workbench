<template>
  <section class="forbidden-view surface-card">
    <el-icon :size="44"><Lock /></el-icon>
    <h1>无权访问此页面</h1>
    <p>你的空间权限可能已发生变化，请返回可访问的空间。</p>
    <el-button type="primary" @click="$router.replace('/')">返回工作台</el-button>
  </section>
</template>

<script setup lang="ts">
import { Lock } from '@element-plus/icons-vue'
import { ElButton, ElIcon } from 'element-plus'
</script>

<style scoped>
.forbidden-view {
  display: flex;
  min-height: 360px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  padding: var(--adw-space-7);
  text-align: center;
}

.forbidden-view .el-icon {
  color: var(--adw-color-warning);
}

.forbidden-view h1 {
  margin: var(--adw-space-4) 0 var(--adw-space-2);
}

.forbidden-view p {
  margin: 0 0 var(--adw-space-5);
  color: var(--adw-text-secondary);
}
</style>
