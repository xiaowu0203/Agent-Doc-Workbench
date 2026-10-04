<template>
  <section class="catalog-bindings" aria-label="版本绑定">
    <div class="catalog-bindings__heading">
      <h3>{{ kind === 'datasets' ? '测试用例绑定与顺序' : '评估器绑定与预期' }}</h3>
      <el-button v-if="editable" @click="pickerOpen = true">{{
        kind === 'datasets' ? '添加测试用例版本' : '添加评估器版本'
      }}</el-button>
    </div>
    <p>
      {{
        kind === 'datasets'
          ? '保存将全量替换绑定。不同来源配置可能影响实验准入，实验创建时还会统一检查。'
          : '绑定级预期为空时使用用例预期。保存将全量替换绑定，发布后冻结。'
      }}
    </p>
    <DataState :loading="loading" :error="error" @retry="load">
      <p v-if="kind === 'datasets'">
        共 {{ rows.length }} 个用例版本，启用
        {{ rows.filter((row) => row.enabled).length }} 个。发布数据集不代表已通过离线实验的同
        Agent、同来源配置检查。
      </p>
      <p v-else>共 {{ rows.length }} 个评估器版本。</p>
      <div v-for="(row, index) in rows" :key="row.versionId" class="catalog-bindings__row">
        <div class="catalog-bindings__title">
          <strong>{{ index + 1 }}. {{ row.name || '名称不可用' }} · v{{ row.versionNo }}</strong
          ><EvaluationStatusTag domain="version" :status="row.status" />
        </div>
        <RouterLink
          v-if="row.parentId"
          :to="`/spaces/${spaceId}/evaluation/${kind === 'datasets' ? 'test-cases' : 'evaluators'}/${row.parentId}?versionId=${row.versionId}`"
          >查看版本 #{{ row.versionId }}</RouterLink
        >
        <span v-else>版本 #{{ row.versionId }}</span>
        <p v-if="row.evaluatorKey">{{ row.evaluatorKey }}</p>
        <JsonConfigEditor
          v-if="kind === 'test-cases'"
          v-model="row.expectedJson"
          label="绑定级 expectedJson（留空使用用例预期）"
          :readonly="!editable"
          :rows="3"
          @update:model-value="markDirty"
        />
        <label v-else
          ><input v-model="row.enabled" type="checkbox" :disabled="!editable" @change="markDirty" />
          启用于新运行</label
        >
        <div v-if="editable" class="catalog-bindings__actions">
          <el-button :disabled="index === 0" @click="move(index, -1)">上移</el-button
          ><el-button :disabled="index === rows.length - 1" @click="move(index, 1)">下移</el-button
          ><el-button type="danger" plain @click="remove(index)">移除绑定</el-button>
        </div>
      </div>
      <p v-if="!rows.length">
        尚未绑定版本。{{
          editable ? '请添加已发布且未归档的同空间版本。' : '此历史版本没有绑定记录。'
        }}
      </p>
      <el-button v-if="editable" type="primary" :loading="saving" :disabled="!dirty" @click="save"
        >保存全部绑定</el-button
      >
      <p v-if="saveError" role="alert">{{ saveError }}</p>
    </DataState>
    <PublishedVersionPicker
      :open="pickerOpen"
      :space-id="spaceId"
      :kind="kind === 'datasets' ? 'test-cases' : 'evaluators'"
      :exclude-ids="rows.map((row) => row.versionId)"
      @close="pickerOpen = false"
      @select="add"
    />
  </section>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { ElButton, ElMessage } from 'element-plus'
import DataState from '@/shared/components/DataState.vue'
import { normalizeApiError } from '@/api/errors'
import {
  getDatasetCases,
  getTestCaseEvaluators,
  replaceDatasetCases,
  replaceTestCaseEvaluators,
} from '../api/evaluation-api'
import type { DatasetVersion, EvaluatorVersion, TestCaseVersion } from '../types'
import EvaluationStatusTag from './EvaluationStatusTag.vue'
import JsonConfigEditor from './JsonConfigEditor.vue'
import PublishedVersionPicker from './PublishedVersionPicker.vue'
import { formatJson } from '../catalog'
const props = defineProps<{
  kind: 'datasets' | 'test-cases'
  versionId: string
  spaceId: string
  editable: boolean
}>()
const emit = defineEmits<{ dirty: [value: boolean] }>()
interface BindingRow {
  versionId: string
  parentId: string | null
  name: string | null
  versionNo: number
  status: string
  evaluatorKey?: string | null
  expectedJson: string
  enabled: boolean
}
const rows = ref<BindingRow[]>([]),
  loading = ref(false),
  saving = ref(false),
  error = ref(''),
  saveError = ref(''),
  pickerOpen = ref(false),
  dirty = ref(false)
