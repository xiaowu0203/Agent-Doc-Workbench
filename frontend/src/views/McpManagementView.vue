<template>
  <section class="mcp-page">
    <PageHeader title="外部 MCP 服务" description="管理空间共享的工具服务与 Agent 可用能力">
      <template #breadcrumb
        ><span class="mcp-page__breadcrumb">MCP 服务 / 外部 MCP 服务</span></template
      >
      <template #actions>
        <el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreateDrawer">
          添加 MCP 服务
        </el-button>
      </template>
    </PageHeader>

    <el-alert
      title="内置 Workbench MCP 始终启用；以下仅管理可选的外部 MCP 服务。"
      type="info"
      :closable="false"
      show-icon
    />

    <div class="mcp-toolbar surface-card">
      <el-input
        v-model="keyword"
        clearable
        class="mcp-toolbar__search"
        placeholder="搜索服务名称或 Server Key"
        aria-label="搜索 MCP 服务"
        @clear="applyFilters"
        @keyup.enter="applyFilters"
      >
        <template #prefix
          ><el-icon><Search /></el-icon
        ></template>
      </el-input>
      <el-select v-model="statusFilter" class="mcp-toolbar__select" @change="applyFilters">
        <el-option label="全部启停状态" value="ALL" />
        <el-option label="已启用" value="ENABLED" />
        <el-option label="已停用" value="DISABLED" />
      </el-select>
      <el-select v-model="authFilter" class="mcp-toolbar__select" @change="applyFilters">
        <el-option label="全部认证方式" value="ALL" />
        <el-option label="NONE" value="NONE" />
        <el-option label="BEARER" value="BEARER" />
        <el-option label="Query API Key" value="QUERY_PARAM" />
      </el-select>
      <div class="mcp-toolbar__spacer"></div>
      <div class="mcp-layout-toggle" aria-label="布局切换">
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
      <span class="mcp-toolbar__count">共 {{ page.total }} 个外部服务</span>
    </div>

    <DataState
      :loading="loading"
      :error="errorMessage"
      :empty="!loading && !servers.length"
      loading-text="正在加载 MCP 服务"
      :empty-text="hasFilters ? '没有匹配的 MCP 服务' : '当前空间还没有外部 MCP 服务'"
      @retry="loadServers"
    >
      <div class="mcp-collection" :class="`mcp-collection--${layout}`">
        <McpServerCard
          v-for="server in servers"
          :key="String(server.id)"
          :server="server"
          :layout="layout"
          :can-manage="canManage"
          :testing="testingIds.has(String(server.id))"
          @detail="openServerDrawer($event, canManage ? 'edit' : 'view')"
          @edit="openServerDrawer($event, 'edit')"
          @test="testConnection"
          @toggle="toggleServer"
          @delete="removeServer"
        />
      </div>
    </DataState>

    <footer v-if="page.total > 0" class="mcp-pagination">
      <span>共 {{ page.total }} 条</span>
      <el-pagination
        v-model:current-page="page.pageNum"
        v-model:page-size="page.pageSize"
        background
        layout="sizes, prev, pager, next, jumper"
        :page-sizes="[6, 12, 24, 48]"
        :total="page.total"
        @current-change="loadServers"
        @size-change="handlePageSizeChange"
      />
    </footer>

    <el-drawer
      v-model="drawerOpen"
      append-to-body
      size="min(620px, 94vw)"
      :title="drawerTitle"
      destroy-on-close
    >
      <div class="mcp-drawer" :aria-busy="detailLoading">
        <p v-if="detailLoading" class="mcp-drawer__loading">正在刷新服务详情与工具快照…</p>
        <el-form label-position="top" :disabled="drawerMode === 'view' || detailLoading">
          <el-form-item label="展示名称" required>
            <el-input v-model="form.displayName" maxlength="100" placeholder="例如：GitHub 工具" />
          </el-form-item>
          <el-form-item label="Server Key" required>
            <el-input
              v-model="form.serverKey"
              maxlength="50"
              placeholder="例如：github"
              :disabled="drawerMode !== 'create'"
            />
            <span class="mcp-form-hint"
              >创建后不可修改；使用小写 kebab-case，并作为工具命名前缀。</span
            >
          </el-form-item>
          <el-form-item label="Streamable HTTP 端点" required>
            <el-input
              v-model="form.endpointUrl"
              maxlength="500"
              placeholder="https://example.com/mcp"
            />
            <span class="mcp-form-hint">仅允许无用户信息、查询参数和片段的公网 HTTPS 地址。</span>
          </el-form-item>
          <el-form-item label="认证方式" required>
            <el-radio-group v-model="form.authType">
              <el-radio-button label="NONE">NONE</el-radio-button>
              <el-radio-button label="BEARER">BEARER</el-radio-button>
              <el-radio-button label="QUERY_PARAM">Query API Key</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-alert
            v-if="form.authType === 'NONE' && selectedServer?.authType !== 'NONE'"
            class="mcp-drawer__warning"
            title="保存后会清除当前已配置的 Bearer Token，此操作不能撤销。"
            type="warning"
            :closable="false"
            show-icon
          />
          <el-form-item v-if="form.authType === 'QUERY_PARAM'" label="Query 参数名" required>
            <el-input v-model="form.authParamName" maxlength="64" placeholder="例如：key" />
            <span class="mcp-form-hint">参数名以明文保存，参数值仍按秘密凭证加密存储。</span>
          </el-form-item>
          <el-form-item v-if="form.authType !== 'NONE'" :label="credentialLabel" required>
            <el-input
              v-model="form.authToken"
              type="password"
              show-password
              maxlength="4096"
              autocomplete="new-password"
              :placeholder="tokenPlaceholder"
            />
            <span class="mcp-form-hint"
              >凭证只写不回显；认证方式不变时，编辑留空表示保留现有凭证。</span
            >
          </el-form-item>
          <el-form-item v-if="drawerMode !== 'create'" label="服务状态">
            <el-switch
              v-model="form.enabled"
              inline-prompt
              active-text="启用"
              inactive-text="停用"
            />
          </el-form-item>
        </el-form>

        <template v-if="selectedServer">
          <el-divider />
          <section class="mcp-test-summary">
            <div>
              <span>连接测试</span>
              <el-tag :type="detailStatusTag.type" effect="plain">{{
                detailStatusTag.label
              }}</el-tag>
            </div>
            <dl>
              <div>
                <dt>最近测试</dt>
                <dd>{{ formatDate(selectedServer.lastTestedAt) }}</dd>
              </div>
              <div>
                <dt>测试耗时</dt>
                <dd>{{ formatDuration(selectedServer.lastTestDurationMs) }}</dd>
              </div>
              <div>
                <dt>配置版本</dt>
                <dd>v{{ selectedServer.configVersion }}</dd>
              </div>
              <div>
                <dt>工具快照</dt>
                <dd>{{ selectedServer.discoveredToolCount }} 个</dd>
              </div>
            </dl>
            <el-alert
              v-if="selectedServer.connectionStatus === 'FAILED'"
              :title="selectedServer.lastTestError || '连接测试失败'"
              type="error"
              :closable="false"
              show-icon
            />
          </section>

          <section class="mcp-tools">
            <div class="mcp-tools__heading">
              <div><strong>已发现工具</strong><span>最近一次成功测试保存的工具快照</span></div>
              <el-button text :loading="toolsLoading" @click="loadTools()">重新读取</el-button>
            </div>
            <el-empty
              v-if="!toolsLoading && !tools.length"
              description="尚未发现工具"
              :image-size="72"
            />
            <el-collapse v-else>
              <el-collapse-item v-for="tool in tools" :key="tool.name" :name="tool.name">
                <template #title
                  ><code>{{ tool.name }}</code></template
                >
                <p>{{ tool.description || '暂无工具描述' }}</p>
                <pre v-if="tool.inputSchema">{{ formatSchema(tool.inputSchema) }}</pre>
              </el-collapse-item>
            </el-collapse>
          </section>
        </template>
      </div>

      <template #footer>
        <el-button @click="drawerOpen = false">{{
          drawerMode === 'view' ? '关闭' : '取消'
        }}</el-button>
        <el-button
          v-if="selectedServer && canManage"
          :loading="testingIds.has(String(selectedServer.id))"
          :disabled="selectedServer.status === 0"
          @click="testConnection(selectedServer)"
          >测试连接</el-button
        >
        <el-button
          v-if="drawerMode !== 'view'"
          type="primary"
          :loading="saving"
          @click="saveServer"
        >
          保存
        </el-button>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { Grid, List, Plus, Search } from '@element-plus/icons-vue'
