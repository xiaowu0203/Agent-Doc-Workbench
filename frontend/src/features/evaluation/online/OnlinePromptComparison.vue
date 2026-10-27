<template>
  <section class="prompt-comparison" aria-label="冻结Prompt对照与保护策略">
    <h3>冻结配置与保护策略</h3>
    <p>非 Prompt 一致证明：{{ consistency }}。模板只读，不读取当前 Agent 配置替代历史内容。</p>
    <dl>
      <div>
        <dt>固定预算 / 单 Task / 超时</dt>
        <dd>
          {{ onlineFact(experiment.budgetPlan?.total) }} /
          {{ onlineFact(experiment.budgetPlan?.perTask) }} Token /
          {{ onlineFact(experiment.budgetPlan?.timeout) }} 秒
        </dd>
      </div>
      <div>
        <dt>健康窗口 / 失败比例上限</dt>
        <dd>
          {{ onlineFact(experiment.protectionPlan?.healthWindowCount) }} 个终态 Task /
          {{ onlineFact(experiment.protectionPlan?.healthFailureBps) }} 万分比；触发时暂停准入
        </dd>
      </div>
      <div>
        <dt>未决保护 / SRM 复核间隔</dt>
        <dd>
          {{ onlineFact(experiment.protectionPlan?.unresolvedSeconds) }} /
          {{ onlineFact(experiment.protectionPlan?.srmRecheckSeconds) }} 秒
        </dd>
      </div>
      <div>
        <dt>SRM 最低独立文档 / 最低期望组数 / 阈值</dt>
        <dd>
          {{ onlineFact(experiment.protectionPlan?.srmMinDocuments) }} /
          {{ onlineFact(experiment.protectionPlan?.srmMinExpectedCount) }} /
          {{ onlineFact(experiment.protectionPlan?.srmThreshold) }}
        </dd>
      </div>
    </dl>
    <el-button v-if="allowed" :loading="loading" @click="load">读取完整冻结 Prompt 对照</el-button>
    <p v-if="allowed">读取会记录知情状态；完整最终系统提示词只在当前授权页面展示。</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <template v-if="value && allowed">
      <div class="prompt-pair">
        <label
          >Baseline 完整系统提示词<el-input type="textarea"
            :model-value="value.baselineSystemPrompt"
            aria-label="冻结基线系统提示词"
            readonly
            :rows="10"
          />
        </label>
        <label
          >Candidate 完整系统提示词<el-input type="textarea"
            :model-value="value.candidateSystemPrompt"
            aria-label="冻结候选系统提示词"
            readonly
            :rows="10"
          />
        </label>
      </div>
      <details>
        <summary>Prompt 行差异（完整正文见上方）</summary>
        <pre
          v-for="hunk in hunks"
          :key="hunk.key"
        ><span v-for="(line, index) in hunk.lines" :key="index" :class="line.kind">{{ line.kind === 'added' ? '+' : line.kind === 'removed' ? '−' : ' ' }} {{ line.text }}{{ '\n' }}</span></pre>
      </details>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElButton, ElInput } from 'element-plus'
import { normalizeApiError } from '@/api/errors'
import { buildDiffHunks } from '@/diff/utils/line-diff'
import { getOnlinePromptComparison } from './api'
import { onlineFact, onlineIdentity } from './presentation'
import type { OnlineExperiment, OnlinePromptComparison } from './types'
const props = defineProps<{ experiment: OnlineExperiment; allowed: boolean }>()
function sameTemplate(left: OnlineExperiment['baseline'], right: OnlineExperiment['baseline']) {
  return (
    !!left &&
    !!right &&
    left.id === right.id &&
    left.schemaVersion === right.schemaVersion &&
    left.hash === right.hash &&
    left.nonPromptHash === right.nonPromptHash
  )
}
const value = ref<OnlinePromptComparison | null>(null),
  loading = ref(false),
  error = ref('')
let controller: AbortController | undefined
const consistency = computed(() => {
  const baseline = props.experiment.baseline,
    candidate = props.experiment.candidate
  return baseline?.schemaVersion === 2 &&
    candidate?.schemaVersion === 2 &&
    /^[0-9a-f]{64}$/.test(baseline.nonPromptHash) &&
    /^[0-9a-f]{64}$/.test(candidate.nonPromptHash)
    ? baseline.nonPromptHash === candidate.nonPromptHash
      ? '两组摘要一致'
      : '两组摘要不一致，不能启动'
    : '未知'
})
const hunks = computed(() =>
  value.value
    ? buildDiffHunks(value.value.baselineSystemPrompt, value.value.candidateSystemPrompt)
    : [],
)
async function load() {
  if (!props.allowed || loading.value) return
  value.value = null
  error.value = ''
  loading.value = true
  const current = new AbortController()
  controller = current
  try {
    const response = await getOnlinePromptComparison(props.experiment.id, current.signal)
    if (current.signal.aborted || !props.allowed) return
    const expected = props.experiment
    if (
      response.schemaVersion !== 2 ||
      response.experimentId !== expected.id ||
      response.spaceId !== expected.spaceId ||
      response.agentId !== expected.agentId ||
      response.dependencyHash !== expected.dependencyHash ||
      !sameTemplate(response.baseline, expected.baseline) ||
      !sameTemplate(response.candidate, expected.candidate) ||
      consistency.value !== '两组摘要一致' ||
      typeof response.baselineSystemPrompt !== 'string' ||
      typeof response.candidateSystemPrompt !== 'string'
    )
      throw new Error('完整模板身份或非Prompt证明不一致')
    value.value = response
  } catch (failure) {
    if (!current.signal.aborted) error.value = normalizeApiError(failure).message
  } finally {
    if (controller === current) loading.value = false
  }
}
watch(
  () =>
    JSON.stringify([
      onlineIdentity(props.experiment),
      props.experiment.baseline,
      props.experiment.candidate,
      props.allowed,
    ]),
  () => {
    controller?.abort()
    value.value = null
    loading.value = false
    error.value = ''
  },
)
onBeforeUnmount(() => controller?.abort())
</script>
<style scoped>
.prompt-comparison {
  display: grid;
  gap: 12px;
}
.prompt-comparison p {
  margin: 0;
}
.prompt-comparison dd {
  margin: 4px 0 12px;
  overflow-wrap: anywhere;
}
.prompt-pair {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
label {
  display: grid;
  gap: 8px;
}
textarea {
  box-sizing: border-box;
  width: 100%;
  font: inherit;
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
  padding: 12px;
}
pre {
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.added {
  background: var(--adw-color-success-soft);
}
.removed {
  background: var(--adw-color-danger-soft);
}
@media (max-width: 720px) {
  .prompt-pair {
    grid-template-columns: 1fr;
  }
}
</style>
