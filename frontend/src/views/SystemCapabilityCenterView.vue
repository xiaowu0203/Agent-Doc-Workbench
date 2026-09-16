<template>
  <section class="capability-page">
    <PageHeader
      title="系统能力中心"
      description="集中维护平台可复用的 Skill、Agent 模板与 MCP 模板"
    >
      <template #breadcrumb
        ><span class="capability-page__breadcrumb">系统管理 / 系统能力中心</span></template
      >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="createDialogOpen = true"
          >新建系统能力</el-button
        >
      </template>
    </PageHeader>

    <div class="capability-statistics">
      <article
        v-for="stat in statisticCards"
        :key="stat.label"
        class="capability-statistics__card surface-card"
      >
        <span
          class="capability-statistics__icon"
          :class="`capability-statistics__icon--${stat.tone}`"
          ><el-icon><component :is="stat.icon" /></el-icon
        ></span>
        <div>
          <small>{{ stat.label }}</small
          ><strong>{{ stat.value }}</strong>
        </div>
      </article>
    </div>

    <div class="capability-panel surface-card">
      <div class="capability-tabs" role="tablist" aria-label="能力类型">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          type="button"
          :class="{ active: typeFilter === tab.value }"
          @click="setType(tab.value)"
        >
          {{ tab.label }} <span>{{ tab.count }}</span>
        </button>
      </div>
      <div class="capability-toolbar">
        <el-input
          v-model="keyword"
          clearable
          class="capability-toolbar__search"
          placeholder="搜索展示名称或技术标识"
          aria-label="搜索系统能力"
          @clear="applyFilters"
          @keyup.enter="applyFilters"
          ><template #prefix
            ><el-icon><Search /></el-icon></template
        ></el-input>
        <el-select v-model="statusFilter" class="capability-toolbar__select" @change="applyFilters"
          ><el-option label="全部状态" value="ALL" /><el-option
            label="已启用"
            :value="1" /><el-option label="已停用" :value="0"
        /></el-select>
        <el-button :icon="Refresh" circle aria-label="刷新系统能力" @click="loadCapabilities" />
        <div class="capability-layout-toggle" aria-label="布局切换">
          <button
            type="button"
            :class="{ active: layout === 'grid' }"
            aria-label="卡片布局"
            @click="layout = 'grid'"
          >
            <el-icon><Grid /></el-icon></button
          ><button
            type="button"
            :class="{ active: layout === 'list' }"
            aria-label="列表布局"
            @click="layout = 'list'"
          >
            <el-icon><List /></el-icon>
          </button>
        </div>
        <span class="capability-toolbar__count">共 {{ page.total }} 项能力</span>
      </div>

      <DataState
        :loading="loading"
        :error="errorMessage"
        :empty="!loading && !capabilities.length"
        loading-text="正在加载系统能力"
        :empty-text="hasFilters ? '没有匹配的系统能力' : '尚未创建系统能力'"
        @retry="loadCapabilities"
      >
        <div class="capability-collection" :class="`capability-collection--${layout}`">
          <SystemCapabilityCard
            v-for="capability in capabilities"
            :key="String(capability.id)"
            :capability="capability"
            :layout="layout"
            @detail="openDetail"
            @versions="openVersions"
            @edit="openMetadataDialog"
            @upload="chooseSkillVersionFile"
            @toggle="toggleCapability"
          />
        </div>
      </DataState>

      <footer v-if="page.total > 0" class="capability-pagination">
        <span>共 {{ page.total }} 项</span
        ><el-pagination
          v-model:current-page="page.pageNum"
          v-model:page-size="page.pageSize"
          background
          layout="sizes, prev, pager, next"
          :page-sizes="[8, 16, 32, 64]"
          :total="page.total"
          @current-change="loadCapabilities"
          @size-change="handlePageSizeChange"
        />
      </footer>
    </div>

    <el-drawer
      v-model="detailDialogOpen"
      :title="selectedCapability?.displayName || '能力详情'"
      size="min(680px, 94vw)"
      destroy-on-close
      ><el-descriptions v-if="selectedCapability" :column="1" border
        ><el-descriptions-item label="能力类型">{{
          typeLabel(selectedCapability.type)
        }}</el-descriptions-item
        ><el-descriptions-item label="技术标识">{{
          selectedCapability.technicalKey
        }}</el-descriptions-item
        ><el-descriptions-item label="状态">{{
          selectedCapability.status === 1 ? '已启用' : '已停用'
        }}</el-descriptions-item
        ><el-descriptions-item label="最新发布版本">{{
          selectedCapability.latestPublishedVersionNo === null
            ? '未发布'
            : `v${selectedCapability.latestPublishedVersionNo}`
        }}</el-descriptions-item
        ><el-descriptions-item label="空间安装"
          >{{ selectedCapability.installationCount }} 个空间</el-descriptions-item
        ><el-descriptions-item label="说明">{{
          selectedCapability.description || '—'
        }}</el-descriptions-item></el-descriptions
      >
      <section v-if="detailVersion" class="capability-detail-config">
        <h3>最新可用配置 · v{{ detailVersion.versionNo }}</h3>
        <el-descriptions v-if="selectedCapability?.type === 'AGENT_TEMPLATE'" :column="1" border>
          <el-descriptions-item label="版本展示名称">{{
            detailVersion.displayName || '—'
          }}</el-descriptions-item>
          <el-descriptions-item label="主模型 ID">{{
            detailVersion.modelId || '—'
          }}</el-descriptions-item>
          <el-descriptions-item label="Skill 选择模式">{{
            detailVersion.skillSelectionMode || '—'
          }}</el-descriptions-item>
          <el-descriptions-item label="系统 Skill"
            >{{ detailVersion.skills?.length || 0 }} 个固定版本</el-descriptions-item
          >
          <el-descriptions-item label="系统 MCP"
            >{{ detailVersion.mcps?.length || 0 }} 个固定版本</el-descriptions-item
          >
          <el-descriptions-item label="系统提示词">
            <pre>{{ detailVersion.systemPrompt || '—' }}</pre>
          </el-descriptions-item>
        </el-descriptions>
        <el-descriptions v-else-if="selectedCapability?.type === 'MCP_TEMPLATE'" :column="1" border>
          <el-descriptions-item label="版本展示名称">{{
            detailVersion.displayName || '—'
          }}</el-descriptions-item>
          <el-descriptions-item label="服务端点">{{
            detailVersion.endpointUrl || '—'
          }}</el-descriptions-item>
          <el-descriptions-item label="认证方式">{{
            detailVersion.authType || 'NONE'
          }}</el-descriptions-item>
          <el-descriptions-item v-if="detailVersion.authType === 'QUERY_PARAM'" label="参数名">{{
            detailVersion.authParamName || '—'
          }}</el-descriptions-item>
        </el-descriptions>
      </section>
      <section v-if="versions.length" class="capability-detail-versions">
        <h3>版本摘要</h3>
        <el-table :data="versions" max-height="360">
          <el-table-column prop="versionNo" label="版本" width="90">
            <template #default="scope">v{{ scope.row.versionNo }}</template>
          </el-table-column>
          <el-table-column prop="displayName" label="展示名称" min-width="150" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="scope">{{ versionStatus(scope.row.status) }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" min-width="160">
            <template #default="scope">{{ formatDate(scope.row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </section>
    </el-drawer>

    <el-dialog v-model="versionsDialogOpen" title="版本管理" width="min(760px, calc(100% - 32px))"
      ><div v-if="selectedCapability?.type !== 'SKILL'" class="capability-version-toolbar">
        <span>模板主体创建后，需要先创建并发布一个版本才能安装。</span>
        <el-button type="primary" @click="openVersionCreateDialog">新建草稿版本</el-button>
      </div>
      <DataState
        :loading="versionsLoading"
        :empty="!versionsLoading && !versions.length"
        empty-text="尚无版本记录"
        ><el-table :data="versions"
          ><el-table-column prop="versionNo" label="版本" width="100"
            ><template #default="scope">v{{ scope.row.versionNo }}</template></el-table-column
          ><el-table-column prop="displayName" label="展示名称" min-width="160" /><el-table-column
            prop="status"
            label="状态"
            width="110"
            ><template #default="scope">{{
              versionStatus(scope.row.status)
            }}</template></el-table-column
          ><el-table-column prop="createdAt" label="创建时间" min-width="160"
            ><template #default="scope">{{
              formatDate(scope.row.createdAt)
            }}</template></el-table-column
          ><el-table-column prop="publishedAt" label="发布时间" min-width="160"
            ><template #default="scope">{{
              formatDate(scope.row.publishedAt)
            }}</template></el-table-column
          ><el-table-column label="操作" width="250"
            ><template #default="scope"
              ><el-button
                v-if="isDraftVersion(scope.row.status)"
                type="primary"
                link
                @click="openVersionEditor(scope.row, false)"
                >编辑</el-button
              ><el-button
                v-if="isDraftVersion(scope.row.status)"
                type="primary"
                link
                @click="publishVersion(scope.row.id)"
                >发布</el-button
              ><el-button
                v-if="!isDraftVersion(scope.row.status)"
                type="primary"
                link
                @click="openVersionEditor(scope.row, true)"
                >查看并复制</el-button
              ><el-button
                v-if="isPublishedVersion(scope.row.status)"
                type="danger"
                link
                @click="setVersionEnabled(scope.row.id, false)"
                >停用</el-button
              ><el-button
                v-if="isDisabledVersion(scope.row.status)"
                type="success"
                link
                @click="setVersionEnabled(scope.row.id, true)"
                >恢复</el-button
              ></template
            ></el-table-column
          ></el-table
        ></DataState
      ></el-dialog
    >

    <AgentConfigDrawer
      :open="agentVersionDialogOpen"
      :agent-id="null"
      :template-id="selectedCapability?.type === 'AGENT_TEMPLATE' ? selectedCapability.id : null"
      :template-version-id="agentVersionEditingId"
      :template-version="agentVersionSource"
      mode="template-version"
      :space-id="0"
      :can-manage="true"
      :can-bind-skill="false"
      :can-bind-mcp="false"
      :can-read-skill="false"
      :can-read-mcp="false"
      @update:open="handleAgentVersionOpenChange"
      @saved="handleAgentVersionSaved"
    />

    <el-dialog
      v-model="mcpVersionDialogOpen"
      :title="
        mcpVersionEditingId
          ? '编辑 MCP 模板草稿'
          : mcpVersionSource
            ? '复制 MCP 模板版本'
            : '新建 MCP 模板版本'
      "
      width="min(680px, calc(100% - 32px))"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="版本展示名称" required>
          <el-input v-model="mcpVersionForm.displayName" maxlength="100" />
        </el-form-item>
        <el-form-item label="Streamable HTTP 端点" required>
          <el-input
            v-model="mcpVersionForm.endpointUrl"
            maxlength="500"
            placeholder="https://example.com/mcp"
          />
          <span class="capability-form-hint"
            >仅保存无凭证的公网 HTTPS 地址；空间安装时单独填写凭证。</span
          >
        </el-form-item>
        <el-form-item label="认证方式" required>
          <el-radio-group v-model="mcpVersionForm.authType">
            <el-radio value="NONE">NONE</el-radio>
            <el-radio value="BEARER">BEARER</el-radio>
            <el-radio value="QUERY_PARAM">Query API Key</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="mcpVersionForm.authType === 'QUERY_PARAM'"
          label="Query 参数名"
          required
        >
          <el-input v-model="mcpVersionForm.authParamName" maxlength="64" placeholder="例如：key" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mcpVersionDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="mcpVersionSaving" @click="saveMcpVersion"
          >保存草稿版本</el-button
        >
      </template>
    </el-dialog>

    <SkillDetailDrawer
      v-if="selectedSystemSkill"
      :open="skillDetailOpen"
      :skill="selectedSystemSkill"
      :can-manage="true"
      scope="system"
      :initial-tab="skillDetailInitialTab"
      @close="skillDetailOpen = false"
      @edit="openSystemSkillMetadataDialog"
      @refresh="refreshSystemSkillDetail"
    />

    <el-dialog
      v-model="createDialogOpen"
      title="新建系统能力"
      width="min(920px, calc(100% - 32px))"
      @closed="resetCreateForm"
      ><el-form ref="createFormRef" :model="createForm" :rules="createRules" label-position="top"
        ><el-form-item label="能力类型" prop="type"
          ><el-radio-group v-model="createForm.type"
            ><el-radio value="SKILL">Skill</el-radio
            ><el-radio value="AGENT_TEMPLATE">Agent 模板</el-radio
            ><el-radio value="MCP_TEMPLATE">MCP 模板</el-radio></el-radio-group
          ></el-form-item
        >
        <div v-if="createForm.type === 'SKILL'" class="capability-skill-entry">
          <el-button
            :type="skillCreationMode === 'upload' ? 'primary' : 'default'"
            @click="skillCreationMode = 'upload'"
            >上传 Skill ZIP</el-button
          >
          <el-button
            :type="skillCreationMode === 'online' ? 'primary' : 'default'"
            @click="skillCreationMode = 'online'"
            >新建 Skill</el-button
          >
        </div>
        <el-form-item
          v-if="createForm.type !== 'SKILL' || skillCreationMode === 'online'"
          label="技术标识"
          prop="technicalKey"
          ><el-input
            v-model="createForm.technicalKey"
            placeholder="如 document-review" /></el-form-item
        ><el-form-item
          v-if="createForm.type !== 'SKILL' || skillCreationMode === 'online'"
          label="展示名称"
          prop="displayName"
          ><el-input v-model="createForm.displayName" /></el-form-item
        ><el-form-item
          v-if="createForm.type !== 'SKILL' || skillCreationMode === 'online'"
          label="说明"
          prop="description"
          ><el-input v-model="createForm.description" type="textarea" :rows="3" /></el-form-item
        ><el-form-item
          v-if="createForm.type === 'SKILL' && skillCreationMode === 'upload'"
          label="Skill ZIP 版本包"
          required
          ><input
            ref="skillPackageInput"
            class="capability-create-file"
            type="file"
            accept=".zip,application/zip"
            @change="selectSkillPackage"
          />
          <p class="capability-create-file-tip">
            {{
              skillPackage
                ? `已选择：${skillPackage.name}`
                : '必须包含 SKILL.md；服务端会执行完整的安全校验。'
            }}
          </p></el-form-item
        ><SkillPackageBuilder
          v-if="createForm.type === 'SKILL' && skillCreationMode === 'online'"
          ref="createPackageBuilder"
          :skill-name="createForm.technicalKey"
        />
      </el-form>
      <p class="capability-create-tip">
        {{
          createForm.type === 'SKILL'
            ? '系统 Skill 会原子创建主体与首个草稿版本。'
            : '创建主体后，请在“版本管理”中补充并发布可安装的版本。'
        }}
      </p>
      <template #footer
        ><el-button @click="createDialogOpen = false">取消</el-button
        ><el-button type="primary" :loading="creating" @click="submitCreate"
          >创建</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="metadataDialogOpen"
      title="编辑 Skill 元数据"
      width="min(560px, calc(100% - 32px))"
    >
      <el-form v-if="editingSkill" label-position="top">
        <el-form-item label="展示名称" required>
          <el-input v-model="editingSkill.displayName" maxlength="100" />
        </el-form-item>
        <el-form-item label="技术标识" required>
          <el-input
            v-model="editingSkill.technicalKey"
            maxlength="100"
            :disabled="Boolean(editingSkill.skillVersionCount)"
          />
          <span class="capability-form-hint"
            >创建任意版本后不可修改，并须与 ZIP 中的 SKILL.md.name 一致。</span
          >
        </el-form-item>
        <el-form-item label="管理描述" required>
          <el-input
            v-model="editingSkill.description"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="metadataDialogOpen = false">取消</el-button>
        <el-button type="primary" @click="saveSkillMetadata">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog
      v-model="templateMetadataDialogOpen"
      title="编辑系统能力元数据"
      width="min(560px, calc(100% - 32px))"
    >
      <el-form v-if="editingTemplate" label-position="top">
        <el-form-item label="技术标识"
          ><el-input :model-value="editingTemplate.technicalKey" disabled
        /></el-form-item>
        <el-form-item label="展示名称" required
          ><el-input v-model="templateMetadataForm.displayName" maxlength="100"
        /></el-form-item>
        <el-form-item label="管理描述"
          ><el-input
            v-model="templateMetadataForm.description"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateMetadataDialogOpen = false">取消</el-button>
        <el-button type="primary" @click="saveTemplateMetadata">保存</el-button>
      </template>
    </el-dialog>
    <input
      ref="versionFileInput"
      class="visually-hidden"
      type="file"
      accept=".zip,application/zip"
      @change="handleSkillVersionFile"
    />
  </section>
