<template>
  <section class="evidence-layout" aria-label="执行证据">
    <div class="evidence-main">
      <article v-if="isolated" class="surface-card evidence-card" aria-label="冻结候选产物正文">
        <header class="artifact-heading">
          <div>
            <h2>隔离候选产物</h2>
            <p>仅核对本次捕获内容，不修改文档；追加内容是片段，不代表完整新正文。</p>
          </div>
          <el-button :loading="artifactLoading" @click="loadArtifacts">刷新产物</el-button>
        </header>
        <DataState
          :loading="artifactLoading && !artifacts.length"
          :error="artifactError"
          @retry="loadArtifacts"
        >
          <p v-if="!canReadDocument">需要文档读取权限才能展开候选正文；产物身份仍可核对。</p>
          <section
            v-for="entry in artifactEntries"
            :key="entry.artifact.id"
            class="artifact-content"
          >
            <h3>
              {{ artifactLabels[entry.artifact.artifactType] || entry.artifact.artifactType }} #{{
                entry.artifact.sequenceNo
              }}
            </h3>
            <p>{{ artifactPreview(entry.artifact) }}</p>
            <template v-if="canReadDocument">
              <p v-if="entry.content.kind === 'unavailable'" role="status">
                {{ entry.content.reason }}
              </p>
              <template v-else-if="entry.content.kind === 'summary'">
                <pre>{{ entry.content.summary }}</pre>
                <p v-if="entry.content.summary === ''">来源记录了空摘要。</p></template
              >
              <template v-else>
                <p>冻结基线 v{{ entry.content.baseVersion }}；不读取当前文档来补写历史原文。</p>
                <p>{{ entry.content.summary ?? '来源未提供变更摘要。' }}</p>
                <p v-if="!entry.content.changes.length">候选变更列表为空，不表示已完成业务交付。</p>
                <section
                  v-for="(change, index) in entry.content.changes"
                  :key="index"
                  class="artifact-change"
                >
                  <h4>
                    变更 {{ index + 1 }} ·
                    {{ change ? operationLabel(change.operation) : '结构不可用' }}
                  </h4>
                  <p v-if="!change">此项文本字段结构无效，保留其位置，不用其他成功项代替。</p>
                  <p v-else-if="!['append', 'replace'].includes(change.operation || '')">
                    操作码未通过当前协议识别。以下仅展示捕获文本，不认定变更有效或已经应用。
                  </p>
                  <div v-if="change" class="artifact-diff">
                    <div>
                      <h5>
                        {{ change.operation === 'replace' ? '替换前' : '捕获的原文（可选）' }}
                      </h5>
                      <pre v-if="change.oldText !== null">{{ change.oldText }}</pre>
                      <p v-else>未捕获原文，无法重建完整前后差异。</p>
                      <p v-if="change.oldText === ''">空文本</p>
                    </div>
                    <div>
                      <h5>
                        {{
                          change.operation === 'append'
                            ? '追加片段'
                            : change.operation === 'replace'
                              ? '替换后'
                              : '捕获的新文本'
                        }}
                      </h5>
                      <pre>{{ change.newText }}</pre>
                      <p v-if="change.newText === ''">空文本</p>
                    </div>
                  </div>
                </section>
              </template>
            </template>
            <details>
              <summary>产物冻结身份</summary>
              <dl>
                <dt>Artifact ID</dt>
                <dd>{{ entry.artifact.id }}</dd>
                <dt>内容 hash</dt>
                <dd>{{ entry.artifact.payloadSha256 }}</dd>
                <dt>JSON 大小</dt>
                <dd>{{ payloadSize(entry.artifact.payloadJson) }}</dd>
                <dt>schema</dt>
                <dd>{{ entry.artifact.schemaVersion }}</dd>
                <dt>生成时间</dt>
                <dd>{{ entry.artifact.createdAt }}</dd>
              </dl>
            </details>
          </section>
          <p v-if="!artifacts.length">暂无候选产物；空列表不表示执行成功。</p>
        </DataState>
      </article>
      <article class="surface-card evidence-card">
        <header>
          <h2>执行证据链</h2>
          <p>沿业务记录查看执行过程，技术链路通过 Trace 联合诊断。</p>
        </header>
        <ol class="evidence-rail">
          <li>
            <span class="evidence-dot"></span>
            <div>
              <h3>Task 已创建</h3>
              <p>{{ lineageLabel }} · {{ task.executionMode }} · {{ task.createdAt }}</p>
              <small>当前 Run / Task {{ task.id }}</small>
            </div>
          </li>
          <li :class="{ pending: !detail.execution }">
            <span class="evidence-dot"></span>
            <div>
              <h3>{{ detail.execution ? 'Execution 快照已冻结' : '等待 Execution 冻结' }}</h3>
              <p>Prompt、模型、Skill、工具与 MCP 以本次冻结配置为准。</p>
              <code>{{ detail.execution?.executionSnapshotHash || '尚未生成执行快照' }}</code>
            </div>
          </li>
          <li :class="{ pending: !detail.execution }">
            <span class="evidence-dot"></span>
            <div>
              <h3>模型与工具调用审计</h3>
              <p>
                {{ detail.execution?.modelCalls.length ?? '不可用' }} 次模型调用 ·
                {{ detail.execution?.toolCalls.length ?? '不可用' }} 次工具调用
              </p>
              <small
                >Task：{{ task.status }} · Execution：{{
                  detail.execution?.status || '尚未创建'
                }}</small
              >
            </div>
          </li>
          <li>
            <span class="evidence-dot"></span>
            <div>
              <h3>{{ isolated ? '隔离候选产物' : '业务输出引用' }}</h3>
              <p>
                {{
                  isolated
                    ? '只捕获候选，不写入正式文档、文档版本或变更请求。'
                    : detail.output
                      ? '本次业务输出可从任务详情查看。'
                      : '当前没有业务输出引用。'
                }}
              </p>
            </div>
          </li>
          <li :class="{ pending: !task.traceId && !detail.execution?.traceId }">
            <span class="evidence-dot"></span>
            <div>
              <h3>技术 Trace</h3>
              <p>
                {{
                  task.traceId || detail.execution?.traceId || '没有关联 Trace；业务记录仍可查看。'
                }}
              </p>
              <el-button @click="$emit('openTrace')">查看调用审计与 Trace</el-button>
            </div>
          </li>
        </ol>
      </article>
      <article v-if="needsEvaluationLink" class="surface-card evidence-card">
        <header>
          <h2>评估来源与返回</h2>
          <p>关联来自执行 Attempt 的正式外键；血缘根节点不作为评估关联。</p>
        </header>
        <p v-if="!canReadEvaluation">需要本空间的评估读取权限才能查看关联。</p>
        <DataState v-else :loading="linkLoading" :error="linkError" @retry="loadLinks">
          <div v-if="links" class="evidence-links">
            <RouterLink v-if="links.sourceTaskId" :to="taskPath(links.sourceTaskId)"
              >来源 Task #{{ links.sourceTaskId }}</RouterLink
            >
            <RouterLink
              :to="{
                name: 'evaluation-test-cases',
                params: { spaceId: task.spaceId, testCaseId: links.testCaseId },
                query: { versionId: String(links.testCaseVersionId) },
              }"
              >测试用例 #{{ links.testCaseId }} · 版本 #{{ links.testCaseVersionId }}</RouterLink
            >
            <RouterLink
              :to="{
                name: 'evaluation-runs',
                params: { spaceId: task.spaceId, runId: links.runId },
                query: {
                  caseRunId: String(links.caseRunId),
                  attemptId: String(links.caseAttemptId),
                },
              }"
              >返回 EvaluationRun #{{ links.runId }}</RouterLink
            >
            <RouterLink
              v-if="links.experimentId"
              :to="{
                name: 'evaluation-experiments',
                params: { spaceId: task.spaceId, experimentId: links.experimentId },
              }"
              >返回 Experiment #{{ links.experimentId }} · {{ links.variantKey }}</RouterLink
            >
          </div>
          <p v-else>未关联评估 Attempt；可以通过血缘跳转查看来源任务。</p>
        </DataState>
      </article>
    </div>
    <aside class="evidence-side">
      <article class="surface-card evidence-card">
        <header><h2>身份与冻结依据</h2></header>
        <el-tag>{{ lineageLabel }}</el-tag>
        <el-tag :type="isolated ? 'warning' : 'info'"
          >{{ task.executionMode }} · {{ isolated ? '隔离执行' : '正式业务执行' }}</el-tag
        >
        <dl>
          <dt>当前 Task / Run</dt>
          <dd>{{ task.id }}</dd>
          <dt>根任务</dt>
          <dd>
            <RouterLink v-if="canReadTask" :to="taskPath(task.rootTaskId)"
              >#{{ task.rootTaskId }}</RouterLink
            ><span v-else>#{{ task.rootTaskId }}</span>
          </dd>
          <dt>父任务</dt>
          <dd>
            <RouterLink v-if="task.parentTaskId && canReadTask" :to="taskPath(task.parentTaskId)"
              >#{{ task.parentTaskId }}</RouterLink
            ><span v-else>{{ task.parentTaskId || '无' }}</span>
          </dd>
          <dt>Agent</dt>
          <dd>
            <RouterLink
              v-if="canReadAgent"
              :to="{
                name: 'space-agents',
                params: { spaceId: task.spaceId },
                query: { agentId: String(task.agentId) },
              }"
              >{{ detail.agentName || `#${task.agentId}` }}</RouterLink
            ><span v-else>{{ detail.agentName || `#${task.agentId}` }}</span>
          </dd>
          <dt>输入文档</dt>
          <dd>
            <RouterLink
              v-if="canReadDocument"
              :to="`/spaces/${task.spaceId}/documents/${task.documentId}`"
              >#{{ task.documentId }}</RouterLink
            ><span v-else>#{{ task.documentId }}</span>
          </dd>
          <dt>Execution ID</dt>
          <dd>{{ detail.execution?.id || '尚未创建' }}</dd>
          <dt>快照 schema</dt>
          <dd>{{ detail.execution?.executionSnapshotSchemaVersion ?? '不可用' }}</dd>
          <dt>快照 hash</dt>
          <dd>{{ detail.execution?.executionSnapshotHash || '不可用' }}</dd>
          <dt>Prompt hash</dt>
          <dd>{{ detail.execution?.promptHash || '不可用' }}</dd>
          <dt>Trace ID</dt>
          <dd>{{ task.traceId || '未关联' }}</dd>
          <dt>Execution Trace / Span</dt>
          <dd>
            {{ detail.execution?.traceId || '未关联' }} / {{ detail.execution?.spanId || '未关联' }}
          </dd>
        </dl>
        <p v-if="task.lineageType === 'LEGACY_UNKNOWN'" class="evidence-note">
          历史血缘未能可靠识别，根节点仅是确定性 anchor。
        </p>
      </article>
    </aside>
  </section>