import {
  ElAlert,
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElDivider,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
  ElTag,
} from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  createMcpServer,
  deleteMcpServer,
  getMcpServer,
  listMcpTools,
  searchMcpServers,
  testMcpConnection,
  updateMcpServer,
} from '@/features/mcp/api/mcp-api'
import McpServerCard from '@/features/mcp/components/McpServerCard.vue'
import type { McpAuthType, McpServer, McpServerPage, McpTool } from '@/features/mcp/types'
import type { EntityId } from '@/features/workspace/types'
import DataState from '@/shared/components/DataState.vue'
import PageHeader from '@/shared/components/PageHeader.vue'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

type DrawerMode = 'create' | 'edit' | 'view'

const route = useRoute()
const workspaceStore = useWorkspaceStore()
const keyword = ref('')
const statusFilter = ref<'ALL' | 'ENABLED' | 'DISABLED'>('ALL')
const authFilter = ref<'ALL' | McpAuthType>('ALL')
const layout = ref<'grid' | 'list'>('grid')
const loading = ref(false)
const errorMessage = ref('')
const servers = ref<McpServer[]>([])
const page = reactive<McpServerPage>({ records: [], total: 0, pageNum: 1, pageSize: 12 })
const drawerOpen = ref(false)
const drawerMode = ref<DrawerMode>('create')
const detailLoading = ref(false)
const saving = ref(false)
const selectedServer = ref<McpServer | null>(null)
const tools = ref<McpTool[]>([])
const toolsLoading = ref(false)
const testingIds = ref(new Set<string>())
const form = reactive({
  serverKey: '',
  displayName: '',
  endpointUrl: '',
  authType: 'NONE' as McpAuthType,
  authParamName: '',
  authToken: '',
  enabled: true,
})
let requestController: AbortController | null = null