</template>

<script setup lang="ts">
import {
  Collection,
  Connection,
  Cpu,
  Grid,
  List,
  Plus,
  Refresh,
  Search,
} from '@element-plus/icons-vue'
import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'

import { normalizeApiError } from '@/api/errors'
import {
  createSystemCapability,
  createMcpTemplateVersion,
  disableAgentTemplateVersion,
  enableAgentTemplateVersion,
  getSystemCapabilityStatistics,
  importSystemSkillPackage,
  listCapabilityVersions,
  publishAgentTemplateVersion,
  publishMcpTemplateVersion,
  searchSystemCapabilities,
  setSystemSkillStatus,
  setMcpTemplateVersionEnabled,
  updateMcpTemplateVersion,
  updateSystemCapabilityTemplate,
  updateSystemSkill,
  uploadSystemSkillVersion,
} from '@/features/system-capability/api/system-capability-api'
import AgentConfigDrawer from '@/features/agent/components/AgentConfigDrawer.vue'
import SystemCapabilityCard from '@/features/system-capability/components/SystemCapabilityCard.vue'
import SkillPackageBuilder from '@/features/skill/components/SkillPackageBuilder.vue'
import SkillDetailDrawer from '@/features/skill/components/SkillDetailDrawer.vue'
import { toSystemSkill } from '@/features/system-capability/skill-adapter'
import type { Skill } from '@/features/skill/types'
import type { McpAuthType } from '@/features/mcp/types'
import type {
  CapabilityVersion,
  SystemCapability,
  SystemCapabilityPage,
  SystemCapabilityStatistics,
  SystemCapabilityType,
  TemplateVersionStatus,
} from '@/features/system-capability/types'
import DataState from '@/shared/components/DataState.vue'
import PageHeader from '@/shared/components/PageHeader.vue'

