<template>
  <section class="model-page">
    <PageHeader title="模型配置" description="统一管理供各空间 Agent 使用的模型与供应商连接">
      <template #breadcrumb
        ><span class="model-page__breadcrumb">系统管理 / 模型配置</span></template
      >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="openCreateDrawer">添加模型</el-button>
      </template>
    </PageHeader>

    <el-alert
      title="模型配置为平台级能力；配置变更仅影响后续创建的执行快照。"
      type="info"
      :closable="false"
      show-icon
    />

    <div class="model-toolbar surface-card">
      <el-input
        v-model="keyword"
        clearable
        class="model-toolbar__search"
        placeholder="搜索模型名称或模型标识"
        aria-label="搜索模型"
        @clear="applyFilters"
        @keyup.enter="applyFilters"
      >
        <template #prefix
          ><el-icon><Search /></el-icon
        ></template>
      </el-input>
      <el-select v-model="providerFilter" class="model-toolbar__select" @change="applyFilters">
        <el-option label="全部供应商" value="ALL" />
        <el-option
          v-for="provider in MODEL_PROVIDERS"
          :key="provider.value"
          :label="provider.label"
          :value="provider.value"
        />
      </el-select>
      <el-select v-model="statusFilter" class="model-toolbar__select" @change="applyFilters">
        <el-option label="全部状态" value="ALL" />
        <el-option label="已启用" value="ENABLED" />
        <el-option label="已停用" value="DISABLED" />
      </el-select>
      <el-select v-model="adapterFilter" class="model-toolbar__select" @change="applyFilters">
        <el-option label="全部适配器" value="ALL" />
        <el-option
          v-for="adapter in MODEL_ADAPTERS"
          :key="adapter.value"
          :label="adapter.label"
          :value="adapter.value"
        />
      </el-select>
      <div class="model-toolbar__spacer"></div>
      <span class="model-toolbar__count">共 {{ page.total }} 个模型</span>
      <div class="model-layout-toggle" aria-label="布局切换">
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
      :empty="!loading && !models.length"
      loading-text="正在加载模型配置"
      :empty-text="hasFilters ? '没有匹配的模型配置' : '尚未添加模型配置'"
      @retry="loadModels"
    >
      <div class="model-collection" :class="`model-collection--${layout}`">
        <ModelCard
          v-for="model in models"
          :key="String(model.id)"
          :model="model"
          :layout="layout"
          :testing="testingIds.has(String(model.id))"
          @edit="openEditDrawer"
          @test="testConnection"
          @toggle="toggleModel"
        />
      </div>
    </DataState>

    <footer v-if="page.total > 0" class="model-pagination">
      <span>共 {{ page.total }} 条</span>
      <el-pagination
        v-model:current-page="page.pageNum"
        v-model:page-size="page.pageSize"
        background
        layout="sizes, prev, pager, next, jumper"
        :page-sizes="[8, 16, 32, 64]"
        :total="page.total"
        @current-change="loadModels"
        @size-change="handlePageSizeChange"
      />
    </footer>

    <ModelConfigDrawer v-model:open="drawerOpen" :model="selectedModel" @saved="refreshAfterSave" />
  </section>
</template>

<script setup lang="ts">
import { Grid, List, Plus, Search } from '@element-plus/icons-vue'
import {
  ElAlert,
  ElButton,
  ElIcon,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
} from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'

import { normalizeApiError } from '@/api/errors'
import { searchModels, testSavedModel, updateModelStatus } from '@/features/model/api/model-api'
import ModelCard from '@/features/model/components/ModelCard.vue'
import ModelConfigDrawer from '@/features/model/components/ModelConfigDrawer.vue'
import { MODEL_ADAPTERS, MODEL_PROVIDERS } from '@/features/model/model-options'
import type { ModelConfig, ModelPage, ModelStatus } from '@/features/model/types'
import DataState from '@/shared/components/DataState.vue'
import PageHeader from '@/shared/components/PageHeader.vue'

const keyword = ref('')
const providerFilter = ref('ALL')
const statusFilter = ref<'ALL' | ModelStatus>('ALL')
const adapterFilter = ref('ALL')
const layout = ref<'grid' | 'list'>('grid')
const loading = ref(false)
const errorMessage = ref('')
const models = ref<ModelConfig[]>([])
const page = reactive<ModelPage>({ records: [], total: 0, pageNum: 1, pageSize: 8 })
const drawerOpen = ref(false)
const selectedModel = ref<ModelConfig | null>(null)
const testingIds = ref(new Set<string>())
let requestController: AbortController | null = null

const hasFilters = computed(() =>
  Boolean(
    keyword.value.trim() ||
    providerFilter.value !== 'ALL' ||
    statusFilter.value !== 'ALL' ||
    adapterFilter.value !== 'ALL',
  ),
)
onMounted(loadModels)
onBeforeUnmount(() => requestController?.abort())

