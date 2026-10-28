<template>
  <section class="rule-editor">
    <div v-if="supported" class="rule-editor__mode">
      <strong>{{ label }}</strong>
      <el-button link @click="jsonMode = !jsonMode">{{
        jsonMode ? (readonly ? '查看字段' : '使用字段表单') : readonly ? '查看 JSON' : '编辑 JSON'
      }}</el-button>
    </div>
    <template v-if="supported && !jsonMode">
      <fieldset :disabled="readonly">
        <template v-if="evaluatorKey === 'artifact-contract'">
          <label v-for="field in countFields" :key="field.key"
            >{{ field.label }}
            <el-input
              :model-value="String(config[field.key] ?? '')"
              type="number"
              :aria-label="field.label"
              :min="field.min"
              :max="field.max"
              step="1"
              :placeholder="field.placeholder"
              @input="numberField(field.key, $event)"
            />
          </label>
          <label
            >必需产物类型（每行一个）<el-input
              type="textarea"
              :model-value="types"
              aria-label="必需产物类型"
              :rows="3"
              placeholder="例如 RESULT_SUMMARY、CHANGE_PROPOSAL、DRAFT_CHANGES"
              @change="
                setField(
                  'requiredTypes',
                  $event
                    .split('\n')
                    .map((value) => value.trim())
                    .filter(Boolean),
                )
              "
            />
          </label>
        </template>
        <template v-else-if="evaluatorKey === 'text-assertion'">
          <p>验证执行结果摘要。所有断言须通过；留空使用绑定或用例中的期望。</p>
          <div v-for="(item, index) in assertions" :key="index" class="rule-editor__assertion">
            <label
              >比较方式<select
                :value="item.operator"
                :aria-label="`文本断言${index + 1}方式`"
                @change="
                  updateAssertion(index, 'operator', ($event.target as HTMLSelectElement).value)
                "
              >
                <option value="CONTAINS">包含</option>
                <option value="NOT_CONTAINS">不包含</option>
                <option value="EXACT">完全一致</option>
                <option value="SHA256">SHA-256 一致</option>
              </select></label
            >
            <label
              >预期文本或摘要<el-input
                type="textarea"
                :model-value="item.value"
                :aria-label="`文本断言${index + 1}内容`"
                maxlength="1000"
                :rows="2"
                @input="
                  updateAssertion(index, 'value', $event)
                "
              />
            </label>
            <el-button
              @click="
                setField(
                  'assertions',
                  assertions.filter((_, position) => position !== index),
                  true,
                )
              "
              >移除断言 {{ index + 1 }}</el-button
            >
          </div>
          <el-button
            :disabled="assertions.length >= 50"
            @click="
              setField('assertions', [
                ...assertions,
                { field: 'resultSummary', operator: 'CONTAINS', value: '' },
              ])
            "
            >添加文本断言</el-button
          >
        </template>
        <template v-else>
          <label
            >必需候选产物类型<el-input
              :model-value="String(config.requiredArtifactType ?? '')"
              aria-label="必需候选产物类型"
              placeholder="例如 DRAFT_CHANGES 或 CHANGE_PROPOSAL"
              @input="
                setField('requiredArtifactType', $event, true)
              "
          /></label>
          <label
            >目标正文 SHA-256（可选）<el-input
              :model-value="String(config.targetContentSha256 ?? '')"
              aria-label="目标正文摘要"
              maxlength="64"
              pattern="[0-9a-f]{64}"
              @input="
                setField('targetContentSha256', $event, true)
              "
          /></label>
        </template>
      </fieldset>
      <p>未填写的可选字段保持省略；保存和发布仍由服务端核验契约。</p>
    </template>
    <template v-else>
      <p v-if="known && !supported">
        当前配置包含表单无法无损编辑的字段或结构，请使用 JSON；切换不会改写配置。
      </p>
      <JsonConfigEditor
        :model-value="modelValue"
        :label="label"
        :readonly="readonly"
        :rows="rows"
        @update:model-value="$emit('update:modelValue', $event)"
      />
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElButton, ElInput } from 'element-plus'
import JsonConfigEditor from './JsonConfigEditor.vue'
const props = withDefaults(
  defineProps<{
    modelValue: string
    evaluatorKey?: string | null
    label: string
    readonly?: boolean
    rows?: number
    schemaVersion?: number | null
  }>(),
  { readonly: false, rows: 7, evaluatorKey: null, schemaVersion: 1 },
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const jsonMode = ref(false)
const fields: Record<string, string[]> = {
  'artifact-contract': ['minCount', 'maxCount', 'schemaVersion', 'requiredTypes'],
  'text-assertion': ['assertions'],
  'document-change-validator': ['requiredArtifactType', 'targetContentSha256'],
}
const known = computed(() => !!fields[props.evaluatorKey || ''])
const config = computed<Record<string, unknown>>(() => {
  try {
    const value: unknown = JSON.parse(props.modelValue || '{}')
    return value !== null && typeof value === 'object' && !Array.isArray(value)
      ? (value as Record<string, unknown>)
      : {}
  } catch {
    return {}
  }
})
const assertions = computed(
  () => (config.value.assertions || []) as { field: string; operator: string; value: string }[],
)
const types = computed(() => (config.value.requiredTypes as string[] | undefined)?.join('\n') || '')
const countFields = [
  { key: 'minCount', label: '最少产物数', min: 0, max: 100, placeholder: '默认 0' },
  { key: 'maxCount', label: '最多产物数', min: 0, max: 100, placeholder: '默认 100' },
  {
    key: 'schemaVersion',
    label: '产物 schema 版本',
    min: 1,
    max: undefined,
    placeholder: '默认 1',
  },
]
const supported = computed(() => {
  if (!known.value || props.schemaVersion !== 1) return false
  try {
    const value = JSON.parse(props.modelValue || '{}')
    if (
      !value ||
      Array.isArray(value) ||
      typeof value !== 'object' ||
      Object.keys(value).some((key) => !fields[props.evaluatorKey!]!.includes(key))
    )
      return false
    if (props.evaluatorKey === 'artifact-contract')
      return (
        countFields.every(
          (field) => !(field.key in value) || Number.isSafeInteger(value[field.key]),
        ) &&
        (!('requiredTypes' in value) ||
          (Array.isArray(value.requiredTypes) &&
            value.requiredTypes.every((item: unknown) => typeof item === 'string')))
      )
    if (props.evaluatorKey === 'text-assertion')
      return (
        !('assertions' in value) ||
        (Array.isArray(value.assertions) &&
          value.assertions.every(
            (item: Record<string, unknown>) =>
              item &&
              !Array.isArray(item) &&
              Object.keys(item).every((key) => ['field', 'operator', 'value'].includes(key)) &&
              item.field === 'resultSummary' &&
              ['CONTAINS', 'NOT_CONTAINS', 'EXACT', 'SHA256'].includes(String(item.operator)) &&
              typeof item.value === 'string',
          ))
      )
    return Object.values(value).every((item) => typeof item === 'string')
  } catch {
    return false
  }
})
function setField(key: string, value: unknown, omitEmpty = false) {
  if (props.readonly || !supported.value) return
  const next = { ...config.value }
  if (omitEmpty && (value === '' || (Array.isArray(value) && !value.length))) delete next[key]
  else next[key] = value
  emit('update:modelValue', JSON.stringify(next, null, 2))
}
function numberField(key: string, text: string) {
  if (text === '') setField(key, '', true)
  else if (Number.isSafeInteger(Number(text))) setField(key, Number(text))
}
function updateAssertion(index: number, key: string, value: string) {
  setField(
    'assertions',
    assertions.value.map((item, position) =>
      position === index ? { ...item, [key]: value } : item,
    ),
  )
}
</script>
<style scoped>
.rule-editor,
fieldset,
label {
  display: grid;
  gap: var(--adw-space-2);
  min-width: 0;
}
fieldset {
  border: 0;
  padding: 0;
  gap: var(--adw-space-3);
}
.rule-editor__mode {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.rule-editor__assertion {
  border-left: 2px solid var(--adw-border-color);
  padding-left: var(--adw-space-3);
}
input,
textarea,
select {
  box-sizing: border-box;
  width: 100%;
  padding: var(--adw-space-2);
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
  background: var(--adw-surface);
  color: var(--adw-text-primary);
}
textarea {
  resize: vertical;
}
p {
  margin: 0;
  color: var(--adw-text-secondary);
  line-height: 1.7;
}
</style>