const keyword = ref('')
const typeFilter = ref<SystemCapabilityType>('SKILL')
const statusFilter = ref<0 | 1 | 'ALL'>('ALL')
const layout = ref<'grid' | 'list'>('grid')
const loading = ref(false)
const errorMessage = ref('')
const capabilities = ref<SystemCapability[]>([])
const statistics = ref<SystemCapabilityStatistics | null>(null)
const page = reactive<SystemCapabilityPage>({ records: [], total: 0, pageNum: 1, pageSize: 8 })
const detailDialogOpen = ref(false)
const versionsDialogOpen = ref(false)
const skillDetailOpen = ref(false)
const skillDetailInitialTab = ref<'overview' | 'versions'>('overview')
const selectedCapability = ref<SystemCapability | null>(null)
const versions = ref<CapabilityVersion[]>([])
const detailVersion = computed(
  () =>
    versions.value.find((version) => isPublishedVersion(version.status)) ||
    versions.value[0] ||
    null,
)
const versionsLoading = ref(false)
const agentVersionDialogOpen = ref(false)
const agentVersionEditingId = ref<string | number | null>(null)
const agentVersionSource = ref<CapabilityVersion | null>(null)
const mcpVersionDialogOpen = ref(false)
const mcpVersionEditingId = ref<string | number | null>(null)
const mcpVersionSource = ref<CapabilityVersion | null>(null)
const mcpVersionSaving = ref(false)
const mcpVersionForm = reactive({
  displayName: '',
  endpointUrl: '',
  authType: 'NONE' as McpAuthType,
  authParamName: '',
})
const createDialogOpen = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInstance>()
const skillPackageInput = ref<HTMLInputElement>()
const skillPackage = ref<File | null>(null)
const skillCreationMode = ref<'upload' | 'online'>('upload')
const createPackageBuilder = ref<{ buildPackage: () => Promise<File>; reset: () => void } | null>(
  null,
)
const editingSkill = ref<SystemCapability | null>(null)
const metadataDialogOpen = ref(false)
const editingTemplate = ref<SystemCapability | null>(null)
const templateMetadataDialogOpen = ref(false)
const templateMetadataForm = reactive({ displayName: '', description: '' })
const uploadingVersionFor = ref<SystemCapability | null>(null)
const versionFileInput = ref<HTMLInputElement>()
const createForm = reactive<{
  type: SystemCapabilityType
  technicalKey: string
  displayName: string
  description: string
}>({ type: 'SKILL', technicalKey: '', displayName: '', description: '' })
let requestController: AbortController | null = null

