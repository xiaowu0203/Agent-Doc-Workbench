<template>
  <el-button link size="small" :aria-label="`复制${label}`" :disabled="copying" @click="copy">
    复制
  </el-button>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElButton, ElMessage } from 'element-plus'

const props = defineProps<{ value: string; label: string }>()
const copying = ref(false)
async function copy() {
  copying.value = true
  try {
    await globalThis.navigator.clipboard.writeText(props.value)
    ElMessage.success(`${props.label}已复制`)
  } catch {
    ElMessage.warning('复制失败，请选中原文手动复制')
  } finally {
    copying.value = false
  }
}
</script>
