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
              >{{ formatNumber(row.tokensUsed) }} / {{ formatNumber(row.tokenBudget) }}</template
            ></el-table-column
          >
          <el-table-column label="创建人" prop="creatorName" width="130" />
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
  </section>
</template>
<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue'
import {
  ElButton,
  ElInput,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchTasks } from '@/features/task/api/task-api'
import type { TaskListItem, TaskStatus } from '@/features/task/types'
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
  error = ref('')
const page = reactive({ total: 0, pageNum: 1, pageSize: 10 })
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
onMounted(loadTasks)
onBeforeUnmount(() => controller?.abort())
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