const selectedSystemSkill = computed<Skill | null>(() => {
  const capability = selectedCapability.value
  return capability?.type === 'SKILL' ? toSystemSkill(capability) : null
})

const createRules: FormRules = {
  technicalKey: [
    { required: true, message: '请输入技术标识', trigger: 'blur' },
    {
      pattern: /^[a-z0-9]+(?:-[a-z0-9]+)*$/,
      message: '使用小写字母、数字和连字符',
      trigger: 'blur',
    },
  ],
  displayName: [{ required: true, message: '请输入展示名称', trigger: 'blur' }],
  description: [{ required: true, message: '请输入能力说明', trigger: 'blur' }],
}
const hasFilters = computed(() => keyword.value.trim() !== '' || statusFilter.value !== 'ALL')
const tabs = computed(() =>
  (['SKILL', 'AGENT_TEMPLATE', 'MCP_TEMPLATE'] as const).map((type) => ({
    value: type,
    label: typeLabel(type),
    count: statistics.value?.byType.find((item) => item.type === type)?.totalCount ?? 0,
  })),
)
const statisticCards = computed(() => [
  {
    label: '系统能力总数',
    value: statistics.value?.totalCount ?? '—',
    icon: Collection,
    tone: 'primary',
  },
  {
    label: '已启用主体',
    value: statistics.value?.enabledCount ?? '—',
    icon: Refresh,
    tone: 'success',
  },
  {
    label: '已发布版本',
    value: statistics.value?.publishedVersionCount ?? '—',
    icon: Cpu,
    tone: 'violet',
  },
  {
    label: '空间安装实例',
    value: statistics.value?.installationCount ?? '—',
    icon: Connection,
    tone: 'cyan',
  },
])

