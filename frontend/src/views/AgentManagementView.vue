<template>
  <section class="agent-page">
    <PageHeader title="Agent 管理" description="为不同文档场景配置独立执行能力">
      <template #breadcrumb
        ><span class="agent-page__breadcrumb">工作台 / Agent 管理</span></template
      >
      <template #actions>
        <el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreateDrawer">
          新建 Agent
        </el-button>
      </template>
    </PageHeader>

    <div class="agent-toolbar surface-card">
      <el-input
        v-model="keyword"
        clearable
        class="agent-toolbar__search"
        placeholder="搜索 Agent 名称或描述"
        aria-label="搜索 Agent"
        @clear="applyFilters"
        @keyup.enter="applyFilters"
      >
        <template #prefix
          ><el-icon><Search /></el-icon
        ></template>
      </el-input>
      <el-select v-model="statusFilter" class="agent-toolbar__select" @change="applyFilters">
        <el-option label="全部状态" value="ALL" />
        <el-option label="已启用" value="ENABLED" />
        <el-option label="已停用" value="DISABLED" />
      </el-select>
      <el-select
        v-model="modelFilter"
        clearable
        filterable
        class="agent-toolbar__select"
        placeholder="全部模型"
        @change="applyFilters"
      >
        <el-option
          v-for="model in models"
          :key="String(model.id)"
          :label="model.displayName"
          :value="model.id"
        />
      </el-select>
      <el-select model-value="RECENT" class="agent-toolbar__select" aria-label="Agent 排序">
        <el-option label="最近更新" value="RECENT" />
      </el-select>
      <div class="agent-toolbar__spacer"></div>
      <span class="agent-toolbar__count">
        共 {{ page.total }} 个 Agent
        <template v-if="activeAgentCount !== null">
          · <b>已启用 {{ activeAgentCount }}</b>
        </template>
      </span>
      <div class="agent-layout-toggle" aria-label="布局切换">
        <button
          type="button"
          :class="{ active: layout === 'grid' }"
          aria-label="卡片布局"
          @click="layout = 'grid'"
        >
          <el-icon><Grid /></el-icon>
        </button>
        <button
          type="button"
          :class="{ active: layout === 'list' }"
          aria-label="列表布局"
          @click="layout = 'list'"
        >
          <el-icon><List /></el-icon>
        </button>
      </div>
    </div>

    <DataState
      :loading="loading"
      :error="errorMessage"
      :empty="!loading && !agents.length"
      loading-text="正在加载 Agent"
      :empty-text="hasFilters ? '没有匹配的 Agent' : '当前空间还没有 Agent'"
      @retry="loadAgents"
    >
      <div class="agent-collection" :class="`agent-collection--${layout}`">
        <AgentCard
          v-for="agent in agents"
          :key="String(agent.id)"
          :agent="agent"
          :layout="layout"
          :can-manage="canManage"
          :can-configure="canConfigure"
          @configure="openConfigDrawer"
          @toggle="toggleAgent"
          @delete="removeAgent"
        />
      </div>
    </DataState>

    <footer v-if="page.total > 0" class="agent-pagination">
      <span>共 {{ page.total }} 条</span>
      <el-pagination
        v-model:current-page="page.pageNum"
        v-model:page-size="page.pageSize"
        background
        layout="sizes, prev, pager, next, jumper"
        :page-sizes="[9, 18, 36, 72]"
        :total="page.total"
        @current-change="loadAgents"
        @size-change="handlePageSizeChange"
      />
    </footer>

    <AgentConfigDrawer
      v-model:open="drawerOpen"
      :agent-id="selectedAgentId"
      :space-id="spaceId"
      :can-manage="canManage"
      :can-bind-skill="canBindSkill"
      :can-bind-mcp="canBindMcp"
      :can-read-skill="canReadSkill"
      :can-read-mcp="canReadMcp"
      @saved="refreshAfterMutation"
    />
  </section>
</template>

<script setup lang="ts">
import { Grid, List, Plus, Search } from '@element-plus/icons-vue'
import {
  ElButton,
  ElIcon,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
} from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  deleteAgent,
  getAgent,
  getAgentOverviewStats,
  listModels,
  searchAgents,
  toAgentUpdateInput,
  updateAgent,
} from '@/features/agent/api/agent-api'
import AgentCard from '@/features/agent/components/AgentCard.vue'
import AgentConfigDrawer from '@/features/agent/components/AgentConfigDrawer.vue'
import type {
  AgentCard as AgentCardData,
  AgentPage,
  AgentStatus,
  ModelOption,
} from '@/features/agent/types'
import type { EntityId } from '@/features/workspace/types'
import DataState from '@/shared/components/DataState.vue'
import PageHeader from '@/shared/components/PageHeader.vue'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

const route = useRoute()
const workspaceStore = useWorkspaceStore()
const keyword = ref('')
const statusFilter = ref<'ALL' | AgentStatus>('ALL')
const modelFilter = ref<EntityId | null>(null)
const layout = ref<'grid' | 'list'>('grid')
const loading = ref(false)
const errorMessage = ref('')
const agents = ref<AgentCardData[]>([])
const models = ref<ModelOption[]>([])
const activeAgentCount = ref<number | null>(null)
const page = reactive<AgentPage>({ records: [], total: 0, pageNum: 1, pageSize: 9 })
const drawerOpen = ref(false)
const selectedAgentId = ref<EntityId | null>(null)
let requestController: AbortController | null = null