const spaceId = computed<EntityId>(() => String(route.params.spaceId))
const canManage = computed(() => workspaceStore.hasPermission(SPACE_PERMISSIONS.MCP_MANAGE))
const hasFilters = computed(
  () => Boolean(keyword.value.trim()) || statusFilter.value !== 'ALL' || authFilter.value !== 'ALL',
)
const drawerTitle = computed(() => {
  if (drawerMode.value === 'create') return '添加 MCP 服务'
  if (drawerMode.value === 'view') return '查看 MCP 服务'
  return '配置 MCP 服务'
})
const tokenPlaceholder = computed(() =>
  selectedServer.value?.authConfigured && selectedServer.value.authType === form.authType
    ? '留空表示保留现有凭证'
    : form.authType === 'QUERY_PARAM'
      ? '请输入 API Key'
      : '请输入 Bearer Token',
)
const credentialLabel = computed(() =>
  form.authType === 'QUERY_PARAM' ? 'Query API Key' : 'Bearer Token',
)
const detailStatusTag = computed(() => {
  if (selectedServer.value?.status === 0) return { type: 'info' as const, label: '已停用' }
  if (selectedServer.value?.connectionStatus === 'SUCCESS') {
    return { type: 'success' as const, label: '连接正常' }
  }
  if (selectedServer.value?.connectionStatus === 'FAILED') {
    return { type: 'danger' as const, label: '连接失败' }
  }
  return { type: 'warning' as const, label: '待测试' }
})

onMounted(loadServers)
onBeforeUnmount(() => requestController?.abort())
watch(spaceId, () => {
  page.pageNum = 1
  drawerOpen.value = false
  void loadServers()
})