onMounted(() => {
  void Promise.all([loadCapabilities(), loadStatistics()])
})
onBeforeUnmount(() => requestController?.abort())

async function loadCapabilities(): Promise<void> {
  requestController?.abort()
  const controller = new AbortController()
  requestController = controller
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await searchSystemCapabilities({
      type: typeFilter.value,
      status: statusFilter.value === 'ALL' ? undefined : statusFilter.value,
      keyword: keyword.value.trim(),
      pageNum: page.pageNum,
      pageSize: page.pageSize,
      signal: controller.signal,
    })
    if (result.records.length === 0 && result.total > 0 && page.pageNum > 1) {
      page.pageNum = Math.max(1, Math.ceil(result.total / page.pageSize))
      await loadCapabilities()
      return
    }
    capabilities.value = result.records
    Object.assign(page, result)
  } catch (error) {
    if (!controller.signal.aborted) errorMessage.value = normalizeApiError(error).message
  } finally {
    if (requestController === controller) loading.value = false
  }
}
async function loadStatistics(): Promise<void> {
  try {
    statistics.value = await getSystemCapabilityStatistics()
  } catch {
    statistics.value = null
  }
}
function setType(type: SystemCapabilityType): void {
  typeFilter.value = type
  applyFilters()
}
function applyFilters(): void {
  page.pageNum = 1
  void loadCapabilities()
}
function handlePageSizeChange(): void {
  page.pageNum = 1
  void loadCapabilities()
}
async function openDetail(capability: SystemCapability): Promise<void> {
  selectedCapability.value = capability
  if (capability.type === 'SKILL') {
    detailDialogOpen.value = false
    versionsDialogOpen.value = false
    skillDetailInitialTab.value = 'overview'
    skillDetailOpen.value = true
    return
  }
  versions.value = []
  detailDialogOpen.value = true
  await loadCapabilityVersions(capability)
}
async function openVersions(capability: SystemCapability): Promise<void> {
  selectedCapability.value = capability
  if (capability.type === 'SKILL') {
    versionsDialogOpen.value = false
    skillDetailInitialTab.value = 'versions'
    skillDetailOpen.value = true
    return
  }
  versions.value = []
  versionsDialogOpen.value = true
  await loadCapabilityVersions(capability)
}
function openAgentVersionDialog(): void {
  if (selectedCapability.value?.type === 'AGENT_TEMPLATE') {
    agentVersionEditingId.value = null
    agentVersionSource.value = null
    agentVersionDialogOpen.value = true
  }
}
function openVersionCreateDialog(): void {
  if (selectedCapability.value?.type === 'AGENT_TEMPLATE') {
    openAgentVersionDialog()
    return
  }
  if (selectedCapability.value?.type === 'MCP_TEMPLATE') openMcpVersionEditor(null, false)
}
function openAgentVersionEditor(versionId: string | number, copy: boolean): void {
  if (selectedCapability.value?.type !== 'AGENT_TEMPLATE') return
  const version = versions.value.find((item) => String(item.id) === String(versionId))
  if (!version) return
  agentVersionEditingId.value = copy ? null : version.id
  agentVersionSource.value = version
  agentVersionDialogOpen.value = true
}
function openVersionEditor(value: unknown, copy: boolean): void {
  const version = value as CapabilityVersion
  if (selectedCapability.value?.type === 'AGENT_TEMPLATE') {
    openAgentVersionEditor(version.id, copy)
    return
  }
  if (selectedCapability.value?.type === 'MCP_TEMPLATE') openMcpVersionEditor(version, copy)
}
function openMcpVersionEditor(version: CapabilityVersion | null, copy: boolean): void {
  mcpVersionEditingId.value = version && !copy ? version.id : null
  mcpVersionSource.value = version
  Object.assign(mcpVersionForm, {
    displayName: version?.displayName || selectedCapability.value?.displayName || '',
    endpointUrl: version?.endpointUrl || '',
    authType: version?.authType || 'NONE',
    authParamName: version?.authParamName || '',
  })
  mcpVersionDialogOpen.value = true
}
function handleAgentVersionOpenChange(open: boolean): void {
  agentVersionDialogOpen.value = open
  if (!open) {
    agentVersionEditingId.value = null
    agentVersionSource.value = null
  }
}
function isDraftVersion(status: TemplateVersionStatus): boolean {
  return status === 0 || status === 'DRAFT'
}
function isPublishedVersion(status: TemplateVersionStatus): boolean {
  return status === 1 || status === 'PUBLISHED'
}
function isDisabledVersion(status: TemplateVersionStatus): boolean {
  return status === 2 || status === 'DISABLED'
}

