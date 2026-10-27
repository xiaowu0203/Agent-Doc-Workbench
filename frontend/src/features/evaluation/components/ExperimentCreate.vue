<template>
  <el-dialog
    :model-value="true"
    title="创建 Prompt 离线实验"
    width="min(760px, 94vw)"
    top="5vh"
    body-class="evaluation-create-body"
    :close-on-click-modal="false"
    @close="$emit('close')"
  >
    <p>
      Baseline 来自数据集冻结来源；候选仅修改 Prompt。创建后仍需预检和单独确认 Token 预算才会启动。
    </p>
    <el-button :disabled="busy" @click="picker = true">选择已发布数据集版本</el-button>
    <label class="experiment-create-name"
      >实验名称（可选）
      <el-input
        v-model="name"
        aria-label="实验名称（可选）"
        :maxlength="MAX_DISPLAY_NAME_LENGTH"
        :disabled="busy"
        placeholder="留空时显示数据集名称"
      />
    </label>
    <p v-if="source">
      {{ source.name }} · v{{ source.version.versionNo }} · #{{ source.version.id }}
    </p>
    <p v-if="source">内容 hash：{{ source.version.contentHash }}</p>
    <p v-if="sourceLoading" role="status">正在核验数据集及启用用例……</p>
    <p v-if="sourceError" role="alert">{{ sourceError }}</p>
    <p v-if="caseCount !== null">
      计划每组 {{ caseCount }} 个启用用例，共
      {{ caseCount * (candidates.length + 1) }}
      次执行。来源配置兼容性由创建后的统一预检核验；预检通过前不能启动。
    </p>
    <section v-if="baseline" aria-label="创建前冻结基线核对">
      <h3>冻结来源配置核对</h3>
      <p v-if="!baseline.sourceConsistent" role="alert">
        {{ baseline.issues.map(sourceIssue).join('；') }}。请调整数据集版本。
      </p>
      <template v-else>
        <p>
          启用用例的执行配置快照一致；代表来源已核验。其他来源的当前可回放性和预算仍由创建及启动预检重新核验。
        </p>
        <dl>
          <dt>冻结 Agent / 代表任务 / 执行</dt>
          <dd>
            #{{ baseline.agentId }} / #{{ baseline.sourceTaskId }} / #{{
              baseline.sourceExecutionId
            }}
          </dd>
          <dt>执行快照</dt>
          <dd>
            schema {{ baseline.executionSnapshotSchemaVersion }} ·
            {{ baseline.executionSnapshotHash }}
          </dd>
          <dt>代表任务冻结 Token 预算</dt>
          <dd>{{ baseline.tokenBudget ?? '未提供' }}（不代表整个实验授权）</dd>
          <dt>当前操作授权</dt>
          <dd>当前具备创建实验所需的评估管理与 Agent 管理权限；启动仍须执行权限和明确预算确认。</dd>
        </dl>
        <p v-if="baselineAuditError" role="alert">
          冻结配置详情暂不可用：{{ baselineAuditError }}
          <el-button link @click="inspectSource(source?.version)">重试冻结配置读取</el-button>
        </p>
        <details v-if="baselineAudit">
          <summary>查看冻结模型、工具与能力范围</summary>
          <p>仅展示原执行快照，不读取当前 Agent 配置或凭证。</p>
          <dl>
            <dt>模型 / 配置版本</dt>
            <dd>
              {{ baselineAudit.model.displayName || baselineAudit.model.modelKey || '未提供' }} /
              {{ baselineAudit.model.configVersion ?? '未提供' }}
            </dd>
            <dt>迭代上限 / 超时</dt>
            <dd>
              {{ baselineAudit.maxIterations ?? '未提供' }} 次 /
              {{ baselineAudit.executionTimeoutSeconds ?? '未提供' }} 秒
            </dd>
            <dt>Prompt hash</dt>
            <dd>{{ baselineAudit.promptHash || '未提供' }}</dd>
            <dt>工具白名单</dt>
            <dd>
              {{
                baselineAudit.toolDefinitions.map((item) => item.name).join('、') || '未绑定工具'
              }}
            </dd>
            <dt>绑定 Skill 版本</dt>
            <dd>
              {{
                baselineAudit.skill.boundSkills
                  .map((item) => `${item.name} v${item.versionNo}`)
                  .join('、') || '未绑定 Skill'
              }}
            </dd>
            <dt>实际选中 Skill 版本</dt>
            <dd>{{ baselineAudit.skill.selectedSkillVersionIds.join('、') || '未选中 Skill' }}</dd>
            <dt>外部 MCP</dt>
            <dd>
              {{
                baselineAudit.externalMcps
                  .map((item) => `${item.serverKey} v${item.configVersion}`)
                  .join('、') || '未绑定外部 MCP'
              }}
            </dd>
          </dl>
        </details>
      </template>
    </section>
    <fieldset v-for="(candidate, index) in candidates" :key="candidate.rowId" :disabled="busy">
      <legend>Prompt 候选 {{ index + 1 }}</legend>
      <label
        >候选标识<el-input
          v-model="candidate.variantKey"
          :aria-label="`候选标识 ${index + 1}`"
          maxlength="64"
          placeholder="例如 prompt-v2"
      /></label>
      <label
        >候选 Prompt<el-input
          type="textarea"
          v-model="candidate.agentPrompt"
          :aria-label="`候选 Prompt ${index + 1}`"
          :rows="6"
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
import { ElButton, ElDialog, ElInput, ElMessageBox } from 'element-plus'
import { normalizeApiError } from '@/api/errors'
import { getTaskExecutionDetail } from '@/features/task/api/task-api'
import type { AgentExecutionAudit } from '@/features/task/types'
import * as api from '../api/evaluation-api'
import PublishedVersionPicker from './PublishedVersionPicker.vue'
import { MAX_PROMPT_CANDIDATES } from '../experiment'
import { MAX_DISPLAY_NAME_LENGTH } from '../display'
import type {
  DatasetVersion,
  DatasetExperimentSource,
  TestCaseVersion,
  EvaluatorVersion,
  Experiment,
  ExperimentCreateRequest,
} from '../types'
const props = defineProps<{ spaceId: string; allowed: boolean; initialVersionId?: string }>()
const emit = defineEmits<{ close: []; created: [value: Experiment] }>()
const source = ref<{ version: DatasetVersion; name: string }>()
const name = ref('')
const baseline = ref<DatasetExperimentSource | null>(null)
const baselineAudit = ref<AgentExecutionAudit | null>(null),
  baselineAuditError = ref('')
