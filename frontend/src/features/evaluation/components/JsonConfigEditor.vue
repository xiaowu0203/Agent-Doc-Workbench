<template>
  <div class="json-editor">
    <div class="json-editor__heading">
      <label :for="inputId">{{ label }}</label>
      <el-button v-if="!readonly" link @click="format">校验并格式化</el-button>
    </div>
    <el-input
      :id="inputId"
      type="textarea"
      :aria-label="label"
      :model-value="modelValue"
      :readonly="readonly"
      :rows="rows"
      spellcheck="false"
      @input="update"
    />
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-else-if="validated" role="status">JSON 语法有效；领域契约由后端校验。</p>
  </div>
</template>
<script setup lang="ts">
import { ref, useId } from 'vue'
import { ElButton, ElInput } from 'element-plus'
import { formatJson } from '../catalog'
const props = withDefaults(
  defineProps<{ modelValue: string; label: string; readonly?: boolean; rows?: number }>(),
  { readonly: false, rows: 7 },
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const inputId = useId(),
  error = ref(''),
  validated = ref(false)
function update(value: string) {
  error.value = ''
  validated.value = false
  emit('update:modelValue', value)
}
function format() {
  try {
    emit('update:modelValue', formatJson(props.modelValue))
    error.value = ''
    validated.value = true
  } catch {
    error.value = 'JSON 语法无效，请检查后重试。'
    validated.value = false
  }
}
</script>
<style scoped>
.json-editor {
  display: grid;
  gap: var(--adw-space-2);
}
.json-editor__heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
:deep(.el-textarea__inner) {
  box-sizing: border-box;
  width: 100%;
  resize: vertical;
  padding: var(--adw-space-3);
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
  background: var(--adw-surface);
  color: var(--adw-text-primary);
  font: 13px/1.65 monospace;
}
:deep(.el-textarea__inner:focus-visible) {
  outline: 2px solid var(--adw-color-primary);
}
:deep(.el-textarea__inner[readonly]) {
  background: var(--adw-surface-muted);
}
p {
  margin: 0;
  color: var(--adw-text-secondary);
  font-size: var(--adw-font-size-caption);
}
p[role='alert'] {
  color: var(--adw-color-danger);
}
</style>
