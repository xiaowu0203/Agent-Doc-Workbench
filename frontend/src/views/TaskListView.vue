<template>
  <section class="task-page">
    <PageHeader title="任务" description="查看当前空间的 Agent 任务及执行状态">
      <template #breadcrumb><span>工作台 / 任务</span></template>
      <template #actions
        ><el-button
          v-if="canCreate"
          type="primary"
          :icon="Plus"
          @click="router.push(`/spaces/${spaceId}/tasks/new`)"
          >新建任务</el-button
        ></template
      >
    </PageHeader>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="执行任务" name="tasks">
        <div class="task-toolbar surface-card">
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索任务编号、名称或指令"
            @keyup.enter="applyFilters"
            @clear="applyFilters"
          />
          <el-select v-model="status" @change="applyFilters"
            ><el-option label="全部状态" value="ALL" /><el-option
              v-for="item in statuses"
              :key="item.value"
              :label="item.label"
              :value="item.value"
          /></el-select>
          <el-button @click="loadTasks">刷新</el-button>
        </div>
        <DataState
          :loading="loading"
          :error="error"
          :empty="!tasks.length"
          empty-text="当前空间还没有任务"
          @retry="loadTasks"
        >
          <div class="task-table surface-card">
            <el-table :data="tasks">
              <el-table-column label="任务" min-width="250"
                ><template #default="{ row }"
                  ><button
                    class="task-link"
                    type="button"
                    @click="router.push(`/spaces/${spaceId}/tasks/${row.id}`)"
                  >
                    <strong>{{ row.name }}</strong
                    ><small>{{ row.taskNo }}</small>
                  </button></template
                ></el-table-column
              >
              <el-table-column label="状态" width="120"
                ><template #default="{ row }"
                  ><el-tag :type="statusType(row.status)">{{
                    statusLabel(row.status)
                  }}</el-tag></template
                ></el-table-column
              >
              <el-table-column label="Agent" prop="agentName" min-width="150" />
              <el-table-column label="关联文档" min-width="200"
                ><template #default="{ row }"
                  >{{ row.documentTitle || '—' }}
                  <el-tag size="small" effect="plain">{{
                    row.documentType === 'FORMAL' ? '正式' : '草稿'
                  }}</el-tag></template
                ></el-table-column
              >
              <el-table-column label="Token" width="150"
                ><template #default="{ row }"
                  >{{ formatNumber(row.tokensUsed) }} /
                  {{ formatNumber(row.tokenBudget) }}</template
                ></el-table-column
              >
              <el-table-column label="创建人" prop="creatorName" width="130" />
              <el-table-column label="操作" width="230" fixed="right"
                ><template #default="{ row }"
                  ><div class="task-actions">
                    <el-button link type="primary" @click.stop="viewTask(row.id)">查看</el-button>
                    <el-button
                      v-if="canCreate && row.status === 'PENDING'"
                      link
                      type="primary"
                      :loading="actionId === `run-${row.id}`"
                      @click.stop="triggerRun(row.id)"
                      >运行</el-button
                    >
                    <el-button
                      v-if="canTerminate && terminableStatuses.includes(row.status)"
                      link
                      type="danger"
                      :loading="actionId === `terminate-${row.id}`"
                      @click.stop="terminateRow(row)"
                      >终止</el-button
                    >
                    <el-button
                      v-if="canCreate && row.status === 'FAILED'"
                      link
                      type="primary"
                      :loading="actionId === `rerun-${row.id}`"
                      @click.stop="rerunRow(row)"
                      >重跑</el-button
                    >
                  </div></template
                ></el-table-column
              >
              <el-table-column label="创建时间" width="180"
                ><template #default="{ row }">{{
                  formatTime(row.createdAt)
                }}</template></el-table-column
              >
            </el-table>
          </div>
        </DataState>
        <footer v-if="page.total" class="task-pagination">
          <span>共 {{ page.total }} 条</span
          ><el-pagination
            v-model:current-page="page.pageNum"
            v-model:page-size="page.pageSize"
            background
            layout="sizes, prev, pager, next"
            :page-sizes="[10, 20, 50]"
            :total="page.total"
            @change="loadTasks"
          />
        </footer>
      </el-tab-pane>
      <el-tab-pane label="任务草稿" name="drafts">
        <DataState
          :loading="draftLoading"
          :error="draftError"
          :empty="!drafts.length"
          empty-text="当前空间没有任务草稿"
          @retry="loadDrafts"
        >
          <div class="task-table surface-card">
            <el-table :data="drafts">
              <el-table-column label="任务草稿" min-width="260">
                <template #default="{ row }">
                  <strong>{{ row.name || '未命名任务' }}</strong>
                  <small>{{ row.instruction || '尚未填写目标描述' }}</small>
                </template>
              </el-table-column>
              <el-table-column label="目标文档" min-width="150">
                <template #default="{ row }">{{ row.documentId || '未选择' }}</template>
              </el-table-column>
              <el-table-column label="读取范围" width="120">
                <template #default="{ row }">{{
                  row.readScope === 'RANGES' ? '选中区域' : '全文'
                }}</template>
              </el-table-column>
              <el-table-column label="更新时间" width="180">
                <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="250" fixed="right">
                <template #default="{ row }">
                  <div class="task-actions">
                    <el-button link type="primary" @click="editDraft(row.id)">继续编辑</el-button>
                    <el-button
                      link
                      type="success"
                      :loading="actionId === `launch-draft-${row.id}`"
                      @click="launchDraft(row.id)"
                    >
                      启动
                    </el-button>
                    <el-button
                      link
                      type="danger"
                      :loading="actionId === `delete-draft-${row.id}`"
                      @click="removeDraft(row.id)"
                    >
                      删除
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </DataState>
        <footer v-if="draftPage.total" class="task-pagination">
          <span>共 {{ draftPage.total }} 条</span
          ><el-pagination
            v-model:current-page="draftPage.pageNum"
            v-model:page-size="draftPage.pageSize"
            background
            layout="sizes, prev, pager, next"
            :page-sizes="[10, 20, 50]"
            :total="draftPage.total"
            @change="loadDrafts"
          />
        </footer>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>