</template>

<script setup lang="ts">
import { ElButton, ElTag } from 'element-plus'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { normalizeApiError } from '@/api/errors'
import { getEvaluationTaskLink } from '@/features/evaluation/api/evaluation-api'
import { getTaskArtifacts } from '@/features/task/api/task-api'
import { artifactPreview, artifactContent } from '@/features/task/artifact-preview'
import type { EvaluationTaskLink, ExecutionArtifact } from '@/features/task/engineering-types'
import type { TaskExecutionDetail, TaskLineageType } from '@/features/task/types'
import type { EntityId } from '@/features/workspace/types'
import DataState from '@/shared/components/DataState.vue'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

const props = defineProps<{ detail: TaskExecutionDetail }>()
defineEmits<{ openTrace: [] }>()
const workspace = useWorkspaceStore()
const task = computed(() => props.detail.task),
  isolated = computed(() => task.value.executionMode === 'ISOLATED')
const inCurrentSpace = computed(
  () => String(workspace.currentSpaceId) === String(task.value.spaceId),
)
const canReadTask = computed(
  () => inCurrentSpace.value && workspace.hasPermission(SPACE_PERMISSIONS.TASK_READ),
)
const canReadEvaluation = computed(
  () => canReadTask.value && workspace.hasPermission(SPACE_PERMISSIONS.EVALUATION_READ),
)
const canReadAgent = computed(
  () => inCurrentSpace.value && workspace.hasPermission(SPACE_PERMISSIONS.AGENT_READ),
)
const canReadDocument = computed(
  () => inCurrentSpace.value && workspace.hasPermission(SPACE_PERMISSIONS.DOCUMENT_READ),
)
const needsEvaluationLink = computed(
  () => task.value.lineageType === 'REPLAY' || task.value.lineageType === 'EXPERIMENT',
)
const lineageLabels: Record<TaskLineageType, string> = {
  ORIGINAL: '首次执行',
  RERUN: '重新执行',
  REVIEW_REWORK: '审批返工',
  REPLAY: '冻结回放',
  EXPERIMENT: '实验执行',
  LEGACY_UNKNOWN: '历史未知',
}
const lineageLabel = computed(() => lineageLabels[task.value.lineageType])
const artifactLabels = {
  CHANGE_PROPOSAL: '候选变更提案',
  DRAFT_CHANGES: '候选草稿变更',
  RESULT_SUMMARY: '结果摘要',
}
const artifacts = ref<ExecutionArtifact[]>([]),
  artifactLoading = ref(false),
  artifactError = ref('')
