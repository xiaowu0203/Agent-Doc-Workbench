<template>
  <el-dialog :model-value="true" title="创建 Prompt 离线实验" width="760px" @close="$emit('close')">
    <p>
      Baseline 来自数据集冻结来源；候选仅修改 Prompt。创建后仍需预检和单独确认 Token 预算才会启动。
    </p>
    <el-button :disabled="busy" @click="picker = true">选择已发布数据集版本</el-button>
    <p v-if="source">
      {{ source.name }} · v{{ source.version.versionNo }} · #{{ source.version.id }}
    </p>
    <p v-if="source">内容 hash：{{ source.version.contentHash }}</p>
    <fieldset v-for="(candidate, index) in candidates" :key="candidate.rowId" :disabled="busy">
      <legend>Prompt 候选 {{ index + 1 }}</legend>
      <label
        >候选标识<input
          v-model="candidate.variantKey"
          :aria-label="`候选标识 ${index + 1}`"
          maxlength="64"
          placeholder="例如 prompt-v2"
      /></label>
      <label
        >候选 Prompt<textarea
          v-model="candidate.agentPrompt"
          :aria-label="`候选 Prompt ${index + 1}`"
          rows="6"
          placeholder="填写本次候选使用的完整 Agent Prompt"
        />
      </label>
      <el-button v-if="candidates.length > 1" @click="candidates.splice(index, 1)"
        >移除候选 {{ index + 1 }}</el-button
      >
    </fieldset>
    <el-button :disabled="busy || candidates.length >= MAX_PROMPT_CANDIDATES" @click="addCandidate"
      >添加候选</el-button
    >
    <p>Prompt 仅在此表单输入，不在实验身份、日志或报告中展示；请勿填写凭证。</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <template #footer
      ><el-button :disabled="busy" @click="$emit('close')">取消</el-button
      ><el-button type="primary" :loading="busy" :disabled="!valid || !allowed" @click="create"
        >创建实验</el-button
      ></template
    >
    <PublishedVersionPicker
      v-if="picker"
      :open="true"
      :space-id="spaceId"
      kind="datasets"
      :exclude-ids="[]"
      @close="picker = false"
      @select="select"
    />
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElButton, ElDialog, ElMessageBox } from 'element-plus'
import { normalizeApiError } from '@/api/errors'
import * as api from '../api/evaluation-api'
import PublishedVersionPicker from './PublishedVersionPicker.vue'
import { MAX_PROMPT_CANDIDATES } from '../experiment'
import type {
  DatasetVersion,
  TestCaseVersion,
  EvaluatorVersion,
  Experiment,
  ExperimentCreateRequest,
} from '../types'
const props = defineProps<{ spaceId: string; allowed: boolean }>()
const emit = defineEmits<{ close: []; created: [value: Experiment] }>()
const source = ref<{ version: DatasetVersion; name: string }>()
const picker = ref(false),
  busy = ref(false),
  error = ref('')
let nextRow = 0,
  request: AbortController | undefined,
  generation = 0