async function publishVersion(versionId: string | number): Promise<void> {
  try {
    if (selectedCapability.value?.type === 'AGENT_TEMPLATE') {
      await publishAgentTemplateVersion(versionId)
    } else if (selectedCapability.value?.type === 'MCP_TEMPLATE') {
      await publishMcpTemplateVersion(versionId)
    }
    ElMessage.success('模板版本已发布')
    await refreshSelectedVersions()
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  }
}

async function setVersionEnabled(versionId: string | number, enabled: boolean): Promise<void> {
  const action = enabled ? '恢复' : '停用'
  try {
    await ElMessageBox.confirm(
      enabled
        ? '恢复后该版本可重新用于安装和升级。'
        : '停用后不能用于新的安装或升级，已安装实例不受影响。',
      `${action}模板版本`,
      { type: enabled ? 'info' : 'warning' },
    )
    if (selectedCapability.value?.type === 'AGENT_TEMPLATE') {
      if (enabled) await enableAgentTemplateVersion(versionId)
      else await disableAgentTemplateVersion(versionId)
    } else if (selectedCapability.value?.type === 'MCP_TEMPLATE') {
      await setMcpTemplateVersionEnabled(versionId, enabled)
    }
    ElMessage.success(`模板版本已${action}`)
    await refreshSelectedVersions()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(normalizeApiError(error).message)
  }
}

async function refreshSelectedVersions(): Promise<void> {
  if (!selectedCapability.value) return
  await Promise.all([
    loadCapabilityVersions(selectedCapability.value),
    loadCapabilities(),
    loadStatistics(),
  ])
}

async function saveMcpVersion(): Promise<void> {
  const payload = {
    displayName: mcpVersionForm.displayName.trim(),
    endpointUrl: mcpVersionForm.endpointUrl.trim(),
    authType: mcpVersionForm.authType,
    authParamName:
      mcpVersionForm.authType === 'QUERY_PARAM' ? mcpVersionForm.authParamName.trim() : undefined,
  }
  if (!payload.displayName || !payload.endpointUrl) {
    ElMessage.warning('请完整填写 MCP 模板版本信息')
    return
  }
  if (!payload.endpointUrl.startsWith('https://')) {
    ElMessage.warning('MCP 端点必须使用 HTTPS')
    return
  }
  if (payload.authType === 'QUERY_PARAM' && !payload.authParamName) {
    ElMessage.warning('Query API Key 认证必须填写参数名')
    return
  }
  if (selectedCapability.value?.type !== 'MCP_TEMPLATE') return
  mcpVersionSaving.value = true
  try {
    if (mcpVersionEditingId.value) {
      await updateMcpTemplateVersion(mcpVersionEditingId.value, payload)
    } else {
      await createMcpTemplateVersion(selectedCapability.value.id, payload)
    }
    ElMessage.success('MCP 模板草稿版本已保存')
    mcpVersionDialogOpen.value = false
    await refreshSelectedVersions()
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    mcpVersionSaving.value = false
  }
}
async function handleAgentVersionSaved(): Promise<void> {
  const template = selectedCapability.value
  if (template?.type !== 'AGENT_TEMPLATE') return
  await Promise.all([loadCapabilityVersions(template), loadCapabilities(), loadStatistics()])
}
async function loadCapabilityVersions(capability: SystemCapability): Promise<void> {
  versionsLoading.value = true
  try {
    versions.value = await listCapabilityVersions(capability.type, capability.id)
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    versionsLoading.value = false
  }
}
function openSystemSkillMetadataDialog(skill: Skill): void {
  const capability = capabilities.value.find((item) => String(item.id) === String(skill.id))
  if (capability) openSkillMetadataDialog(capability)
}
async function refreshSystemSkillDetail(): Promise<void> {
  await Promise.all([loadCapabilities(), loadStatistics()])
  if (selectedCapability.value) {
    selectedCapability.value =
      capabilities.value.find((item) => String(item.id) === String(selectedCapability.value?.id)) ??
      selectedCapability.value
  }
}
async function submitCreate(): Promise<void> {
  if (createForm.type !== 'SKILL' || skillCreationMode.value === 'online') {
    const valid = await createFormRef.value?.validate().catch(() => false)
    if (!valid) return
  }
  if (createForm.type === 'SKILL' && skillCreationMode.value === 'upload' && !skillPackage.value) {
    ElMessage.warning('请上传 Skill ZIP 版本包')
    return
  }
  creating.value = true
  const shouldConfigureAgentVersion = createForm.type === 'AGENT_TEMPLATE'
  try {
    if (createForm.type === 'SKILL') {
      const file =
        skillCreationMode.value === 'online'
          ? await createPackageBuilder.value?.buildPackage()
          : skillPackage.value
      if (!file) return
      await importSystemSkillPackage(
        file,
        skillCreationMode.value === 'online'
          ? { displayName: createForm.displayName, description: createForm.description }
          : undefined,
      )
      ElMessage.success('系统 Skill 与首个草稿版本已创建')
    } else {
      const created = await createSystemCapability(createForm.type, createForm)
      ElMessage.success('系统能力主体已创建，请继续创建并发布版本')
      if (createForm.type === 'AGENT_TEMPLATE') {
        selectedCapability.value = {
          id: created.id,
          type: 'AGENT_TEMPLATE',
          technicalKey: createForm.technicalKey,
          displayName: createForm.displayName,
          description: createForm.description,
          status: 1,
          latestPublishedVersionId: null,
          latestPublishedVersionNo: null,
          installationCount: 0,
          skillVersionCount: null,
          skillBoundAgentCount: null,
          latestSkillVersionNo: null,
          latestSkillVersionStatus: null,
          latestSkillActivationDescription: null,
          latestSkillAllowedToolCount: null,
          latestSkillVersionCreatedAt: null,
          latestSkillVersionPublishedAt: null,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        }
      }
    }
    createDialogOpen.value = false
    page.pageNum = 1
    await Promise.all([loadCapabilities(), loadStatistics()])
    if (shouldConfigureAgentVersion) await openAgentVersionDialog()
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    creating.value = false
  }
}
function resetCreateForm(): void {
  createForm.type = 'SKILL'
  createForm.technicalKey = ''
  createForm.displayName = ''
  createForm.description = ''
  skillPackage.value = null
  skillCreationMode.value = 'upload'
  createPackageBuilder.value?.reset()
  if (skillPackageInput.value) skillPackageInput.value.value = ''
  createFormRef.value?.clearValidate()
}
function selectSkillPackage(event: Event): void {
  const input = event.target as HTMLInputElement
  skillPackage.value = input.files?.[0] ?? null
}