const artifactEntries = computed(() =>
  artifacts.value.map((artifact) => ({
    artifact,
    content: canReadDocument.value
      ? artifactContent(artifact)
      : { kind: 'unavailable' as const, reason: '需要文档读取权限' },
  })),
)
const links = ref<EvaluationTaskLink | null>(null),
  linkLoading = ref(false),
  linkError = ref('')
let artifactController: AbortController | null = null,
  linkController: AbortController | null = null
function taskPath(id: EntityId) {
  return `/spaces/${task.value.spaceId}/tasks/${id}`
}
function payloadSize(payload: string) {
  const size = new window.Blob([payload]).size
  return size < 1024 ? `${size} B` : `${(size / 1024).toFixed(1)} KB`
}
function operationLabel(operation: string | null) {
  return operation === 'append'
    ? '末尾追加'
    : operation === 'replace'
      ? '全文替换'
      : `未识别操作（${operation ?? '未提供'}）`
}
async function loadArtifacts() {
  artifactController?.abort()
  artifacts.value = []
  artifactError.value = ''
  artifactLoading.value = false
  if (!isolated.value || !canReadTask.value) return
  const request = new AbortController()
  artifactController = request
  artifactLoading.value = true
  const current = task.value,
    executionId = props.detail.execution?.id
  try {
    const response = await getTaskArtifacts(current.id, request.signal)
    if (request.signal.aborted) return
    if (
      response.some(
        (item) =>
          String(item.taskId) !== String(current.id) ||
          (executionId != null && String(item.executionId) !== String(executionId)),
      )
    )
      throw new Error('产物关联身份不一致')
    artifacts.value = response
  } catch (e) {
    if (!request.signal.aborted) artifactError.value = normalizeApiError(e).message
  } finally {
    if (!request.signal.aborted) artifactLoading.value = false
  }
}
async function loadLinks() {
  linkController?.abort()
  links.value = null
  linkError.value = ''
  linkLoading.value = false
  if (!needsEvaluationLink.value || !canReadEvaluation.value) return
  const request = new AbortController()
  linkController = request
  linkLoading.value = true
  const current = task.value
  try {
    const response = await getEvaluationTaskLink(current.spaceId, current.id, request.signal)
    if (request.signal.aborted) return
    if (
      response &&
      (String(response.taskId) !== String(current.id) ||
        String(response.spaceId) !== String(current.spaceId))
    )
      throw new Error('评估关联身份不一致')
    links.value = response
  } catch (e) {
    if (!request.signal.aborted) linkError.value = normalizeApiError(e).message
  } finally {
    if (!request.signal.aborted) linkLoading.value = false
  }
}
watch(
  [
    () => task.value.id,
    () => task.value.spaceId,
    () => task.value.executionMode,
    () => task.value.status,
    () => props.detail.execution?.id,
    canReadTask,
  ],
  () => void loadArtifacts(),
  { immediate: true },
)
watch(
  [() => task.value.id, () => task.value.spaceId, () => task.value.lineageType, canReadEvaluation],
  () => void loadLinks(),
  { immediate: true },
)
onBeforeUnmount(() => {
  artifactController?.abort()
  linkController?.abort()
})
</script>