let request: AbortController | undefined, mutationRequest: AbortController | undefined
function markDirty() {
  dirty.value = true
  emit('dirty', true)
}
function move(index: number, direction: number) {
  const row = rows.value.splice(index, 1)[0]!
  rows.value.splice(index + direction, 0, row)
  markDirty()
}
function remove(index: number) {
  rows.value.splice(index, 1)
  markDirty()
}
function add(version: EvaluatorVersion | TestCaseVersion | DatasetVersion, name: string) {
  if ('datasetId' in version) return
  if (!props.editable || rows.value.some((row) => row.versionId === String(version.id))) return
  rows.value.push({
    versionId: String(version.id),
    parentId: String('evaluatorId' in version ? version.evaluatorId : version.testCaseId),
    name,
    versionNo: version.versionNo,
    status: version.status,
    evaluatorKey: 'evaluatorKey' in version ? version.evaluatorKey : null,
    expectedJson: '',
    enabled: true,
  })
  markDirty()
}
async function load() {
  request?.abort()
  const current = new AbortController()
  request = current
  loading.value = true
  error.value = ''
  saveError.value = ''
  rows.value = []
  dirty.value = false
  emit('dirty', false)
  try {
    if (props.kind === 'datasets') {
      const result = await getDatasetCases(props.versionId, current.signal)
      if (current.signal.aborted) return
      rows.value = result.map((row) => ({
        versionId: String(row.testCaseVersionId),
        parentId: row.testCaseId ? String(row.testCaseId) : null,
        name: row.testCaseName,
        versionNo: row.versionNo,
        status: row.status,
        expectedJson: '',
        enabled: row.enabled,
      }))
    } else {
      const result = await getTestCaseEvaluators(props.versionId, current.signal)
      if (current.signal.aborted) return
      rows.value = result.map((row) => ({
        versionId: String(row.evaluatorVersionId),
        parentId: row.evaluatorId ? String(row.evaluatorId) : null,
        name: row.evaluatorName,
        versionNo: row.versionNo,
        status: row.status,
        evaluatorKey: row.evaluatorKey,
        expectedJson: row.expectedJson || '',
        enabled: true,
      }))
    }
  } catch (e) {
    if (!current.signal.aborted) error.value = normalizeApiError(e).message
  } finally {
    if (!current.signal.aborted) loading.value = false
  }
}
async function save() {
  if (!props.editable || saving.value) return
  saving.value = true
  saveError.value = ''
  const versionId = props.versionId
  const current = new AbortController()
  mutationRequest = current
  try {
    if (props.kind === 'datasets')
      await replaceDatasetCases(
        versionId,
        {
          cases: rows.value.map((row, index) => ({
            testCaseVersionId: row.versionId,
            sortOrder: index,
            enabled: row.enabled,
          })),
        },
        current.signal,
      )
    else
      await replaceTestCaseEvaluators(
        versionId,
        {
          evaluators: rows.value.map((row, index) => ({
            evaluatorVersionId: row.versionId,
            sortOrder: index,
            expectedJson: row.expectedJson.trim() ? formatJson(row.expectedJson) : null,
          })),
        },
        current.signal,
      )
    if (current.signal.aborted || versionId !== props.versionId) return
    ElMessage.success('版本绑定已保存')
    await load()
  } catch (e) {
    if (!current.signal.aborted && versionId === props.versionId)
      saveError.value = normalizeApiError(e).message
  } finally {
    if (!current.signal.aborted) saving.value = false
  }
}
watch(
  () => [props.versionId, props.spaceId, props.kind],
  () => {
    mutationRequest?.abort()
    saving.value = false
    pickerOpen.value = false
    void load()
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  request?.abort()
  mutationRequest?.abort()
})
</script>
<style scoped>
.catalog-bindings {
  display: grid;
  gap: var(--adw-space-3);
  margin-top: var(--adw-space-6);
}
.catalog-bindings__heading,
.catalog-bindings__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--adw-space-3);
}
h3,
p {
  margin: 0;
}
p {
  color: var(--adw-text-secondary);
  line-height: 1.6;
}
.catalog-bindings__row {
  display: grid;
  gap: var(--adw-space-2);
  padding: var(--adw-space-4) 0;
  border-bottom: 1px solid var(--adw-border-color-light);
  overflow-wrap: anywhere;
}
.catalog-bindings__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--adw-space-2);
}
p[role='alert'] {
  color: var(--adw-color-danger);
}
a {
  color: var(--adw-color-primary);
  text-decoration: none;
}
</style>