<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue'
import {
  ElButton,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTabPane,
  ElTabs,
} from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  deleteTaskDraft,
  launchTaskDraft,
  rerunTask,
  runTask,
  searchTaskDrafts,
  searchTasks,
  terminateTask,
} from '@/features/task/api/task-api'
import type { TaskDraft, TaskListItem, TaskStatus } from '@/features/task/types'
import DataState from '@/shared/components/DataState.vue'
import PageHeader from '@/shared/components/PageHeader.vue'
import { normalizeApiError } from '@/api/errors'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

const route = useRoute(),
  router = useRouter(),
  workspace = useWorkspaceStore()
const spaceId = computed(() => route.params.spaceId as string),
  canCreate = computed(() => workspace.hasPermission(SPACE_PERMISSIONS.TASK_CREATE))
const keyword = ref(''),
  status = ref<'ALL' | TaskStatus>('ALL'),
  tasks = ref<TaskListItem[]>([]),
  loading = ref(false),
  error = ref(''),
  actionId = ref<string | null>(null)
const page = reactive({ total: 0, pageNum: 1, pageSize: 10 })
const activeTab = ref<'tasks' | 'drafts'>('tasks')
const drafts = ref<TaskDraft[]>([])
const draftLoading = ref(false)
const draftError = ref('')
const draftPage = reactive({ total: 0, pageNum: 1, pageSize: 10 })
let controller: AbortController | null = null
const statuses: { value: TaskStatus; label: string }[] = [
  ['PENDING', '待运行'],
  ['DISPATCHED', '已分发'],
  ['RUNNING', '运行中'],
  ['WAITING_INPUT', '等待输入'],
  ['WAITING_AUTH', '等待授权'],
  ['CANCELING', '取消中'],
  ['COMPLETED', '已完成'],
  ['TERMINATED', '已终止'],
  ['FAILED', '异常失败'],
].map(([value, label]) => ({ value: value as TaskStatus, label }))
const terminableStatuses: TaskStatus[] = [
  'PENDING',
  'DISPATCHED',
  'RUNNING',
  'WAITING_INPUT',
  'WAITING_AUTH',
]
const canTerminate = computed(() => workspace.hasPermission(SPACE_PERMISSIONS.TASK_TERMINATE))
onMounted(loadTasks)
onBeforeUnmount(() => controller?.abort())
watch(activeTab, (tab) => {
  if (tab === 'drafts' && !drafts.value.length) void loadDrafts()
})
watch(spaceId, () => {
  page.pageNum = 1
  void loadTasks()
})
function applyFilters() {
  page.pageNum = 1
  void loadTasks()
}
async function loadTasks() {
  controller?.abort()
  controller = new AbortController()
  loading.value = true
  error.value = ''
  try {
    const result = await searchTasks(
      {
        spaceId: spaceId.value,
        pageNum: page.pageNum,
        pageSize: page.pageSize,
        keyword: keyword.value.trim() || undefined,
        status: status.value === 'ALL' ? undefined : status.value,
      },
      controller.signal,
    )
    tasks.value = result.records
    page.total = result.total
  } catch (e) {
    if (!controller.signal.aborted) error.value = normalizeApiError(e).message
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}
async function loadDrafts() {
  controller?.abort()
  controller = new AbortController()
  draftLoading.value = true
  draftError.value = ''
  try {
    const result = await searchTaskDrafts(
      spaceId.value,
      draftPage.pageNum,
      draftPage.pageSize,
      undefined,
      controller.signal,
    )
    drafts.value = result.records
    draftPage.total = result.total
  } catch (e) {
    if (!controller.signal.aborted) draftError.value = normalizeApiError(e).message
  } finally {
    if (!controller.signal.aborted) draftLoading.value = false
  }
}
function editDraft(id: TaskDraft['id']) {
  void router.push({
    name: 'space-task-create',
    params: { spaceId: spaceId.value },
    query: { draftId: String(id) },
  })
}
async function launchDraft(id: TaskDraft['id']) {
  if (actionId.value) return
  actionId.value = `launch-draft-${id}`
  try {
    const task = await launchTaskDraft(id)
    ElMessage.success(`任务 ${task.taskNo} 已启动`)
    await router.push(`/spaces/${spaceId.value}/tasks/${task.id}`)
  } catch (e) {
    ElMessage.error(normalizeApiError(e).message)
  } finally {
    actionId.value = null
  }
}
async function removeDraft(id: TaskDraft['id']) {
  if (actionId.value) return
  try {
    await ElMessageBox.confirm('删除后无法恢复，确定删除这个任务草稿吗？', '删除草稿', {
      type: 'warning',
    })
    actionId.value = `delete-draft-${id}`
    await deleteTaskDraft(id)
    ElMessage.success('任务草稿已删除')
    if (drafts.value.length === 1 && draftPage.pageNum > 1) draftPage.pageNum -= 1
    await loadDrafts()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(normalizeApiError(e).message)
  } finally {
    actionId.value = null
  }
}
async function triggerRun(id: TaskListItem['id']) {
  if (actionId.value) return
  actionId.value = `run-${id}`
  try {
    await runTask(id)
    ElMessage.success('任务已重新提交执行队列')
    await loadTasks()
  } catch (e) {
    ElMessage.error(normalizeApiError(e).message)
  } finally {
    actionId.value = null
  }
}
function viewTask(id: TaskListItem['id']) {
  void router.push(`/spaces/${spaceId.value}/tasks/${id}`)
}
async function terminateRow(value: unknown) {
  const task = value as TaskListItem
  if (actionId.value) return
  try {
    await ElMessageBox.confirm(`确定终止“${task.name}”吗？`, '终止任务', { type: 'warning' })
    actionId.value = `terminate-${task.id}`
    await terminateTask(task.id)
    ElMessage.success('任务已终止')
    await loadTasks()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(normalizeApiError(e).message)
  } finally {
    actionId.value = null
  }
}
async function rerunRow(value: unknown) {
  const task = value as TaskListItem
  if (actionId.value) return
  try {
    await ElMessageBox.confirm(
      `将复用“${task.name}”的输入创建新任务，确定继续吗？`,
      '重新运行任务',
      {
        type: 'warning',
      },
    )
    actionId.value = `rerun-${task.id}`
    const result = await rerunTask(task.id)
    ElMessage.success('新任务已进入执行队列')
    await router.push(`/spaces/${spaceId.value}/tasks/${result.id}`)
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(normalizeApiError(e).message)
  } finally {
    actionId.value = null
  }
}
function statusLabel(value: TaskStatus) {
  return statuses.find((x) => x.value === value)?.label || value
}
function statusType(value: TaskStatus) {
  return value === 'COMPLETED'
    ? 'success'
    : value === 'FAILED'
      ? 'danger'
      : ['RUNNING', 'DISPATCHED'].includes(value)
        ? 'primary'
        : ['PENDING', 'WAITING_INPUT', 'WAITING_AUTH', 'CANCELING'].includes(value)
          ? 'warning'
          : 'info'
}
function formatNumber(value: number | null) {
  return value == null ? '—' : value.toLocaleString('zh-CN')
}
function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'short' }).format(
    new Date(value),
  )
}
</script>
<style scoped>
.task-page {
  display: grid;
  gap: 24px;
}
.task-toolbar {
  display: flex;
  gap: 12px;
  padding: 16px;
}
.task-toolbar .el-input {
  max-width: 420px;
}
.task-toolbar .el-select {
  width: 160px;
}
.task-table {
  overflow: hidden;
}
.task-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.task-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.task-link {
  padding: 0;
  border: 0;
  color: inherit;
  background: none;
  text-align: left;
  cursor: pointer;
}
.task-link:hover strong {
  color: var(--adw-color-primary);
}
.task-table strong,
.task-table small {
  display: block;
}
.task-table small {
  margin-top: 5px;
  color: var(--adw-text-tertiary);
}
.task-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--adw-text-secondary);
}
@media (max-width: 720px) {
  .task-toolbar {
    flex-wrap: wrap;
  }
  .task-toolbar .el-input {
    max-width: none;
  }
  .task-pagination {
    align-items: flex-start;
    gap: 12px;
    flex-direction: column;
  }
}
</style>