const sourceIssues: Record<string, string> = {
  CASE_UNAVAILABLE: '用例缺失、归档、未发布或归属不一致',
  SOURCE_IDENTITY_MISSING: '来源冻结身份不完整或协议不受支持',
  SOURCE_SNAPSHOT_MISMATCH: '用例使用了不同的执行配置快照',
  BASELINE_SOURCE_UNAVAILABLE: '代表来源不可回放或与冻结身份不一致',
}
function sourceIssue(code: string) {
  return sourceIssues[code] || code
}
const picker = ref(false),
  busy = ref(false),
  error = ref('')
const sourceLoading = ref(false),
  sourceError = ref(''),
  caseCount = ref<number | null>(null)
let sourceRead: AbortController | undefined
let nextRow = 0,
  request: AbortController | undefined,
  generation = 0
const candidates = ref([{ rowId: nextRow++, variantKey: 'prompt-v2', agentPrompt: '' }])
const valid = computed(
  () =>
    !!source.value &&
    !sourceLoading.value &&
    !sourceError.value &&
    baseline.value?.sourceConsistent === true &&
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
    name.value.trim(),
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
  void inspectSource(version)
}
async function inspectSource(loaded?: DatasetVersion) {
  sourceRead?.abort()
  const pending = new AbortController()
  sourceRead = pending
  baseline.value = null
  baselineAudit.value = null
  baselineAuditError.value = ''
  sourceLoading.value = true
  sourceError.value = ''
  caseCount.value = null
  try {
    const version = loaded ?? (await api.getDatasetVersion(props.initialVersionId!, pending.signal))
    if (pending.signal.aborted) return
    if (
      !('datasetId' in version) ||
      (!loaded && String(version.id) !== props.initialVersionId) ||
      String(version.spaceId) !== props.spaceId ||
      version.status !== 'PUBLISHED' ||
      !version.contentHash ||
      !version.datasetId
    )
      throw new Error('数据集版本已不可用于新实验，请重新选择')
    const parent = await api.getDataset(version.datasetId, pending.signal)
    if (pending.signal.aborted) return
    if (
      String(parent.id) !== String(version.datasetId) ||
      String(parent.spaceId) !== props.spaceId ||
      parent.archived
    )
      throw new Error('数据集已归档或归属不一致')
    const cases = await api.getDatasetCases(version.id, pending.signal)
    if (pending.signal.aborted) return
    caseCount.value = cases.filter((item) => item.enabled).length
    if (!caseCount.value) throw new Error('数据集没有启用用例，请选择其他版本')
    const preview = await api.getDatasetExperimentSource(version.id, pending.signal)
    if (pending.signal.aborted) return
    if (
      String(preview.spaceId) !== props.spaceId ||
      String(preview.datasetVersionId) !== String(version.id) ||
      preview.enabledCaseCount !== caseCount.value ||
      typeof preview.sourceConsistent !== 'boolean' ||
      !Array.isArray(preview.issues) ||
      preview.issues.some((item) => typeof item !== 'string') ||
      (preview.sourceConsistent &&
        (preview.issues.length > 0 ||
          ![preview.sourceTaskId, preview.sourceExecutionId, preview.agentId].every((id) =>
            /^[1-9]\d{0,18}$/.test(String(id)),
          ) ||
          preview.executionSnapshotSchemaVersion !== 3 ||
          !/^[0-9a-f]{64}$/.test(preview.executionSnapshotHash || '')))
    )
      throw new Error('冻结来源预览身份或数量不一致')
    baseline.value = preview
    if (preview.sourceConsistent) {
      try {
        const detail = await getTaskExecutionDetail(preview.sourceTaskId!, pending.signal)
        if (pending.signal.aborted) return
        const execution = detail.execution
        if (
          String(detail.task.spaceId) !== props.spaceId ||
          String(detail.task.id) !== String(preview.sourceTaskId) ||
          String(detail.task.agentId) !== String(preview.agentId) ||
          !execution ||
          String(execution.id) !== String(preview.sourceExecutionId) ||
          execution.executionSnapshotHash !== preview.executionSnapshotHash ||
          execution.executionSnapshotSchemaVersion !== preview.executionSnapshotSchemaVersion
        )
          throw new Error('冻结执行配置与来源预览不一致')
        baselineAudit.value = execution
      } catch (failure) {
        if (!pending.signal.aborted) baselineAuditError.value = normalizeApiError(failure).message
      }
    }
    source.value = { version, name: parent.name }
  } catch (failure) {
    if (!pending.signal.aborted) sourceError.value = normalizeApiError(failure).message
  } finally {
    if (!pending.signal.aborted) sourceLoading.value = false
  }
}
watch(
  () => props.initialVersionId,
  (id) => {
    if (props.allowed && id && /^[1-9]\d{0,18}$/.test(id)) void inspectSource()
  },
  { immediate: true },
)
async function create() {
  if (!valid.value || !props.allowed || busy.value || !source.value) return
  const currentGeneration = generation,
    identity = fingerprint.value
  const payload: ExperimentCreateRequest = {
    spaceId: props.spaceId,
    datasetVersionId: source.value.version.id,
    clientRequestKey: requestKey,
    ...(name.value.trim() ? { name: name.value.trim() } : {}),
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
    sourceRead?.abort()
    sourceLoading.value = false
    sourceError.value = ''
    caseCount.value = null
    baseline.value = null
    baselineAudit.value = null
    baselineAuditError.value = ''
    request?.abort()
    busy.value = false
    source.value = undefined
    candidates.value = [{ rowId: nextRow++, variantKey: 'prompt-v2', agentPrompt: '' }]
    picker.value = false
    error.value = ''
  },
)
onBeforeUnmount(() => {
  sourceRead?.abort()
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
<style>
.evaluation-create-body {
  max-height: 72vh;
  overflow-y: auto;
}
</style>