async function loadModels(): Promise<void> {
  requestController?.abort()
  const controller = new AbortController()
  requestController = controller
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await searchModels({
      keyword: keyword.value.trim(),
      provider: providerFilter.value === 'ALL' ? undefined : providerFilter.value,
      status: statusFilter.value === 'ALL' ? undefined : statusFilter.value,
      adapterType: adapterFilter.value === 'ALL' ? undefined : adapterFilter.value,
      pageNum: page.pageNum,
      pageSize: page.pageSize,
      signal: controller.signal,
    })
    if (result.records.length === 0 && result.total > 0 && page.pageNum > 1) {
      page.pageNum = Math.max(1, Math.ceil(result.total / page.pageSize))
      await loadModels()
      return
    }
    models.value = result.records
    Object.assign(page, result)
  } catch (error) {
    if (!controller.signal.aborted) errorMessage.value = normalizeApiError(error).message
  } finally {
    if (requestController === controller) loading.value = false
  }
}

function applyFilters(): void {
  page.pageNum = 1
  void loadModels()
}

function handlePageSizeChange(): void {
  page.pageNum = 1
  void loadModels()
}

function refreshAfterSave(): void {
  page.pageNum = 1
  void loadModels()
}
function openCreateDrawer(): void {
  selectedModel.value = null
  drawerOpen.value = true
}
function openEditDrawer(model: ModelConfig): void {
  selectedModel.value = model
  drawerOpen.value = true
}

async function testConnection(model: ModelConfig): Promise<void> {
  const id = String(model.id)
  testingIds.value = new Set(testingIds.value).add(id)
  try {
    const result = await testSavedModel(model.id)
    ElMessage({
      type: result.connected ? 'success' : 'error',
      message: result.message || (result.connected ? '模型连接成功' : '模型连接失败'),
      duration: 3500,
      showClose: true,
    })
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    const next = new Set(testingIds.value)
    next.delete(id)
    testingIds.value = next
  }
}

async function toggleModel(model: ModelConfig): Promise<void> {
  const enabling = model.status === 'DISABLED'
  try {
    await ElMessageBox.confirm(
      enabling
        ? `确定启用“${model.displayName}”吗？`
        : `停用后，引用该模型的新任务将无法启动。确定继续吗？`,
      enabling ? '启用模型' : '停用模型',
      { type: enabling ? 'info' : 'warning', confirmButtonText: enabling ? '启用' : '停用' },
    )
    await updateModelStatus(model.id, enabling ? 1 : 0)
    ElMessage.success(enabling ? '模型已启用' : '模型已停用')
    await loadModels()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(normalizeApiError(error).message)
  }
}
</script>

<style scoped>
.model-page {
  display: grid;
  gap: var(--adw-space-5);
}
.model-page__breadcrumb {
  display: inline-block;
  margin-bottom: var(--adw-space-2);
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.model-toolbar {
  display: flex;
  align-items: center;
  gap: var(--adw-space-3);
  padding: var(--adw-space-4);
}
.model-toolbar__search {
  width: min(300px, 100%);
}
.model-toolbar__select {
  width: 160px;
}
.model-toolbar__spacer {
  flex: 1;
}
.model-toolbar__count {
  flex: 0 0 auto;
  color: var(--adw-text-secondary);
  font-size: 13px;
}
.model-toolbar__count b {
  color: var(--adw-color-success);
  font-weight: 500;
}
.model-layout-toggle {
  display: inline-flex;
  flex: 0 0 auto;
  overflow: hidden;
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
}
.model-layout-toggle button {
  display: inline-flex;
  width: 38px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border: 0;
  color: var(--adw-text-secondary);
  background: var(--adw-surface);
  cursor: pointer;
}
.model-layout-toggle button + button {
  border-left: 1px solid var(--adw-border-color);
}
.model-layout-toggle button.active {
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}
.model-collection {
  display: grid;
  gap: var(--adw-space-5);
}
.model-collection--grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.model-collection--list {
  grid-template-columns: 1fr;
}
.model-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--adw-text-secondary);
  font-size: 13px;
}
@media (max-width: 1280px) {
  .model-collection--grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 960px) {
  .model-collection--grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .model-toolbar {
    flex-wrap: wrap;
  }
  .model-toolbar__spacer {
    display: none;
  }
}
@media (max-width: 720px) {
  .model-collection--grid {
    grid-template-columns: 1fr;
  }
  .model-toolbar__search,
  .model-toolbar__select {
    width: 100%;
  }
  .model-toolbar__count {
    margin-left: 0;
  }
  .model-pagination {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--adw-space-3);
  }
}
</style>