async function loadServers(): Promise<void> {
  requestController?.abort()
  const controller = new AbortController()
  requestController = controller
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await searchMcpServers(spaceId.value, {
      keyword: keyword.value.trim(),
      status: statusFilter.value === 'ALL' ? undefined : statusFilter.value === 'ENABLED' ? 1 : 0,
      authType: authFilter.value === 'ALL' ? undefined : authFilter.value,
      pageNum: page.pageNum,
      pageSize: page.pageSize,
      signal: controller.signal,
    })
    servers.value = result.records
    Object.assign(page, result)
    syncSelectedServer()
  } catch (error) {
    if (controller.signal.aborted) return
    errorMessage.value = error instanceof Error ? error.message : 'MCP 服务列表加载失败'
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}

function applyFilters(): void {
  page.pageNum = 1
  void loadServers()
}

function handlePageSizeChange(): void {
  page.pageNum = 1
  void loadServers()
}

function resetForm(): void {
  Object.assign(form, {
    serverKey: '',
    displayName: '',
    endpointUrl: '',
    authType: 'NONE',
    authParamName: '',
    authToken: '',
    enabled: true,
  })
}

function fillForm(server: McpServer): void {
  Object.assign(form, {
    serverKey: server.serverKey,
    displayName: server.displayName,
    endpointUrl: server.endpointUrl,
    authType: server.authType,
    authParamName: server.authParamName || '',
    authToken: '',
    enabled: server.status === 1,
  })
}

function openCreateDrawer(): void {
  drawerMode.value = 'create'
  selectedServer.value = null
  tools.value = []
  resetForm()
  drawerOpen.value = true
}

async function openServerDrawer(server: McpServer, mode: DrawerMode): Promise<void> {
  drawerMode.value = mode
  selectedServer.value = server
  tools.value = []
  fillForm(server)
  drawerOpen.value = true
  detailLoading.value = true
  try {
    const [detail] = await Promise.all([getMcpServer(server.id), loadTools(server.id)])
    selectedServer.value = detail
    fillForm(detail)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'MCP 服务详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

async function loadTools(
  serverId: EntityId | null = selectedServer.value?.id ?? null,
): Promise<void> {
  if (!serverId) return
  toolsLoading.value = true
  try {
    tools.value = await listMcpTools(serverId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '工具快照加载失败')
  } finally {
    toolsLoading.value = false
  }
}

async function saveServer(): Promise<void> {
  const serverKey = form.serverKey.trim()
  const displayName = form.displayName.trim()
  let endpointUrl = form.endpointUrl.trim()
  let authToken = form.authToken.trim()
  if (!serverKey || !displayName || !endpointUrl) {
    ElMessage.warning('请完整填写 MCP 服务信息')
    return
  }
  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(serverKey)) {
    ElMessage.warning('Server Key 必须使用小写 kebab-case')
    return
  }
  if (!extractQueryCredential()) return
  endpointUrl = form.endpointUrl.trim()
  authToken = form.authToken.trim()
  if (!endpointUrl.startsWith('https://')) {
    ElMessage.warning('MCP 端点必须使用 HTTPS')
    return
  }
  if (form.authType === 'QUERY_PARAM' && !form.authParamName.trim()) {
    ElMessage.warning('Query API Key 认证必须填写参数名')
    return
  }
  const credentialCanBeReused =
    selectedServer.value?.authConfigured && selectedServer.value.authType === form.authType
  if (form.authType !== 'NONE' && !form.authToken.trim() && !credentialCanBeReused) {
    ElMessage.warning('所选认证方式必须填写凭证')
    return
  }
  if (selectedServer.value?.authType !== 'NONE' && form.authType === 'NONE') {
    try {
      await ElMessageBox.confirm('保存后会清除现有 Bearer Token，是否继续？', '清除认证凭证', {
        type: 'warning',
      })
    } catch (error) {
      if (error === 'cancel' || error === 'close') return
      throw error
    }
  }
  saving.value = true
  try {
    if (drawerMode.value === 'create') {
      await createMcpServer({
        spaceId: spaceId.value,
        serverKey,
        displayName,
        endpointUrl,
        authType: form.authType,
        authParamName: form.authType === 'QUERY_PARAM' ? form.authParamName.trim() : undefined,
        authToken: authToken || undefined,
      })
      ElMessage.success('MCP 服务已创建')
      page.pageNum = 1
    } else if (selectedServer.value) {
      await updateMcpServer(selectedServer.value.id, {
        displayName,
        endpointUrl,
        authType: form.authType,
        authParamName: form.authType === 'QUERY_PARAM' ? form.authParamName.trim() : undefined,
        authToken: authToken || undefined,
        status: form.enabled ? 1 : 0,
      })
      ElMessage.success('MCP 服务配置已更新')
    }
    drawerOpen.value = false
    await loadServers()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'MCP 服务保存失败')
  } finally {
    saving.value = false
  }
}