const spaceId = computed<EntityId>(() => String(route.params.spaceId))
const canManage = computed(() => workspaceStore.hasPermission(SPACE_PERMISSIONS.AGENT_MANAGE))
const canBindSkill = computed(() =>
  workspaceStore.hasPermission(SPACE_PERMISSIONS.AGENT_BIND_SKILL),
)
const canBindMcp = computed(() => workspaceStore.hasPermission(SPACE_PERMISSIONS.AGENT_BIND_MCP))
const canReadSkill = computed(() => workspaceStore.hasPermission(SPACE_PERMISSIONS.SKILL_READ))
const canReadMcp = computed(() => workspaceStore.hasPermission(SPACE_PERMISSIONS.MCP_READ))
const canConfigure = computed(() => canManage.value || canBindSkill.value || canBindMcp.value)
const hasFilters = computed(
  () => keyword.value.trim() || statusFilter.value !== 'ALL' || modelFilter.value !== null,
)

onMounted(() => {
  void loadModels()
  void loadStats()
  void loadAgents()
})
onBeforeUnmount(() => requestController?.abort())
watch(spaceId, () => {
  page.pageNum = 1
  drawerOpen.value = false
  modelFilter.value = null
  void loadModels()
  void loadStats()
  void loadAgents()
})

async function loadAgents(): Promise<void> {
  requestController?.abort()
  const controller = new AbortController()
  requestController = controller
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await searchAgents(spaceId.value, {
      keyword: keyword.value.trim(),
      status: statusFilter.value === 'ALL' ? undefined : statusFilter.value,
      modelId: modelFilter.value || undefined,
      pageNum: page.pageNum,
      pageSize: page.pageSize,
      signal: controller.signal,
    })
    agents.value = result.records
    Object.assign(page, result)
  } catch (error) {
    if (controller.signal.aborted) return
    errorMessage.value = error instanceof Error ? error.message : 'Agent 列表加载失败'
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}

async function loadModels(): Promise<void> {
  try {
    models.value = await listModels(false)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模型列表加载失败')
  }
}

async function loadStats(): Promise<void> {
  try {
    const stats = await getAgentOverviewStats(spaceId.value)
    activeAgentCount.value = stats.activeAgentCount
  } catch {
    activeAgentCount.value = null
  }
}

function applyFilters(): void {
  page.pageNum = 1
  void loadAgents()
}

function handlePageSizeChange(): void {
  page.pageNum = 1
  void loadAgents()
}

function openCreateDrawer(): void {
  selectedAgentId.value = null
  drawerOpen.value = true
}

function openConfigDrawer(agent: AgentCardData): void {
  selectedAgentId.value = agent.id
  drawerOpen.value = true
}

async function toggleAgent(agent: AgentCardData): Promise<void> {
  const enabling = agent.status === 'DISABLED'
  try {
    await ElMessageBox.confirm(
      enabling
        ? '启用后可以继续选择该 Agent 创建任务。'
        : '停用后不能创建新任务，已运行任务不会受影响。',
      enabling ? '启用 Agent' : '停用 Agent',
      { type: enabling ? 'info' : 'warning' },
    )
    const detail = await getAgent(agent.id)
    await updateAgent(agent.id, toAgentUpdateInput(detail, enabling ? 'ENABLED' : 'DISABLED'))
    ElMessage.success(enabling ? 'Agent 已启用' : 'Agent 已停用')
    await refreshAfterMutation()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : 'Agent 状态更新失败')
  }
}

async function removeAgent(agent: AgentCardData): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除“${agent.name}”吗？删除后不能再用于创建任务。`,
      '删除 Agent',
      { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' },
    )
    await deleteAgent(agent.id)
    ElMessage.success('Agent 已删除')
    if (agents.value.length === 1 && page.pageNum > 1) page.pageNum--
    await refreshAfterMutation()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : 'Agent 删除失败')
  }
}

async function refreshAfterMutation(): Promise<void> {
  await Promise.all([loadAgents(), loadStats()])
}
</script>

<style scoped>
.agent-page {
  display: grid;
  gap: var(--adw-space-6);
}
.agent-page__breadcrumb {
  display: block;
  margin-bottom: var(--adw-space-3);
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.agent-toolbar {
  display: flex;
  align-items: center;
  gap: var(--adw-space-3);
  padding: var(--adw-space-4);
}
.agent-toolbar__search {
  width: min(320px, 30vw);
}
.agent-toolbar__select {
  width: 160px;
}
.agent-toolbar__spacer {
  flex: 1;
}
.agent-toolbar__count {
  color: var(--adw-text-secondary);
  font-size: 13px;
  white-space: nowrap;
}
.agent-toolbar__count b {
  color: var(--adw-color-success);
  font-weight: 600;
}
.agent-layout-toggle {
  display: flex;
  gap: 4px;
}
.agent-layout-toggle button {
  display: inline-flex;
  width: 36px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--adw-border-color);
  border-radius: 6px;
  color: var(--adw-text-secondary);
  background: var(--adw-surface);
  cursor: pointer;
}
.agent-layout-toggle button.active {
  border-color: var(--adw-color-primary);
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}
.agent-collection {
  display: grid;
  gap: var(--adw-space-4);
}
.agent-collection--grid {
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
}
.agent-collection--list {
  grid-template-columns: 1fr;
}
.agent-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--adw-space-6);
  padding: var(--adw-space-2) 0;
  color: var(--adw-text-secondary);
  font-size: 13px;
}
@media (max-width: 1050px) {
  .agent-toolbar {
    align-items: stretch;
    flex-wrap: wrap;
  }
  .agent-toolbar__search {
    width: 100%;
  }
  .agent-toolbar__spacer {
    display: none;
  }
}
@media (max-width: 680px) {
  .agent-toolbar__select {
    width: calc(50% - 6px);
  }
  .agent-pagination {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
