<template>
  <el-dialog
    :model-value="true"
    title="创建评估运行"
    width="min(640px, 94vw)"
    top="5vh"
    body-class="evaluation-create-body"
    :close-on-click-modal="false"
    @close="$emit('close')"
  >
    <form class="run-create" @submit.prevent="create">
      <label
        >运行名称（可选）
        <el-input
          v-model="name"
          aria-label="运行名称（可选）"
          :maxlength="MAX_DISPLAY_NAME_LENGTH"
          :disabled="busy"
          placeholder="留空时显示来源名称"
        />
      </label>
      <label
        >评估来源
        <el-select v-model="kind" aria-label="评估来源" :disabled="busy" @change="clearSource">
          <el-option label="数据集版本" value="datasets" />
          <el-option label="单个测试用例版本" value="test-cases" />
        </el-select>
      </label>
      <el-button :disabled="busy" @click="pickerOpen = true">选择已发布版本</el-button>
      <p v-if="loading && !selected" role="status">正在核验带入的来源版本……</p>
      <p v-if="sourceError && !selected" role="alert">{{ sourceError }}。请重新选择来源版本。</p>
      <DataState v-if="selected" :loading="loading" :error="sourceError" @retry="checkSource">
        <div class="run-create__source">
          <strong>{{ sourceName }} · v{{ selected.versionNo }}</strong>
          <p>计划 {{ caseCount }} 个用例；只对数据集中启用的用例执行。</p>
          <details>
            <summary>冻结版本身份</summary>
            <p>版本 #{{ selected.id }}</p>
            <p>内容 hash：{{ selected.contentHash }}</p>
          </details>
        </div>
      </DataState>
      <label
        >执行授权有效期（秒）
        <el-input
          v-model.number="ttl"
          type="number"
          aria-label="执行授权有效期（秒）"
          :min="WORKER_TTL_MIN"
          :max="WORKER_TTL_MAX"
          step="1"
          :disabled="busy"
        />
      </label>
      <p>有效期为 300～86400 秒，默认 3600 秒。过期可能暂停运行，恢复时需重新授权。</p>
      <p>运行将创建隔离回放 Task，调用模型与工具并消耗 Token；候选产物不会写入正式文档。</p>
      <p v-if="error" role="alert">{{ error }}</p>
      <el-button type="primary" native-type="submit" :loading="busy" :disabled="!ready || !canRun"
        >确认创建运行</el-button
      >
    </form>
    <PublishedVersionPicker
      :open="pickerOpen"
      :space-id="spaceId"
      :kind="kind"
      :exclude-ids="[]"
      @close="pickerOpen = false"
      @select="selectSource"
    />
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElButton, ElDialog, ElInput, ElMessageBox, ElOption, ElSelect } from 'element-plus'
import DataState from '@/shared/components/DataState.vue'
import { normalizeApiError } from '@/api/errors'
import PublishedVersionPicker from './PublishedVersionPicker.vue'
import * as api from '../api/evaluation-api'
import type { DatasetVersion, TestCaseVersion, EvaluatorVersion, EvaluationRun } from '../types'
import { WORKER_TTL_DEFAULT, WORKER_TTL_MIN, WORKER_TTL_MAX, validWorkerTtl } from '../run'
import { MAX_DISPLAY_NAME_LENGTH } from '../display'
const props = defineProps<{
  spaceId: string
  canRun: boolean
  initialKind?: string
  initialVersionId?: string
}>()
const emit = defineEmits<{ close: []; created: [run: EvaluationRun] }>()
const kind = ref<'datasets' | 'test-cases'>('datasets'),
  pickerOpen = ref(false)
const selected = ref<DatasetVersion | TestCaseVersion | null>(null),
  sourceName = ref('')
const name = ref('')
const ttl = ref(WORKER_TTL_DEFAULT),
  caseCount = ref<number | null>(null)
const loading = ref(false),
  busy = ref(false),
  sourceError = ref(''),
  error = ref('')