function openSkillMetadataDialog(capability: SystemCapability): void {
  editingSkill.value = { ...capability }
  metadataDialogOpen.value = true
}

function openMetadataDialog(capability: SystemCapability): void {
  if (capability.type === 'SKILL') {
    openSkillMetadataDialog(capability)
    return
  }
  editingTemplate.value = capability
  Object.assign(templateMetadataForm, {
    displayName: capability.displayName,
    description: capability.description || '',
  })
  templateMetadataDialogOpen.value = true
}

async function saveTemplateMetadata(): Promise<void> {
  if (!editingTemplate.value || editingTemplate.value.type === 'SKILL') return
  const displayName = templateMetadataForm.displayName.trim()
  if (!displayName) {
    ElMessage.warning('请填写展示名称')
    return
  }
  try {
    await updateSystemCapabilityTemplate(editingTemplate.value.type, editingTemplate.value.id, {
      displayName,
      description: templateMetadataForm.description.trim() || undefined,
      status: editingTemplate.value.status,
    })
    templateMetadataDialogOpen.value = false
    ElMessage.success('系统能力元数据已更新')
    await Promise.all([loadCapabilities(), loadStatistics()])
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  }
}

function chooseSkillVersionFile(capability: SystemCapability): void {
  uploadingVersionFor.value = capability
  versionFileInput.value?.click()
}

async function handleSkillVersionFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !uploadingVersionFor.value) return
  try {
    await uploadSystemSkillVersion(uploadingVersionFor.value.id, file)
    ElMessage.success('系统 Skill 草稿版本上传成功')
    await loadCapabilities()
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    uploadingVersionFor.value = null
  }
}

async function saveSkillMetadata(): Promise<void> {
  if (!editingSkill.value) return
  try {
    await updateSystemSkill(editingSkill.value.id, {
      technicalKey: editingSkill.value.technicalKey,
      displayName: editingSkill.value.displayName,
      description: editingSkill.value.description || '',
    })
    metadataDialogOpen.value = false
    ElMessage.success('系统 Skill 元数据已更新')
    await loadCapabilities()
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  }
}

async function toggleCapability(capability: SystemCapability): Promise<void> {
  const enabling = capability.status === 0
  try {
    await ElMessageBox.confirm(
      enabling
        ? '启用后可继续发布版本和执行新的空间安装。'
        : '停用后不能发布版本或执行新的空间安装，已安装实例不受影响。',
      `${enabling ? '启用' : '停用'}${typeLabel(capability.type)}`,
      { type: enabling ? 'info' : 'warning' },
    )
    if (capability.type === 'SKILL') {
      await setSystemSkillStatus(capability.id, enabling)
    } else {
      await updateSystemCapabilityTemplate(capability.type, capability.id, {
        displayName: capability.displayName,
        description: capability.description || undefined,
        status: enabling ? 1 : 0,
      })
    }
    ElMessage.success(`系统能力已${enabling ? '启用' : '停用'}`)
    await Promise.all([loadCapabilities(), loadStatistics()])
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(normalizeApiError(error).message)
  }
}
function typeLabel(type: SystemCapabilityType): string {
  return { SKILL: 'Skill', AGENT_TEMPLATE: 'Agent 模板', MCP_TEMPLATE: 'MCP 模板' }[type]
}
function versionStatus(status: TemplateVersionStatus): string {
  if (status === 2 || status === 'DISABLED') return '已停用'
  return status === 1 || status === 'PUBLISHED' ? '已发布' : '草稿'
}
function formatDate(value?: string | null): string {
  return value
    ? new Intl.DateTimeFormat('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
      }).format(new Date(value))
    : '—'
}
</script>

