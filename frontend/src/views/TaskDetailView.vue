<template>
  <section class="detail-page">
    <PageHeader :title="task?.name || '任务详情'" description="查看任务状态、输入与执行结果">
      <template #breadcrumb
        ><button class="back-link" type="button" @click="router.push(`/spaces/${spaceId}/tasks`)">
          任务</button
        ><span> / {{ task?.taskNo || '详情' }}</span></template
      >
      <template #actions
        ><el-button v-if="canRerun" type="primary" :loading="rerunning" @click="rerun"
          >重新运行</el-button
        ><el-button
          v-if="canTerminate"
          type="danger"
          plain
          :loading="terminating"
          @click="terminate"
          >终止任务</el-button
        ></template
      >
    </PageHeader>
    <DataState :loading="loading" :error="error" @retry="loadTask">
      <template v-if="task">
        <article class="surface-card progress-card">
          <header>
            <div>
              <h2>执行进度</h2>
              <p>{{ progressDescription }}</p>
            </div>
            <span>{{
              refreshing ? '正在刷新…' : isActive ? '每 3 秒自动刷新' : '执行已结束'
            }}</span>
          </header>
          <el-steps
            :active="activeStep"
            align-center
            finish-status="success"
            :process-status="
              task.status === 'FAILED' || task.status === 'TERMINATED' ? 'error' : 'process'
            "
            ><el-step title="任务已创建" /><el-step title="等待调度" /><el-step
              title="发送至 Agent" /><el-step title="Agent 执行" /><el-step title="处理完成"
          /></el-steps>
          <dl class="execution-times">
            <div>
              <dt>开始时间</dt>
              <dd>{{ formatTime(task.startTime) }}</dd>
            </div>
            <div>
              <dt>派发时间</dt>
              <dd>{{ formatTime(task.dispatchedAt) }}</dd>
            </div>
            <div>
              <dt>最近心跳</dt>
              <dd>{{ formatTime(task.lastHeartbeatAt) }}</dd>
            </div>
            <div>
              <dt>系统重试</dt>
              <dd>{{ task.retryCount }} / 3 次</dd>
            </div>
            <div>
              <dt>结束时间</dt>
              <dd>{{ formatTime(task.endTime) }}</dd>
            </div>
          </dl>
        </article>
        <div class="detail-stats">
          <article class="surface-card">
            <span>状态</span><strong>{{ statusLabel(task.status) }}</strong>
          </article>
          <article class="surface-card">
            <span>Token</span
            ><strong
              >{{ formatNumber(task.tokensUsed) }} / {{ formatNumber(task.tokenBudget) }}</strong
            >
          </article>
          <article class="surface-card">
            <span>读取范围</span
            ><strong>{{
              task.readScope === 'FULL'
                ? `全文 · ${task.focusRegions.length} 个关注区域`
                : `${task.focusRegions.length} 个授权区域`
            }}</strong>
          </article>
          <article class="surface-card">
            <span>创建时间</span><strong>{{ formatTime(task.createdAt) }}</strong>
          </article>
        </div>
        <div class="detail-grid">
          <article class="surface-card detail-card">
            <h2>任务指令</h2>
            <p>{{ task.instruction }}</p>
            <div v-if="task.focusRegions.length" class="focus-regions">
              <h3>关注区域</h3>
              <div v-for="(region, index) in task.focusRegions" :key="`${region.start}-${index}`">
                <strong>区域 {{ index + 1 }} · {{ region.length }} 字符</strong>
                <p>{{ region.textPreview }}</p>
                <small>{{ region.instruction }}</small>
              </div>
            </div>
          </article>
          <article class="surface-card detail-card">
            <h2>执行结果</h2>
            <p>{{ task.resultSummary || '任务尚未生成结果' }}</p>
            <el-alert
              v-if="task.errorMessage"
              type="error"
              :closable="false"
              :title="task.errorMessage"
            />
          </article>
        </div>
      </template>
    </DataState>
  </section>
</template>
<script setup lang="ts">
import { ElAlert, ElButton, ElMessage, ElMessageBox, ElStep, ElSteps } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { normalizeApiError } from '@/api/errors'
import { getTask, rerunTask, terminateTask } from '@/features/task/api/task-api'
import type { TaskDetail, TaskStatus } from '@/features/task/types'
import DataState from '@/shared/components/DataState.vue'
import PageHeader from '@/shared/components/PageHeader.vue'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

const route = useRoute(),
  router = useRouter(),
  workspace = useWorkspaceStore(),
  task = ref<TaskDetail | null>(null),
  loading = ref(false),
  refreshing = ref(false),
  terminating = ref(false),
  rerunning = ref(false),
  error = ref('')
let controller: AbortController | null = null,
  pollTimer: number | null = null
const spaceId = computed(() => route.params.spaceId as string),
  taskId = computed(() => route.params.taskId as string)