let request: AbortController | undefined
const ready = computed(
  () =>
    !!selected.value &&
    !loading.value &&
    !sourceError.value &&
    caseCount.value !== null &&
    caseCount.value > 0 &&
    validWorkerTtl(ttl.value),
)
function clearSource() {
  request?.abort()
  selected.value = null
  caseCount.value = null
  sourceError.value = ''
  loading.value = false
}
function selectSource(value: DatasetVersion | TestCaseVersion | EvaluatorVersion, name: string) {
  if (
    'evaluatorId' in value ||
    String(value.spaceId) !== props.spaceId ||
    'datasetId' in value !== (kind.value === 'datasets')
  )
    return
  selected.value = value
  sourceName.value = name
  void checkSource()
}
async function verifySource(signal: AbortSignal, loadedVersion?: DatasetVersion | TestCaseVersion) {
  const selection = selected.value
  if (!selection) throw new Error('请先选择已发布版本')
  const version =
    loadedVersion ??
    ('datasetId' in selection
      ? await api.getDatasetVersion(selection.id, signal)
      : await api.getTestCaseVersion(selection.id, signal))
  if (signal.aborted) return
  if (
    String(version.id) !== String(selection.id) ||
    String(version.spaceId) !== props.spaceId ||
    version.status !== 'PUBLISHED' ||
    !version.contentHash
  )
    throw new Error('来源版本已变化，请重新选择')
  const parent =
    'datasetId' in version
      ? await api.getDataset(version.datasetId!, signal)
      : await api.getTestCase(version.testCaseId!, signal)
  if (signal.aborted) return
  if (
    String(parent.spaceId) !== props.spaceId ||
    parent.archived ||
    String(parent.id) !== String('datasetId' in version ? version.datasetId : version.testCaseId)
  )
    throw new Error('来源资源已归档或归属不一致')
  const count =
    'datasetId' in version
      ? (await api.getDatasetCases(version.id, signal)).filter((item) => item.enabled).length
      : 1
  if (signal.aborted) return
  if (!count) throw new Error('数据集没有启用的用例，请选择另一版本')
  sourceName.value = parent.name
  caseCount.value = count
}
watch(
  () => [props.initialVersionId, props.initialKind],
  async () => {
    if (
      !props.canRun ||
      !props.initialVersionId ||
      !/^[1-9]\d{0,18}$/.test(props.initialVersionId) ||
      !['datasets', 'test-cases'].includes(props.initialKind || '')
    )
      return
    request?.abort()
    const pending = new AbortController()
    request = pending
    kind.value = props.initialKind as 'datasets' | 'test-cases'
    loading.value = true
    sourceError.value = ''
    try {
      const version =
        kind.value === 'datasets'
          ? await api.getDatasetVersion(props.initialVersionId, pending.signal)
          : await api.getTestCaseVersion(props.initialVersionId, pending.signal)
      if (pending.signal.aborted) return
      if (String(version.id) !== props.initialVersionId) throw new Error('带入的来源版本身份不一致')
      selected.value = version
      await verifySource(pending.signal, version)
    } catch (failure) {
      if (!pending.signal.aborted) sourceError.value = normalizeApiError(failure).message
    } finally {
      if (!pending.signal.aborted) loading.value = false
    }
  },
  { immediate: true },
)
async function checkSource() {
  request?.abort()
  const pending = new AbortController()
  request = pending
  loading.value = true
  sourceError.value = ''
  caseCount.value = null
  try {
    await verifySource(pending.signal)
  } catch (e) {
    if (!pending.signal.aborted) sourceError.value = normalizeApiError(e).message
  } finally {
    if (!pending.signal.aborted) loading.value = false
  }
}
async function create() {
  if (!ready.value || !props.canRun || busy.value) return
  const identity = `${props.spaceId}/${kind.value}/${selected.value!.id}/${ttl.value}/${name.value.trim()}`
  const count = caseCount.value
  try {
    await ElMessageBox.confirm(
      `将创建 ${count} 个隔离回放用例，调用模型与工具并消耗 Token。授权有效期 ${ttl.value} 秒。`,
      '确认评估运行',
      { confirmButtonText: '创建运行', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  if (
    !props.canRun ||
    !ready.value ||
    identity !==
      `${props.spaceId}/${kind.value}/${selected.value?.id}/${ttl.value}/${name.value.trim()}`
  )
    return
  request?.abort()
  const pending = new AbortController()
  request = pending
  busy.value = true
  error.value = ''
  try {
    await verifySource(pending.signal)
    if (pending.signal.aborted || !props.canRun) return
    if (caseCount.value !== count) throw new Error('计划用例数已变化，请重新核对')
    const value = await api.createEvaluationRun(
      {
        spaceId: props.spaceId,
        ...(kind.value === 'datasets'
          ? { datasetVersionId: selected.value!.id }
          : { singleTestCaseVersionId: selected.value!.id }),
        workerCapabilityTtlSeconds: ttl.value,
        ...(name.value.trim() ? { name: name.value.trim() } : {}),
      },
      pending.signal,
    )
    if (pending.signal.aborted) return
    if (String(value.spaceId) !== props.spaceId) throw new Error('运行空间归属不一致')
    emit('created', value)
  } catch (e) {
    if (!pending.signal.aborted)
      error.value = `${normalizeApiError(e).message}。如请求已发送，请先刷新运行列表确认是否创建成功，再决定重试。`
  } finally {
    if (!pending.signal.aborted) busy.value = false
  }
}
watch(
  () => [props.spaceId, props.canRun],
  () => {
    request?.abort()
    busy.value = false
    clearSource()
  },
)
onBeforeUnmount(() => request?.abort())
</script>
<style scoped>
.run-create {
  display: grid;
  gap: var(--adw-space-4);
}
label {
  display: grid;
  gap: var(--adw-space-2);
}
input,
select {
  padding: 10px;
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
  color: var(--adw-text-primary);
  background: var(--adw-surface);
}
p {
  margin: 0;
  line-height: 1.7;
  color: var(--adw-text-secondary);
  overflow-wrap: anywhere;
}
.run-create__source {
  padding: var(--adw-space-4);
  background: var(--adw-color-primary-soft);
  border-left: 3px solid var(--adw-color-primary);
}
[role='alert'] {
  color: var(--adw-color-danger);
}
</style>
<style>
.evaluation-create-body {
  max-height: 72vh;
  overflow-y: auto;
}
</style>