async function testConnection(server: McpServer): Promise<void> {
  setTesting(server.id, true)
  try {
    const result = await testMcpConnection(server.id)
    if (result.connected) {
      ElMessage.success(`连接成功，发现 ${result.tools.length} 个工具`)
      tools.value = result.tools
    } else {
      ElMessage.warning(result.errorMessage || 'MCP 连接测试失败')
    }
    await loadServers()
    if (selectedServer.value && String(selectedServer.value.id) === String(server.id)) {
      selectedServer.value = await getMcpServer(server.id)
      fillForm(selectedServer.value)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'MCP 连接测试失败')
  } finally {
    setTesting(server.id, false)
  }
}

async function toggleServer(server: McpServer): Promise<void> {
  const disabling = server.status === 1
  try {
    await ElMessageBox.confirm(
      disabling
        ? '停用后 Agent 不再加载此 MCP 服务，是否继续？'
        : '启用后 Agent 可继续使用此服务。',
      disabling ? '停用 MCP 服务' : '启用 MCP 服务',
      { type: disabling ? 'warning' : 'info' },
    )
    await updateMcpServer(server.id, {
      displayName: server.displayName,
      endpointUrl: server.endpointUrl,
      authType: server.authType,
      authParamName: server.authParamName || undefined,
      status: disabling ? 0 : 1,
    })
    ElMessage.success(disabling ? 'MCP 服务已停用' : 'MCP 服务已启用')
    await loadServers()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : 'MCP 服务状态更新失败')
  }
}