<style scoped>
.capability-page {
  display: grid;
  gap: var(--adw-space-5);
}
.capability-page__breadcrumb {
  display: inline-block;
  margin-bottom: var(--adw-space-2);
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.capability-statistics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--adw-space-4);
}
.capability-statistics__card {
  display: flex;
  align-items: center;
  gap: var(--adw-space-4);
  padding: var(--adw-space-5);
}
.capability-statistics__icon {
  display: inline-flex;
  width: 48px;
  height: 48px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 24px;
}
.capability-statistics__icon--primary {
  color: #1769e8;
  background: #e3efff;
}
.capability-statistics__icon--success {
  color: #08a35d;
  background: #dcf8e9;
}
.capability-statistics__icon--violet {
  color: #6246ea;
  background: #eeeaff;
}
.capability-statistics__icon--cyan {
  color: #0897af;
  background: #dff8fa;
}
.capability-statistics small {
  display: block;
  color: var(--adw-text-secondary);
  font-size: 13px;
}
.capability-statistics strong {
  display: block;
  margin-top: 5px;
  color: var(--adw-text-primary);
  font-size: 28px;
  line-height: 1;
}
.capability-panel {
  display: grid;
  gap: var(--adw-space-4);
  padding: var(--adw-space-4);
}
.capability-tabs {
  display: flex;
  gap: var(--adw-space-2);
  border-bottom: 1px solid var(--adw-border-color-light);
}
.capability-tabs button {
  padding: 10px 20px;
  border: 0;
  border-radius: var(--adw-radius-sm) var(--adw-radius-sm) 0 0;
  color: var(--adw-text-secondary);
  background: transparent;
  cursor: pointer;
  font: inherit;
}
.capability-tabs button.active {
  color: #fff;
  background: var(--adw-color-primary);
}
.capability-tabs span {
  margin-left: 8px;
}
.capability-toolbar {
  display: flex;
  align-items: center;
  gap: var(--adw-space-3);
}
.capability-toolbar__search {
  width: min(300px, 100%);
}
.capability-toolbar__select {
  width: 150px;
}
.capability-toolbar__count {
  margin-left: auto;
  color: var(--adw-text-secondary);
  font-size: 13px;
}
.capability-layout-toggle {
  display: inline-flex;
  overflow: hidden;
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
}
.capability-layout-toggle button {
  display: inline-flex;
  width: 36px;
  height: 32px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-left: 1px solid var(--adw-border-color);
  background: var(--adw-surface);
  color: var(--adw-text-secondary);
  cursor: pointer;
}
.capability-layout-toggle button:first-child {
  border-left: 0;
}
.capability-layout-toggle button.active {
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}
.capability-collection {
  display: grid;
  gap: var(--adw-space-4);
}
.capability-collection--grid {
  grid-template-columns: repeat(auto-fill, minmax(285px, 1fr));
}
.capability-collection--list {
  grid-template-columns: 1fr;
}
.capability-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: var(--adw-space-2);
  color: var(--adw-text-secondary);
  font-size: 13px;
}
.capability-create-tip {
  margin: 0;
  color: var(--adw-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.capability-create-file {
  width: 100%;
}
.capability-create-file-tip {
  margin: var(--adw-space-2) 0 0;
  color: var(--adw-text-secondary);
  font-size: 12px;
}
.capability-version-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--adw-space-4);
  margin-bottom: var(--adw-space-4);
  color: var(--adw-text-secondary);
  font-size: 13px;
}
.capability-detail-versions {
  margin-top: var(--adw-space-6);
}
.capability-detail-config {
  margin-top: var(--adw-space-5);
}
.capability-detail-config h3,
.capability-detail-versions h3 {
  margin: 0 0 var(--adw-space-3);
  font-size: 16px;
}
.capability-detail-config pre {
  max-height: 220px;
  margin: 0;
  overflow: auto;
  color: var(--adw-text-primary);
  font: inherit;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}
.capability-form-hint {
  display: block;
  margin-top: 6px;
  color: var(--adw-text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}
.visually-hidden {
  display: none;
}
@media (max-width: 1280px) {
  .capability-collection--grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .capability-statistics {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 720px) {
  .capability-statistics,
  .capability-collection--grid {
    grid-template-columns: 1fr;
  }
  .capability-toolbar {
    flex-wrap: wrap;
  }
  .capability-toolbar__search,
  .capability-toolbar__select {
    width: 100%;
  }
  .capability-toolbar__count {
    width: 100%;
    margin-left: 0;
  }
  .capability-version-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .capability-pagination {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--adw-space-3);
  }
  .capability-tabs {
    overflow-x: auto;
  }
  .capability-tabs button {
    flex: 0 0 auto;
    padding-inline: 14px;
  }
}
</style>