const canTerminate = computed(() =>
  Boolean(
    task.value &&
    ['PENDING', 'DISPATCHED', 'RUNNING', 'WAITING_INPUT', 'WAITING_AUTH'].includes(
      task.value.status,
    ) &&
    workspace.hasPermission(SPACE_PERMISSIONS.TASK_TERMINATE),
  ),
)
const canRerun = computed(() =>
  Boolean(
    task.value?.status === 'FAILED' && workspace.hasPermission(SPACE_PERMISSIONS.TASK_CREATE),
  ),
)
const activeStatuses: TaskStatus[] = [
  'PENDING',
  'DISPATCHED',
  'RUNNING',
  'WAITING_INPUT',
  'WAITING_AUTH',
  'CANCELING',
]
const isActive = computed(() => Boolean(task.value && activeStatuses.includes(task.value.status)))
const activeStep = computed(() => {
  if (!task.value) return 0
  switch (task.value.status) {
    case 'PENDING':
      return 1
    case 'DISPATCHED':
      return 2
    case 'RUNNING':
    case 'WAITING_INPUT':
    case 'WAITING_AUTH':
    case 'CANCELING':
      return 3
    case 'COMPLETED':
      return 5
    case 'FAILED':
    case 'TERMINATED':
      return task.value.dispatchedAt ? 3 : task.value.startTime ? 2 : 1
    default:
      return 0
  }
})
const progressDescription = computed(() =>
  task.value
    ? (
        {
          PENDING: '任务正在等待执行资源。',
          DISPATCHED: '任务已发送给 Agent，等待开始执行。',
          RUNNING: 'Agent 正在处理任务。',
          WAITING_INPUT: 'Agent 正在等待补充输入。',
          WAITING_AUTH: 'Agent 正在等待操作授权。',
          CANCELING: '正在请求 Agent 取消任务。',
          COMPLETED: '任务已经执行完成。',
          TERMINATED: '任务已由用户终止。',
          FAILED: '任务执行异常，可查看错误后重新运行。',
        } satisfies Record<TaskStatus, string>
      )[task.value.status]
    : '',
)
onMounted(() => loadTask())
onBeforeUnmount(stopPolling)
function stopPolling() {
  controller?.abort()
  if (pollTimer !== null) window.clearTimeout(pollTimer)
  pollTimer = null
}
function schedulePoll() {
  if (pollTimer !== null) window.clearTimeout(pollTimer)
  pollTimer = isActive.value ? window.setTimeout(() => loadTask(true), 3000) : null
}
async function loadTask(background = false) {
  controller?.abort()
  controller = new AbortController()
  if (background) refreshing.value = true
  else loading.value = true
  if (!background) error.value = ''
  try {
    task.value = await getTask(taskId.value, controller.signal)
  } catch (e) {
    if (!controller.signal.aborted && !task.value) error.value = normalizeApiError(e).message
  } finally {
    if (!controller.signal.aborted) {
      loading.value = false
      refreshing.value = false
      schedulePoll()
    }
  }
}
async function terminate() {
  try {
    await ElMessageBox.confirm('确定终止这个任务吗？', '终止任务', { type: 'warning' })
    stopPolling()
    terminating.value = true
    task.value = await terminateTask(taskId.value)
    ElMessage.success('任务已终止')
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(normalizeApiError(e).message)
  } finally {
    terminating.value = false
    schedulePoll()
  }
}
async function rerun() {
  try {
    await ElMessageBox.confirm('将清除本次失败状态并重新执行，确定继续吗？', '重新运行任务', {
      type: 'warning',
    })
    stopPolling()
    rerunning.value = true
    task.value = await rerunTask(taskId.value)
    ElMessage.success('任务已重新进入执行队列')
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(normalizeApiError(e).message)
  } finally {
    rerunning.value = false
    schedulePoll()
  }
}
const labels: Record<TaskStatus, string> = {
  PENDING: '待运行',
  DISPATCHED: '已分发',
  RUNNING: '运行中',
  WAITING_INPUT: '等待输入',
  WAITING_AUTH: '等待授权',
  CANCELING: '取消中',
  COMPLETED: '已完成',
  TERMINATED: '已终止',
  FAILED: '异常失败',
}
function statusLabel(value: TaskStatus) {
  return labels[value]
}
function formatNumber(value: number | null) {
  return value == null ? '—' : value.toLocaleString('zh-CN')
}
function formatTime(value: string | null) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'medium' }).format(
        new Date(value),
      )
    : '—'
}
</script>
<style scoped>
.detail-page {
  display: grid;
  gap: 24px;
}
.back-link {
  padding: 0;
  border: 0;
  color: var(--adw-color-primary);
  background: none;
  cursor: pointer;
}
.progress-card {
  padding: 20px;
}
.progress-card header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
}
.progress-card h2 {
  margin: 0;
  font-size: 18px;
}
.progress-card header p {
  margin: 6px 0 0;
  color: var(--adw-text-secondary);
}
.progress-card header span {
  color: var(--adw-text-tertiary);
  font-size: 13px;
}
.execution-times {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin: 24px 0 0;
  padding-top: 16px;
  border-top: 1px solid var(--adw-border-color-light);
}
.execution-times div {
  min-width: 0;
}
.execution-times dt {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.execution-times dd {
  margin: 5px 0 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.detail-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.detail-stats article,
.detail-card {
  padding: 20px;
}
.detail-stats span {
  display: block;
  color: var(--adw-text-secondary);
}
.detail-stats strong {
  display: block;
  margin-top: 10px;
  font-size: 18px;
}
.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.detail-card h2 {
  margin: 0 0 16px;
  font-size: 18px;
}
.detail-card p {
  white-space: pre-wrap;
  line-height: 1.7;
}
.focus-regions {
  display: grid;
  gap: 10px;
  margin-top: 20px;
}
.focus-regions h3 {
  margin: 0;
}
.focus-regions > div {
  padding: 12px;
  border: 1px solid var(--adw-border-color);
  border-radius: 8px;
}
.focus-regions p {
  margin: 6px 0;
  color: var(--adw-text-secondary);
}
.focus-regions small {
  color: var(--adw-color-primary);
}
@media (max-width: 900px) {
  .execution-times {
    grid-template-columns: repeat(2, 1fr);
  }
  .detail-stats {
    grid-template-columns: 1fr 1fr;
  }
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 560px) {
  .progress-card header {
    flex-direction: column;
  }
  .execution-times,
  .detail-stats {
    grid-template-columns: 1fr;
  }
}
</style>
