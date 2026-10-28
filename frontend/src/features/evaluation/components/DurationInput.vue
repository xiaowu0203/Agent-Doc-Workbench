<template>
  <div class="duration-input">
    <label
      >{{ label
      }}<el-input
        :model-value="amount"
        :aria-label="`${label}数值`"
        type="number"
        min="0"
        step="1"
        :disabled="disabled"
        @input="update"
    /></label>
    <label
      >单位<el-select
        v-model="unit"
        :aria-label="`${label}单位`"
        :disabled="disabled"
        @change="changeUnit"
      >
        <el-option
          v-for="item in units"
          :key="item.seconds"
          :value="item.seconds"
          :disabled="!Number.isInteger(modelValue / item.seconds)"
          :label="item.label"
        />
      </el-select></label
    >
    <small
      >输入整数，精确
      {{ Number.isInteger(modelValue) ? modelValue : '待填写' }} 秒；不四舍五入。</small
    >
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElInput, ElSelect, ElOption } from 'element-plus'
const props = withDefaults(
  defineProps<{ modelValue: number; label: string; disabled?: boolean }>(),
  { disabled: false },
)
const emit = defineEmits<{ 'update:modelValue': [seconds: number] }>()
const units = [
  { seconds: 86400, label: '天' },
  { seconds: 3600, label: '小时' },
  { seconds: 60, label: '分钟' },
  { seconds: 1, label: '秒' },
]
const unit = ref(
  units.find((item) => props.modelValue > 0 && Number.isInteger(props.modelValue / item.seconds))
    ?.seconds || 1,
)
const amount = ref(String(props.modelValue / unit.value))
watch(
  () => props.modelValue,
  (seconds) => {
    if (!Number.isFinite(seconds)) return
    if (!Number.isInteger(seconds / unit.value)) unit.value = 1
    amount.value = String(seconds / unit.value)
  },
)
function changeUnit() {
  amount.value = String(props.modelValue / unit.value)
}
function update(value: string) {
  if (props.disabled) return
  amount.value = value
  const seconds = Number(amount.value) * unit.value
  emit(
    'update:modelValue',
    amount.value !== '' && Number.isInteger(Number(amount.value)) && Number.isSafeInteger(seconds)
      ? seconds
      : NaN,
  )
}
</script>
<style scoped>
.duration-input {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  min-width: 0;
}
label {
  display: grid;
  gap: 5px;
}
input,
select {
  box-sizing: border-box;
  min-width: 0;
  width: 100%;
  padding: 8px;
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
  background: var(--adw-surface);
  color: var(--adw-text-primary);
  font: inherit;
}
small {
  grid-column: 1 / -1;
  color: var(--adw-text-secondary);
}
</style>