const candidates = ref([{ rowId: nextRow++, variantKey: 'prompt-v2', agentPrompt: '' }])
const valid = computed(
  () =>
    !!source.value &&
    candidates.value.every(
      (item) =>
        item.variantKey.trim() &&
        item.variantKey.toLowerCase() !== 'baseline' &&
        item.variantKey.length <= 64 &&
        item.agentPrompt.trim(),
    ) &&
    new Set(candidates.value.map((item) => item.variantKey)).size === candidates.value.length,
)
// 同一表单内容保留幂等键，编辑后生成新键；不持久化 Prompt 或凭证。
const fingerprint = computed(() =>
  JSON.stringify([
    props.spaceId,
    source.value?.version.id,
    candidates.value.map(({ variantKey, agentPrompt }) => ({ variantKey, agentPrompt })),
  ]),
)
let requestKey = window.crypto.randomUUID()
watch(
  fingerprint,
  () => {
    requestKey = window.crypto.randomUUID()
  },
  { flush: 'sync' },
)
function addCandidate() {
  candidates.value.push({
    rowId: nextRow++,
    variantKey: `prompt-v${candidates.value.length + 2}`,
    agentPrompt: '',
  })
}
function select(version: DatasetVersion | TestCaseVersion | EvaluatorVersion, name: string) {
  if (
    !('datasetId' in version) ||
    String(version.spaceId) !== props.spaceId ||
    version.status !== 'PUBLISHED' ||
    !version.contentHash ||
    busy.value
  )
    return
  source.value = { version, name }
  picker.value = false
}
async function create() {
  if (!valid.value || !props.allowed || busy.value || !source.value) return
  const currentGeneration = generation,
    identity = fingerprint.value
  const payload: ExperimentCreateRequest = {
    spaceId: props.spaceId,
    datasetVersionId: source.value.version.id,
    clientRequestKey: requestKey,
    candidateVariants: candidates.value.map(({ variantKey, agentPrompt }) => ({
      variantKey,
      agentPrompt,
    })),
  }
  busy.value = true
  error.value = ''
  request?.abort()
  const pending = new AbortController()
  request = pending
  try {
    const version = await api.getDatasetVersion(payload.datasetVersionId, pending.signal)
    if (pending.signal.aborted) return
    if (
      String(version.spaceId) !== props.spaceId ||
      version.status !== 'PUBLISHED' ||
      !version.contentHash ||
      !version.datasetId
    )
      throw new Error('数据集版本已不可用于新实验，请重新选择')
    const parent = await api.getDataset(version.datasetId, pending.signal)
    if (pending.signal.aborted) return
    if (
      String(parent.spaceId) !== props.spaceId ||
      String(parent.id) !== String(version.datasetId) ||
      parent.archived
    )
      throw new Error('数据集已归档或归属不一致，请重新选择')
    try {
      await ElMessageBox.confirm(
        '创建不可变候选与实验，不修改生产 Agent。模型运行将在预检与预算确认后另行启动。',
        '创建离线实验',
        { confirmButtonText: '创建实验', cancelButtonText: '返回编辑' },
      )
    } catch {
      return
    }
    if (
      pending.signal.aborted ||
      currentGeneration !== generation ||
      identity !== fingerprint.value ||
      !props.allowed
    )
      return
    const value = await api.createExperiment(payload, pending.signal)
    if (pending.signal.aborted) return
    if (
      String(value.spaceId) !== props.spaceId ||
      String(value.datasetVersionId) !== String(payload.datasetVersionId)
    )
      throw new Error('实验身份或空间归属不一致')
    emit('created', value)
  } catch (e) {
    if (!pending.signal.aborted)
      error.value = `${normalizeApiError(e).message}。相同表单再次提交会复用本次幂等键。`
  } finally {
    if (!pending.signal.aborted) busy.value = false
  }
}
watch(
  () => [props.spaceId, props.allowed],
  () => {
    generation++
    request?.abort()
    busy.value = false
    source.value = undefined
    candidates.value = [{ rowId: nextRow++, variantKey: 'prompt-v2', agentPrompt: '' }]
    picker.value = false
    error.value = ''
  },
)
onBeforeUnmount(() => {
  generation++
  request?.abort()
})
</script>
<style scoped>
fieldset {
  margin: var(--adw-space-4) 0;
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-md);
  padding: var(--adw-space-4);
}
label {
  display: grid;
  gap: var(--adw-space-2);
  margin-bottom: var(--adw-space-3);
}
input,
textarea {
  width: 100%;
  padding: var(--adw-space-2);
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
  background: var(--adw-bg-surface);
  color: var(--adw-text-primary);
}
p {
  overflow-wrap: anywhere;
}
[role='alert'] {
  color: var(--adw-color-danger);
}
input:focus-visible,
textarea:focus-visible {
  outline: 2px solid var(--adw-color-primary);
}
</style>