<style scoped>
.evidence-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(300px, 1fr);
  gap: var(--adw-space-4);
  align-items: start;
}
.evidence-main,
.evidence-side {
  display: grid;
  gap: var(--adw-space-4);
  min-width: 0;
}
.evidence-card {
  padding: var(--adw-space-5);
  min-width: 0;
}
header {
  margin-bottom: var(--adw-space-5);
}
h2 {
  margin: 0;
  font-size: var(--adw-font-size-subtitle);
}
h3 {
  margin: 0;
  font-size: 15px;
}
p,
small {
  color: var(--adw-text-secondary);
  line-height: 1.7;
}
header p,
.evidence-note {
  font-size: 12px;
  margin: 6px 0 0;
}
.evidence-rail {
  list-style: none;
  margin: 0;
  padding: 0;
}
.evidence-rail li {
  position: relative;
  display: flex;
  gap: 18px;
  padding: 0 0 30px;
}
.evidence-rail li:last-child {
  padding-bottom: 0;
}
.evidence-rail li:not(:last-child)::before {
  content: '';
  position: absolute;
  top: 18px;
  bottom: 0;
  left: 7px;
  width: 1px;
  background: var(--adw-border-color-light);
}
.evidence-dot {
  width: 15px;
  height: 15px;
  margin-top: 3px;
  border: 3px solid var(--adw-color-primary);
  border-radius: 50%;
  flex-shrink: 0;
  background: white;
}
.pending .evidence-dot {
  border-color: var(--adw-text-tertiary);
}
.evidence-rail p {
  margin: 7px 0;
  font-size: 13px;
  overflow-wrap: anywhere;
}
.evidence-rail code {
  color: var(--adw-text-secondary);
  font-size: 11px;
  overflow-wrap: anywhere;
}
.evidence-rail .el-button {
  margin-top: 8px;
}
dl {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  gap: 10px;
  font-size: 12px;
  margin: 18px 0 0;
}
dt {
  color: var(--adw-text-secondary);
}
dd {
  margin: 0;
  overflow-wrap: anywhere;
}
a {
  color: var(--adw-color-primary);
  text-decoration: none;
}
a:hover {
  text-decoration: underline;
}
.evidence-links {
  display: grid;
  gap: 12px;
  font-size: 13px;
}
.artifact-heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.artifact-content + .artifact-content {
  border-top: 1px solid var(--adw-border-color-light);
  padding-top: 18px;
}
.artifact-content {
  margin-top: 18px;
  overflow-wrap: anywhere;
}
.artifact-change {
  border-left: 3px solid var(--adw-color-primary);
  padding-left: 12px;
  margin: 16px 0;
}
.artifact-diff {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.artifact-diff > div {
  min-width: 0;
  padding: 12px;
  background: var(--adw-surface-muted);
  border: 1px solid var(--adw-border-color-light);
}
.artifact-content pre {
  margin: 8px 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 360px;
  overflow-y: auto;
  font: inherit;
}
.artifact-diff h5 {
  margin: 0;
  font-size: 13px;
}
@media (max-width: 1100px) {
  .artifact-diff {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (max-width: 1100px) {
  .evidence-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