async function removeServer(server: McpServer): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除“${server.displayName}”吗？存在启用中的 Agent 绑定时服务端会拒绝删除。`,
      '删除 MCP 服务',
      { type: 'warning', confirmButtonText: '删除' },
    )
    await deleteMcpServer(server.id)
    ElMessage.success('MCP 服务已删除')
    if (servers.value.length === 1 && page.pageNum > 1) page.pageNum -= 1
    await loadServers()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : 'MCP 服务删除失败')
  }
}

function setTesting(serverId: EntityId, testing: boolean): void {
  const next = new Set(testingIds.value)
  if (testing) next.add(String(serverId))
  else next.delete(String(serverId))
  testingIds.value = next
}

function extractQueryCredential(): boolean {
  let url: URL
  try {
    url = new URL(form.endpointUrl.trim())
  } catch {
    ElMessage.warning('请输入合法的 MCP HTTPS 端点')
    return false
  }
  if (url.hash) {
    ElMessage.warning('MCP 端点不能包含 URL 片段')
    return false
  }
  const rawQuery = url.search.startsWith('?') ? url.search.slice(1) : ''
  if (!rawQuery) return true
  const separator = rawQuery.indexOf('=')
  if (rawQuery.includes('&') || separator <= 0 || separator === rawQuery.length - 1) {
    ElMessage.warning('端点 query 只能包含一个非空 API Key 参数')
    return false
  }
  let name: string
  let value: string
  try {
    name = decodeURIComponent(rawQuery.slice(0, separator))
    value = decodeURIComponent(rawQuery.slice(separator + 1))
  } catch {
    ElMessage.warning('端点 query 包含无效的 URL 编码')
    return false
  }
  form.endpointUrl = `${url.origin}${url.pathname}`
  form.authType = 'QUERY_PARAM'
  form.authParamName = name
  form.authToken = value
  ElMessage.info('已将 URL 中的 API Key 拆分为加密认证配置')
  return true
}

function syncSelectedServer(): void {
  if (!selectedServer.value) return
  selectedServer.value =
    servers.value.find((server) => String(server.id) === String(selectedServer.value?.id)) ??
    selectedServer.value
}

function formatDate(value: string | null): string {
  if (!value) return '尚未测试'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

function formatDuration(value: number | null): string {
  return value === null ? '—' : `${value} ms`
}

function formatSchema(schema: string): string {
  try {
    return JSON.stringify(JSON.parse(schema), null, 2)
  } catch {
    return schema
  }
}
</script>

<style scoped>
.mcp-page {
  display: grid;
  gap: var(--adw-space-6);
}
.mcp-page__breadcrumb {
  display: block;
  margin-bottom: var(--adw-space-3);
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.mcp-toolbar {
  display: flex;
  align-items: center;
  gap: var(--adw-space-3);
  padding: var(--adw-space-4);
}
.mcp-toolbar__search {
  width: min(330px, 32vw);
}
.mcp-toolbar__select {
  width: 170px;
}
.mcp-toolbar__spacer {
  flex: 1;
}
.mcp-toolbar__count {
  color: var(--adw-text-secondary);
  font-size: 13px;
  white-space: nowrap;
}
.mcp-layout-toggle {
  display: flex;
  gap: 4px;
}
.mcp-layout-toggle button {
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
.mcp-layout-toggle button.active {
  border-color: var(--adw-color-primary);
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}
.mcp-collection {
  display: grid;
  gap: var(--adw-space-4);
}
.mcp-collection--grid {
  grid-template-columns: repeat(auto-fill, minmax(330px, 1fr));
}
.mcp-collection--list {
  grid-template-columns: 1fr;
}
.mcp-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--adw-space-6);
  padding: var(--adw-space-2) 0;
  color: var(--adw-text-secondary);
  font-size: 13px;
}
.mcp-drawer {
  min-height: 220px;
}
.mcp-drawer__loading {
  margin: 0 0 var(--adw-space-4);
  color: var(--adw-text-tertiary);
  font-size: 13px;
}
.mcp-form-hint {
  margin-top: 6px;
  color: var(--adw-text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}
.mcp-drawer__warning {
  margin-bottom: var(--adw-space-4);
}
.mcp-test-summary > div:first-child {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.mcp-test-summary > div:first-child span {
  font-weight: 600;
}
.mcp-test-summary dl {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin: var(--adw-space-4) 0;
}
.mcp-test-summary dl div {
  display: grid;
  gap: 4px;
}
.mcp-test-summary dt {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.mcp-test-summary dd {
  margin: 0;
  color: var(--adw-text-primary);
}
.mcp-tools {
  margin-top: var(--adw-space-6);
}
.mcp-tools__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.mcp-tools__heading > div {
  display: grid;
  gap: 4px;
}
.mcp-tools__heading span {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.mcp-tools code {
  color: var(--adw-color-primary);
  font-size: 13px;
}
.mcp-tools p {
  color: var(--adw-text-secondary);
  line-height: 1.6;
}
.mcp-tools pre {
  max-height: 260px;
  overflow: auto;
  padding: 12px;
  border-radius: var(--adw-radius-sm);
  color: var(--adw-text-secondary);
  background: var(--adw-surface-muted);
  font-size: 12px;
  white-space: pre-wrap;
}
@media (max-width: 900px) {
  .mcp-toolbar {
    align-items: stretch;
    flex-wrap: wrap;
  }
  .mcp-toolbar__search {
    width: 100%;
  }
  .mcp-toolbar__spacer {
    display: none;
  }
}
@media (max-width: 620px) {
  .mcp-toolbar__select {
    width: calc(50% - 6px);
  }
  .mcp-pagination {
    align-items: flex-start;
    flex-direction: column;
  }
  .mcp-test-summary dl {
    grid-template-columns: 1fr;
  }
}
</style>
