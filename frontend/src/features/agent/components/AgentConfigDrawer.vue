<template>
  <el-drawer
    :model-value="open"
    append-to-body
    destroy-on-close
    class="agent-config-drawer"
    :title="drawerTitle"
    size="min(760px, 92vw)"
    @close="emit('update:open', false)"
  >
    <DataState
      :loading="loading"
      :error="loadError"
      :empty="false"
      loading-text="正在加载 Agent 配置"
      @retry="loadConfiguration"
    >
      <div class="agent-config">
        <el-alert
          title="配置保存后只影响后续开始的执行；已经运行中的任务继续使用其不可变快照。"
          type="info"
          :closable="false"
          show-icon
        />

        <div v-if="sourceTemplateId" class="agent-template-source">
          <div>
            <strong>来源：系统 Agent 模板</strong>
            <span>当前安装版本 v{{ currentTemplateVersionNo ?? '-' }}</span>
          </div>
          <el-button
            v-if="canManage"
            link
            type="primary"
            :loading="upgradeLoading"
            @click="openUpgradeDialog"
          >
            检查模板升级
          </el-button>
        </div>

        <el-tabs v-model="activeTab">
          <el-tab-pane label="基础与执行" name="basic">
            <el-form label-position="top" class="agent-config__form">
              <div class="agent-config__two-columns">
                <el-form-item
                  :label="isTemplateVersionMode ? '版本展示名称' : 'Agent 名称'"
                  required
                >
                  <el-input
                    v-model="form.name"
                    maxlength="100"
                    placeholder="例如：文档审计 Agent"
                    :disabled="!canManage"
                  />
                </el-form-item>
                <el-form-item v-if="!isTemplateVersionMode" label="执行状态">
                  <el-switch
                    v-model="form.enabled"
                    active-text="已启用"
                    inactive-text="已停用"
                    :disabled="!canManage || !activeAgentId"
                  />
                </el-form-item>
              </div>

              <el-form-item :label="isTemplateVersionMode ? '版本说明' : 'Agent 描述'">
                <el-input
                  v-model="form.description"
                  type="textarea"
                  :rows="2"
                  maxlength="500"
                  show-word-limit
                  placeholder="说明 Agent 的职责和适用场景"
                  :disabled="!canManage"
                />
              </el-form-item>

              <div class="agent-config__two-columns">
                <el-form-item label="主模型" required>
                  <el-select
                    v-model="form.modelId"
                    filterable
                    placeholder="选择启用模型"
                    :disabled="!canManage"
                  >
                    <el-option
                      v-for="model in models"
                      :key="String(model.id)"
                      :label="model.displayName"
                      :value="model.id"
                    >
                      <span>{{ model.displayName }}</span>
                      <small class="agent-config__option-meta">{{ model.provider }}</small>
                    </el-option>
                  </el-select>
                </el-form-item>
                <el-form-item label="Token 预算">
                  <el-input-number
                    v-model="form.tokenBudget"
                    :min="1"
                    :step="1000"
                    controls-position="right"
                    placeholder="跟随空间限制"
                    :disabled="!canManage"
                  />
                  <span class="agent-config__hint">留空表示不增加 Agent 级限制。</span>
                </el-form-item>
              </div>

              <el-form-item label="Skill 选择模式" required>
                <div class="agent-mode-grid">
                  <button
                    type="button"
                    class="agent-mode-card"
                    :class="{ active: form.skillSelectionMode === 'ALL_BOUND' }"
                    :disabled="!canManage"
                    @click="selectMode('ALL_BOUND')"
                  >
                    <strong>ALL_BOUND</strong>
                    <span>向主模型暴露全部已绑定 Skill 的轻量目录，由模型按需读取。</span>
                  </button>
                  <button
                    type="button"
                    class="agent-mode-card"
                    :class="{ active: form.skillSelectionMode === 'ROUTER' }"
                    :disabled="!canManage"
                    @click="selectMode('ROUTER')"
                  >
                    <strong>ROUTER</strong>
                    <span>先路由选择候选 Skill，再向主模型暴露本次候选目录。</span>
                  </button>
                </div>
              </el-form-item>

              <el-form-item v-if="form.skillSelectionMode === 'ROUTER'" label="Skill Router 模型">
                <el-select
                  v-model="form.skillRouterModelId"
                  clearable
                  filterable
                  placeholder="留空时复用主模型"
                  :disabled="!canManage"
                >
                  <el-option
                    v-for="model in models"
                    :key="String(model.id)"
                    :label="model.displayName"
                    :value="model.id"
                  />
                </el-select>
              </el-form-item>

              <el-form-item label="系统提示词" required>
                <el-input
                  v-model="form.systemPrompt"
                  type="textarea"
                  :rows="8"
                  maxlength="20000"
                  show-word-limit
                  placeholder="定义 Agent 的角色、边界与输出要求"
                  :disabled="!canManage"
                />
              </el-form-item>

              <section class="agent-config__section">
                <header>
                  <div>
                    <strong>执行限制</strong>
                    <span>限制工具循环与单次任务最长执行时间。</span>
                  </div>
                </header>
                <div class="agent-config__two-columns">
                  <el-form-item label="最大工具迭代次数">
                    <el-input-number
                      v-model="form.maxIterations"
                      :min="1"
                      :max="100"
                      controls-position="right"
                      :disabled="!canManage"
                    />
                  </el-form-item>
                  <el-form-item label="执行超时（秒）">
                    <el-input-number
                      v-model="form.executionTimeoutSeconds"
                      :min="10"
                      :max="3600"
                      :step="30"
                      controls-position="right"
                      :disabled="!canManage"
                    />
                  </el-form-item>
                </div>
              </section>

              <el-collapse class="agent-config__advanced">
                <el-collapse-item title="高级范围与工具裁剪" name="advanced">
                  <el-form-item v-if="!isTemplateVersionMode" label="文档访问范围 JSON">
                    <el-input
                      v-model="form.documentScope"
                      type="textarea"
                      :rows="3"
                      placeholder="留空表示不配置额外文档范围"
                      :disabled="!canManage"
                    />
                  </el-form-item>
                  <el-form-item label="Agent 全局工具白名单">
                    <el-radio-group v-model="form.toolLimitMode" :disabled="!canManage">
                      <el-radio-button value="ALL">不额外限制</el-radio-button>
                      <el-radio-button value="CUSTOM">指定工具</el-radio-button>
                      <el-radio-button value="NONE">禁用全部</el-radio-button>
                    </el-radio-group>
                    <el-select
                      v-if="form.toolLimitMode === 'CUSTOM'"
                      v-model="form.toolWhitelist"
                      class="agent-config__full-width"
                      multiple
                      filterable
                      allow-create
                      default-first-option
                      placeholder="输入模型可见工具名后回车"
                      :disabled="!canManage"
                    />
                    <span class="agent-config__hint">
                      这里是 Agent 级二次裁剪；每个外部 MCP 仍可在“MCP 绑定”页单独限制。
                    </span>
                  </el-form-item>
                </el-collapse-item>
              </el-collapse>
            </el-form>
          </el-tab-pane>

          <el-tab-pane
            v-if="isTemplateVersionMode"
            :label="`系统 Skill (${templateSkillRows.length})`"
            name="template-skills"
          >
            <AgentSkillBindingPanel
              scope="template"
              :rows="templateSkillBindingRows"
              :available-skills="templateSkillOptions"
              :can-bind="true"
              :can-read="true"
              :adding="addingTemplateSkill"
              @add="addTemplateSkillFromPanel"
              @remove="removeTemplateSkill"
              @load-versions="loadTemplateSkillVersions"
            />
          </el-tab-pane>

          <el-tab-pane
            v-if="isTemplateVersionMode"
            :label="`系统 MCP (${templateMcpRows.length})`"
            name="template-mcp"
          >
            <AgentMcpBindingPanel
              scope="template"
              :rows="templateMcpBindingRows"
              :available-items="templateMcpOptions"
              :can-manage="true"
              :can-bind="true"
              :can-read="true"
              @add="addTemplateMcp"
              @remove="removeTemplateMcp"
              @load-versions="loadTemplateMcpVersions"
            />
          </el-tab-pane>

          <el-tab-pane
            v-if="!isTemplateVersionMode"
            :label="`Skill 绑定 (${skillRows.length})`"
            name="skills"
          >
            <AgentSkillBindingPanel
              scope="space"
              :rows="spaceSkillRows"
              :available-skills="availableSkills"
              :can-bind="canBindSkill"
              :can-read="canReadSkill"
              :adding="addingSkill"
              @add="addSpaceSkillFromPanel"
              @remove="removeSkill"
              @load-versions="loadSpaceSkillVersions"
            />
          </el-tab-pane>

          <el-tab-pane
            v-if="!isTemplateVersionMode"
            :label="`MCP 绑定 (${mcpRows.length})`"
            name="mcp"
          >
            <AgentMcpBindingPanel
              v-model:external-mcp-enabled="form.externalMcpEnabled"
              scope="space"
              :rows="spaceMcpBindingRows"
              :available-items="spaceMcpOptions"
              :can-manage="canManage"
              :can-bind="canBindMcp"
              :can-read="canReadMcp"
              @add="addSpaceMcpFromPanel"
              @remove="removeMcp"
              @mode-change="handleMcpModeChangeFromPanel"
              @load-tools="loadMcpToolsFromPanel"
            />
          </el-tab-pane>
        </el-tabs>

        <el-dialog
          v-model="upgradeDialogOpen"
          append-to-body
          destroy-on-close
          title="升级系统 Agent 模板"
          width="min(620px, 92vw)"
        >
          <div class="agent-upgrade">
            <el-form label-position="top">
              <el-form-item label="目标版本" required>
                <el-select
                  v-model="upgradeTargetVersionId"
                  class="agent-config__full-width"
                  placeholder="选择更高的已发布版本"
                  @change="upgradePreview = null"
                >
                  <el-option
                    v-for="version in upgradeVersions"
                    :key="String(version.id)"
                    :label="`v${version.versionNo} · ${version.displayName || '未命名版本'}`"
                    :value="version.id"
                  />
                </el-select>
              </el-form-item>
            </el-form>
            <el-empty
              v-if="!upgradeLoading && !upgradeVersions.length"
              description="当前已是最新可用版本"
              :image-size="72"
            />
            <template v-if="upgradePreview">
              <el-alert
                v-if="upgradePreview.conflictingFields.length"
                type="warning"
                :closable="false"
                show-icon
                :title="`检测到 ${upgradePreview.conflictingFields.length} 项冲突，应用时保留当前空间配置`"
              />
              <el-alert
                v-else
                type="success"
                :closable="false"
                show-icon
                title="未检测到冲突，可以安全应用升级"
              />
              <dl class="agent-upgrade__summary">
                <div>
                  <dt>配置冲突</dt>
                  <dd>{{ upgradeConflictLabels }}</dd>
                </div>
                <div>
                  <dt>升级后 Skill</dt>
                  <dd>{{ upgradePreview.proposedSkillVersionIds.length }} 个版本</dd>
                </div>
                <div>
                  <dt>升级后 MCP</dt>
                  <dd>{{ upgradePreview.proposedMcpBindings.length }} 个绑定</dd>
                </div>
              </dl>
              <p class="agent-config__hint">
                这是旧模板、当前空间修改和新模板之间的三方合并结果。发生冲突时默认保留当前空间值。
              </p>
            </template>
          </div>
          <template #footer>
            <el-button @click="upgradeDialogOpen = false">取消</el-button>
            <el-button
              v-if="upgradeVersions.length"
              :loading="upgradeLoading"
              @click="previewTemplateUpgrade"
            >
              预览变更
            </el-button>
            <el-button
              v-if="upgradePreview"
              type="primary"
              :loading="upgradeLoading"
              @click="applyTemplateUpgrade"
            >
              确认并应用
            </el-button>
          </template>
        </el-dialog>
      </div>
    </DataState>

    <template #footer>
      <div class="agent-config__footer">
        <span v-if="currentConfigVersion">当前配置版本 v{{ currentConfigVersion }}</span>
        <div>
          <el-button @click="emit('update:open', false)">{{ canSave ? '取消' : '关闭' }}</el-button>
          <el-button v-if="canSave" type="primary" :loading="saving" @click="saveConfiguration">
            {{ isTemplateVersionMode ? '保存草稿版本' : '保存配置' }}
          </el-button>
        </div>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import {
  ElAlert,
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElDrawer,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
  ElTabPane,
  ElTabs,
} from 'element-plus'
import { computed, reactive, ref, watch } from 'vue'

import {
  createAgent,
  getAgent,
  listAgentMcpBindings,
  listAgentSkills,
  listModels,
  replaceAgentMcpBindings,
  replaceAgentSkills,
  updateAgent,
  upgradeAgentTemplate,
} from '@/features/agent/api/agent-api'
import {
  createAgentTemplateVersion,
  listCapabilityVersions,
  searchSystemCapabilities,
  updateAgentTemplateVersion,
} from '@/features/system-capability/api/system-capability-api'
import type {
  AgentDetail,
  AgentInput,
  AgentMcpBinding,
  AgentSkillBinding,
  AgentTemplateUpgradePreview,
  ModelOption,
  SkillSelectionMode,
} from '@/features/agent/types'
import type {
  AgentTemplateVersionCreateInput,
  CapabilityVersion,
  SystemCapability,
  SystemCapabilityType,
} from '@/features/system-capability/types'
import { listMcpTools, searchMcpServers } from '@/features/mcp/api/mcp-api'
import type { McpServer, McpTool } from '@/features/mcp/types'
import { listSkillVersions, searchSkills } from '@/features/skill/api/skill-api'
import type { Skill, SkillVersion } from '@/features/skill/types'
import type { EntityId } from '@/features/workspace/types'
import DataState from '@/shared/components/DataState.vue'
import AgentMcpBindingPanel from './AgentMcpBindingPanel.vue'
import AgentSkillBindingPanel from './AgentSkillBindingPanel.vue'
import type { McpBindingRow } from './AgentMcpBindingPanel.vue'
import type { SkillBindingOption, SkillBindingRow } from './AgentSkillBindingPanel.vue'

type ToolLimitMode = 'ALL' | 'CUSTOM' | 'NONE'

interface SkillRow {
  skillId: EntityId
  skillName: string
  skillDisplayName: string
  skillVersionId: EntityId
  versionNo: number
  versions: SkillVersion[]
  loadingVersions: boolean
}

interface McpRow {
  mcpServerId: EntityId
  serverKey: string
  displayName: string
  mode: ToolLimitMode
  toolWhitelist: string[]
  tools: McpTool[]
  loadingTools: boolean
}

interface TemplateSkillRow {
  skillId: EntityId
  skillName: string
  skillDisplayName: string
  skillVersionId: EntityId | null
  versions: CapabilityVersion[]
  loadingVersions: boolean
}

interface TemplateMcpRow {
  mcpTemplateId: EntityId
  mcpTemplateVersionId: EntityId | null
  serverKey: string
  displayName: string
  versions: CapabilityVersion[]
  toolWhitelistText: string
  loadingVersions: boolean
}

const props = defineProps<{
  open: boolean
  agentId: EntityId | null
  /** 模板版本模式下所属的系统 Agent 模板 ID。 */
  templateId?: EntityId | null
  /** 编辑草稿时的版本 ID；为空时保存为新草稿。 */
  templateVersionId?: EntityId | null
  mode?: 'space-agent' | 'template-version'
  templateVersion?: CapabilityVersion | null
  spaceId: EntityId
  canManage: boolean
  canBindSkill: boolean
  canBindMcp: boolean
  canReadSkill: boolean
  canReadMcp: boolean
}>()

const emit = defineEmits<{
  'update:open': [open: boolean]
  saved: []
}>()

const isTemplateVersionMode = computed(() => props.mode === 'template-version')
const drawerTitle = computed(() => {
  if (!isTemplateVersionMode.value) return props.agentId ? 'Agent 配置' : '新建 Agent'
  if (props.templateVersionId) return '编辑 Agent 模板草稿'
  return props.templateVersion ? '复制 Agent 模板版本' : '新建 Agent 模板版本'
})

const activeTab = ref('basic')
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const activeAgentId = ref<EntityId | null>(null)
const currentConfigVersion = ref<number | null>(null)
const sourceTemplateId = ref<EntityId | null>(null)
const sourceTemplateVersionId = ref<EntityId | null>(null)
const currentTemplateVersionNo = ref<number | null>(null)
const upgradeDialogOpen = ref(false)
const upgradeLoading = ref(false)
const upgradeVersions = ref<CapabilityVersion[]>([])
const upgradeTargetVersionId = ref<EntityId | null>(null)
const upgradePreview = ref<AgentTemplateUpgradePreview | null>(null)
const models = ref<ModelOption[]>([])
const skills = ref<Skill[]>([])
const mcpServers = ref<McpServer[]>([])
const skillRows = ref<SkillRow[]>([])
const mcpRows = ref<McpRow[]>([])
const systemSkills = ref<SystemCapability[]>([])
const systemMcpTemplates = ref<SystemCapability[]>([])
const templateSkillRows = ref<TemplateSkillRow[]>([])
const templateMcpRows = ref<TemplateMcpRow[]>([])
const templateSkillToAdd = ref<EntityId | null>(null)
const templateMcpToAdd = ref<EntityId | null>(null)
const addingTemplateSkill = ref(false)
const skillToAdd = ref<EntityId | null>(null)
const mcpToAdd = ref<EntityId | null>(null)
const addingSkill = ref(false)
let loadSequence = 0

const form = reactive({
  name: '',
  description: '',
  systemPrompt: '',
  modelId: '' as EntityId,
  skillSelectionMode: 'ALL_BOUND' as SkillSelectionMode,
  skillRouterModelId: null as EntityId | null,
  externalMcpEnabled: false,
  tokenBudget: undefined as number | undefined,
  documentScope: '',
  toolLimitMode: 'ALL' as ToolLimitMode,
  toolWhitelist: [] as string[],
  maxIterations: 12,
  executionTimeoutSeconds: 600,
  enabled: true,
})

const canSave = computed(
  () =>
    props.canManage || (Boolean(activeAgentId.value) && (props.canBindSkill || props.canBindMcp)),
)
const availableSkills = computed(() => {
  const selected = new Set(skillRows.value.map((row) => String(row.skillId)))
  return skills.value.filter((skill) => !selected.has(String(skill.id)))
})
const availableMcpServers = computed(() => {
  const selected = new Set(mcpRows.value.map((row) => String(row.mcpServerId)))
  return mcpServers.value.filter((server) => !selected.has(String(server.id)))
})
const templateSkillOptions = computed<SkillBindingOption[]>(() =>
  systemSkills.value.map((skill) => ({
    id: skill.id,
    name: skill.technicalKey,
    displayName: skill.displayName,
  })),
)
const templateMcpOptions = computed(() =>
  systemMcpTemplates.value.map((template) => ({
    id: template.id,
    name: template.technicalKey,
    displayName: template.displayName,
  })),
)
const spaceMcpOptions = computed(() =>
  availableMcpServers.value.map((server) => ({
    id: server.id,
    name: server.serverKey,
    displayName: server.displayName,
  })),
)
const spaceSkillRows = computed(() => skillRows.value as unknown as SkillBindingRow[])
const templateSkillBindingRows = computed(() => templateSkillRows.value as SkillBindingRow[])
const spaceMcpBindingRows = computed(() => mcpRows.value as unknown as McpBindingRow[])
const templateMcpBindingRows = computed(() => templateMcpRows.value as unknown as McpBindingRow[])
const upgradeConflictLabels = computed(() => {
  const labels: Record<string, string> = {
    name: '名称',
    description: '描述',
    systemPrompt: '系统提示词',
    modelId: '主模型',
    skillSelectionMode: 'Skill 选择模式',
    skillRouterModelId: 'Router 模型',
    externalMcpEnabled: '外部 MCP 开关',
    tokenBudget: 'Token 预算',
    toolWhitelist: '工具白名单',
    maxIterations: '最大迭代次数',
    executionTimeoutSeconds: '执行超时',
    skills: 'Skill 绑定',
    mcps: 'MCP 绑定',
  }
  return upgradePreview.value?.conflictingFields.length
    ? upgradePreview.value.conflictingFields.map((field) => labels[field] || field).join('、')
    : '无'
})

watch(
  [
    () => props.open,
    () => props.agentId,
    () => props.templateVersionId,
    () => props.templateVersion?.id,
  ],
  ([open]) => {
    if (open) void loadConfiguration()
    else loadSequence++
  },
  { immediate: true },
)

async function loadConfiguration(): Promise<void> {
  const sequence = ++loadSequence
  loading.value = true
  loadError.value = ''
  activeTab.value = 'basic'
  activeAgentId.value = props.agentId
  currentConfigVersion.value = null
  sourceTemplateId.value = null
  sourceTemplateVersionId.value = null
  currentTemplateVersionNo.value = null
  upgradeDialogOpen.value = false
  upgradePreview.value = null
  skillRows.value = []
  mcpRows.value = []
  templateSkillRows.value = []
  templateMcpRows.value = []
  templateSkillToAdd.value = null
  templateMcpToAdd.value = null
  skillToAdd.value = null
  mcpToAdd.value = null
  resetForm()
  try {
    if (isTemplateVersionMode.value) {
      const [modelOptions, skillOptions, mcpOptions] = await Promise.all([
        listModels(true),
        loadAllSystemCapabilities('SKILL'),
        loadAllSystemCapabilities('MCP_TEMPLATE'),
      ])
      if (sequence !== loadSequence) return
      models.value = modelOptions
      systemSkills.value = skillOptions
      systemMcpTemplates.value = mcpOptions
      if (props.templateVersion) await applyTemplateVersion(props.templateVersion)
      return
    }
    const detailPromise = props.agentId ? getAgent(props.agentId) : Promise.resolve(null)
    const skillBindingsPromise = props.agentId
      ? listAgentSkills(props.agentId)
      : Promise.resolve<AgentSkillBinding[]>([])
    const mcpBindingsPromise = props.agentId
      ? listAgentMcpBindings(props.agentId)
      : Promise.resolve<AgentMcpBinding[]>([])
    const [detail, modelOptions, skillBindings, mcpBindings, skillOptions, serverOptions] =
      await Promise.all([
        detailPromise,
        listModels(true),
        skillBindingsPromise,
        mcpBindingsPromise,
        !isTemplateVersionMode.value && props.canReadSkill
          ? loadAllSkills()
          : Promise.resolve<Skill[]>([]),
        !isTemplateVersionMode.value && props.canReadMcp
          ? loadAllMcpServers()
          : Promise.resolve<McpServer[]>([]),
      ])
    if (sequence !== loadSequence) return
    models.value = modelOptions
    skills.value = skillOptions
    mcpServers.value = serverOptions
    if (detail) {
      applyDetail(detail)
      if (detail.templateId && detail.templateVersionId) {
        const templateVersions = await listCapabilityVersions('AGENT_TEMPLATE', detail.templateId)
        currentTemplateVersionNo.value =
          templateVersions.find(
            (version) => String(version.id) === String(detail.templateVersionId),
          )?.versionNo ?? null
      }
    }
    skillRows.value = skillBindings.map(toSkillRow)
    mcpRows.value = mcpBindings.map(toMcpRow)
  } catch (error) {
    if (sequence !== loadSequence) return
    loadError.value = error instanceof Error ? error.message : 'Agent 配置加载失败'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

async function loadAllSkills(): Promise<Skill[]> {
  const first = await searchSkills(props.spaceId, { status: 'ACTIVE', pageNum: 1, pageSize: 100 })
  if (first.total <= first.records.length) return first.records
  const pageCount = Math.ceil(first.total / 100)
  const rest = await Promise.all(
    Array.from({ length: pageCount - 1 }, (_, index) =>
      searchSkills(props.spaceId, { status: 'ACTIVE', pageNum: index + 2, pageSize: 100 }),
    ),
  )
  return [first, ...rest].flatMap((page) => page.records)
}

async function loadAllMcpServers(): Promise<McpServer[]> {
  const first = await searchMcpServers(props.spaceId, { status: 1, pageNum: 1, pageSize: 100 })
  if (first.total <= first.records.length) return first.records
  const pageCount = Math.ceil(first.total / 100)
  const rest = await Promise.all(
    Array.from({ length: pageCount - 1 }, (_, index) =>
      searchMcpServers(props.spaceId, { status: 1, pageNum: index + 2, pageSize: 100 }),
    ),
  )
  return [first, ...rest].flatMap((page) => page.records)
}

async function loadAllSystemCapabilities(type: SystemCapabilityType): Promise<SystemCapability[]> {
  const first = await searchSystemCapabilities({ type, status: 1, pageNum: 1, pageSize: 100 })
  if (first.total <= first.records.length) {
    return first.records.filter((item) => item.latestPublishedVersionId !== null)
  }
  const pageCount = Math.ceil(first.total / 100)
  const rest = await Promise.all(
    Array.from({ length: pageCount - 1 }, (_, index) =>
      searchSystemCapabilities({ type, status: 1, pageNum: index + 2, pageSize: 100 }),
    ),
  )
  return [first, ...rest]
    .flatMap((page) => page.records)
    .filter((item) => item.latestPublishedVersionId !== null)
}

function resetForm(): void {
  Object.assign(form, {
    name: '',
    description: '',
    systemPrompt: '',
    modelId: '',
    skillSelectionMode: 'ALL_BOUND',
    skillRouterModelId: null,
    externalMcpEnabled: false,
    tokenBudget: undefined,
    documentScope: '',
    toolLimitMode: 'ALL',
    toolWhitelist: [],
    maxIterations: 12,
    executionTimeoutSeconds: 600,
    enabled: true,
  })
}

function applyDetail(agent: AgentDetail): void {
  activeAgentId.value = agent.id
  currentConfigVersion.value = agent.configVersion
  sourceTemplateId.value = agent.templateId
  sourceTemplateVersionId.value = agent.templateVersionId
  Object.assign(form, {
    name: agent.name,
    description: agent.description || '',
    systemPrompt: agent.systemPrompt,
    modelId: agent.modelId,
    skillSelectionMode: agent.skillSelectionMode,
    skillRouterModelId: agent.skillRouterModelId,
    externalMcpEnabled: agent.externalMcpEnabled,
    tokenBudget: agent.tokenBudget ?? undefined,
    documentScope: agent.documentScope || '',
    toolLimitMode:
      agent.toolWhitelist === null ? 'ALL' : agent.toolWhitelist.length ? 'CUSTOM' : 'NONE',
    toolWhitelist: agent.toolWhitelist || [],
    maxIterations: agent.maxIterations,
    executionTimeoutSeconds: agent.executionTimeoutSeconds,
    enabled: agent.status === 'ENABLED',
  })
}

async function openUpgradeDialog(): Promise<void> {
  if (!sourceTemplateId.value || !sourceTemplateVersionId.value) return
  upgradeDialogOpen.value = true
  upgradeLoading.value = true
  upgradePreview.value = null
  try {
    const versions = (
      await listCapabilityVersions('AGENT_TEMPLATE', sourceTemplateId.value)
    ).filter(isPublishedCapabilityVersion)
    const current = versions.find(
      (version) => String(version.id) === String(sourceTemplateVersionId.value),
    )
    currentTemplateVersionNo.value = current?.versionNo ?? null
    // 当前来源版本可能已被平台停用，普通空间成员无法读取该历史版本；
    // 此时仍展示其余已发布版本，由后端继续校验必须高于当前版本。
    upgradeVersions.value = current
      ? versions.filter((version) => version.versionNo > current.versionNo)
      : versions
    upgradeTargetVersionId.value = upgradeVersions.value[0]?.id ?? null
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模板版本加载失败')
  } finally {
    upgradeLoading.value = false
  }
}

async function previewTemplateUpgrade(): Promise<void> {
  if (!activeAgentId.value || !upgradeTargetVersionId.value) return
  upgradeLoading.value = true
  try {
    upgradePreview.value = await upgradeAgentTemplate(activeAgentId.value, {
      targetVersionId: upgradeTargetVersionId.value,
      previewOnly: true,
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模板升级预览失败')
  } finally {
    upgradeLoading.value = false
  }
}

async function applyTemplateUpgrade(): Promise<void> {
  if (!activeAgentId.value || !upgradePreview.value) return
  upgradeLoading.value = true
  try {
    const preview = upgradePreview.value
    await upgradeAgentTemplate(activeAgentId.value, {
      targetVersionId: preview.targetTemplateVersionId,
      previewOnly: false,
      resolvedConfig: preview.proposedConfig,
      resolvedSkillVersionIds: preview.proposedSkillVersionIds,
      resolvedMcpBindings: preview.proposedMcpBindings,
    })
    ElMessage.success('Agent 模板升级已应用')
    upgradeDialogOpen.value = false
    emit('saved')
    await loadConfiguration()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Agent 模板升级失败')
  } finally {
    upgradeLoading.value = false
  }
}

async function applyTemplateVersion(version: CapabilityVersion): Promise<void> {
  Object.assign(form, {
    name: version.displayName || '',
    description: version.description || '',
    systemPrompt: version.systemPrompt || '',
    modelId: version.modelId || '',
    skillSelectionMode: version.skillSelectionMode || 'ALL_BOUND',
    skillRouterModelId: version.skillRouterModelId || null,
    externalMcpEnabled: version.externalMcpEnabled ?? false,
    tokenBudget: version.tokenBudget ?? undefined,
    toolLimitMode:
      version.toolWhitelist === null || version.toolWhitelist === undefined
        ? 'ALL'
        : version.toolWhitelist.length
          ? 'CUSTOM'
          : 'NONE',
    toolWhitelist: version.toolWhitelist || [],
    maxIterations: version.maxIterations || 12,
    executionTimeoutSeconds: version.executionTimeoutSeconds || 600,
  })
  const skillRefs = version.skills || []
  const mcpRefs = version.mcps || []
  templateSkillRows.value = await Promise.all(
    skillRefs.map(async (reference) => {
      const capability = systemSkills.value.find(
        (item) => String(item.id) === String(reference.skillId),
      )
      const allVersions = await listCapabilityVersions('SKILL', reference.skillId)
      return {
        skillId: reference.skillId,
        skillName: capability?.technicalKey || String(reference.skillId),
        skillDisplayName: capability?.displayName || String(reference.skillId),
        skillVersionId: reference.skillVersionId,
        versions: allVersions.filter((item) => isPublishedCapabilityVersion(item)),
        loadingVersions: false,
      }
    }),
  )
  templateMcpRows.value = await Promise.all(
    mcpRefs.map(async (reference) => {
      const template = systemMcpTemplates.value.find(
        (item) => String(item.id) === String(reference.mcpTemplateId),
      )
      const allVersions = await listCapabilityVersions(
        'MCP_TEMPLATE',
        reference.mcpTemplateId || reference.mcpTemplateVersionId,
      )
      return {
        mcpTemplateId: reference.mcpTemplateId || reference.mcpTemplateVersionId,
        mcpTemplateVersionId: reference.mcpTemplateVersionId,
        serverKey: template?.technicalKey || String(reference.mcpTemplateId || ''),
        displayName: template?.displayName || String(reference.mcpTemplateId || ''),
        versions: allVersions.filter((item) => isPublishedCapabilityVersion(item)),
        toolWhitelistText: (reference.toolWhitelist || []).join('\n'),
        loadingVersions: false,
      }
    }),
  )
}

function isPublishedCapabilityVersion(version: CapabilityVersion): boolean {
  return version.status === 1 || version.status === 'PUBLISHED'
}

function toSkillRow(binding: AgentSkillBinding): SkillRow {
  const skill = skills.value.find((item) => String(item.id) === String(binding.skillId))
  return {
    skillId: binding.skillId,
    skillName: binding.skillName,
    skillDisplayName: skill?.displayName || binding.skillName,
    skillVersionId: binding.skillVersionId,
    versionNo: binding.versionNo,
    versions: [
      {
        id: binding.skillVersionId,
        skillId: binding.skillId,
        versionNo: binding.versionNo,
        status: 'PUBLISHED',
        activationDescription: '',
        sha256: binding.sha256,
        packageSize: 0,
        allowedTools: [],
        readableResourcePaths: [],
        createdBy: 0,
        createdAt: null,
        publishedAt: null,
      },
    ],
    loadingVersions: false,
  }
}

function toMcpRow(binding: AgentMcpBinding): McpRow {
  return {
    mcpServerId: binding.mcpServerId,
    serverKey: binding.serverKey,
    displayName: binding.displayName,
    mode: binding.toolWhitelist === null ? 'ALL' : binding.toolWhitelist.length ? 'CUSTOM' : 'NONE',
    toolWhitelist: binding.toolWhitelist || [],
    tools: (binding.toolWhitelist || []).map((name) => ({
      name,
      description: null,
      inputSchema: null,
    })),
    loadingTools: false,
  }
}

function selectMode(mode: SkillSelectionMode): void {
  if (!props.canManage) return
  form.skillSelectionMode = mode
  if (mode === 'ALL_BOUND') form.skillRouterModelId = null
}

async function loadVersions(row: SkillRow): Promise<void> {
  if (row.loadingVersions || row.versions.length > 1) return
  row.loadingVersions = true
  try {
    const versions = (await listSkillVersions(row.skillId)).filter(
      (version) => version.status === 'PUBLISHED',
    )
    row.versions = versions.some((version) => String(version.id) === String(row.skillVersionId))
      ? versions
      : [...row.versions, ...versions]
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Skill 版本加载失败')
  } finally {
    row.loadingVersions = false
  }
}

async function addSkill(): Promise<void> {
  const skill = skills.value.find((item) => String(item.id) === String(skillToAdd.value))
  if (!skill) return
  addingSkill.value = true
  try {
    const versions = (await listSkillVersions(skill.id)).filter(
      (version) => version.status === 'PUBLISHED',
    )
    if (!versions.length) {
      ElMessage.warning('该 Skill 尚无已发布版本，无法绑定')
      return
    }
    const latest = versions[0]
    skillRows.value.push({
      skillId: skill.id,
      skillName: skill.name,
      skillDisplayName: skill.displayName,
      skillVersionId: latest.id,
      versionNo: latest.versionNo,
      versions,
      loadingVersions: false,
    })
    skillToAdd.value = null
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Skill 版本加载失败')
  } finally {
    addingSkill.value = false
  }
}

function removeSkill(skillId: EntityId): void {
  skillRows.value = skillRows.value.filter((row) => String(row.skillId) !== String(skillId))
}

function addMcp(): void {
  const server = mcpServers.value.find((item) => String(item.id) === String(mcpToAdd.value))
  if (!server) return
  mcpRows.value.push({
    mcpServerId: server.id,
    serverKey: server.serverKey,
    displayName: server.displayName,
    mode: 'ALL',
    toolWhitelist: [],
    tools: [],
    loadingTools: false,
  })
  mcpToAdd.value = null
}

function addSpaceMcpFromPanel(serverId: EntityId): void {
  mcpToAdd.value = serverId
  addMcp()
}
function handleMcpModeChangeFromPanel(row: McpBindingRow): void {
  if (row.mcpServerId == null) return
  const target = mcpRows.value.find((item) => String(item.mcpServerId) === String(row.mcpServerId))
  if (target) handleMcpModeChange(target)
}
function loadMcpToolsFromPanel(row: McpBindingRow): Promise<void> {
  if (row.mcpServerId == null) return Promise.resolve()
  const target = mcpRows.value.find((item) => String(item.mcpServerId) === String(row.mcpServerId))
  return target ? loadMcpTools(target) : Promise.resolve()
}

function removeMcp(serverId: EntityId): void {
  mcpRows.value = mcpRows.value.filter((row) => String(row.mcpServerId) !== String(serverId))
}

function handleMcpModeChange(row: McpRow): void {
  if (row.mode === 'NONE') row.toolWhitelist = []
  if (row.mode === 'CUSTOM') void loadMcpTools(row)
}

async function loadMcpTools(row: McpRow): Promise<void> {
  if (row.loadingTools || row.tools.length > row.toolWhitelist.length) return
  row.loadingTools = true
  try {
    row.tools = await listMcpTools(row.mcpServerId)
    if (!row.tools.length) ElMessage.warning('该 MCP 服务还没有成功发现的工具快照')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'MCP 工具加载失败')
  } finally {
    row.loadingTools = false
  }
}

async function addTemplateSkill(skillId?: unknown): Promise<void> {
  if (typeof skillId === 'string' || typeof skillId === 'number') templateSkillToAdd.value = skillId
  const skill = systemSkills.value.find(
    (item) => String(item.id) === String(templateSkillToAdd.value),
  )
  if (!skill) return
  addingTemplateSkill.value = true
  try {
    const versions = (await listCapabilityVersions('SKILL', skill.id)).filter(
      isPublishedCapabilityVersion,
    )
    templateSkillRows.value.push({
      skillId: skill.id,
      skillName: skill.technicalKey,
      skillDisplayName: skill.displayName,
      skillVersionId: versions[0]?.id || null,
      versions,
      loadingVersions: false,
    })
    templateSkillToAdd.value = null
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '系统 Skill 版本加载失败')
  } finally {
    addingTemplateSkill.value = false
  }
}

function addTemplateSkillFromPanel(skillId: EntityId): Promise<void> {
  return addTemplateSkill(skillId)
}
function addSpaceSkillFromPanel(skillId: EntityId): Promise<void> {
  skillToAdd.value = skillId
  return addSkill()
}
function loadSpaceSkillVersions(row: SkillBindingRow): Promise<void> {
  const target = skillRows.value.find((item) => String(item.skillId) === String(row.skillId))
  return target ? loadVersions(target) : Promise.resolve()
}

function removeTemplateSkill(skillId: EntityId): void {
  templateSkillRows.value = templateSkillRows.value.filter(
    (row) => String(row.skillId) !== String(skillId),
  )
}

function addTemplateMcp(templateId?: unknown): void {
  if (typeof templateId === 'string' || typeof templateId === 'number')
    templateMcpToAdd.value = templateId
  const template = systemMcpTemplates.value.find(
    (item) => String(item.id) === String(templateMcpToAdd.value),
  )
  if (!template) return
  const row: TemplateMcpRow = {
    mcpTemplateId: template.id,
    mcpTemplateVersionId: null,
    serverKey: template.technicalKey,
    displayName: template.displayName,
    versions: [],
    toolWhitelistText: '',
    loadingVersions: false,
  }
  templateMcpRows.value.push(row)
  templateMcpToAdd.value = null
  void loadTemplateMcpVersions(row)
}

function removeTemplateMcp(mcpTemplateId: EntityId): void {
  templateMcpRows.value = templateMcpRows.value.filter(
    (row) => String(row.mcpTemplateId) !== String(mcpTemplateId),
  )
}

async function loadTemplateSkillVersions(row: SkillBindingRow): Promise<void> {
  if (row.loadingVersions || row.versions?.length) return
  row.loadingVersions = true
  try {
    row.versions = (await listCapabilityVersions('SKILL', row.skillId)).filter(
      isPublishedCapabilityVersion,
    )
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '系统 Skill 版本加载失败')
  } finally {
    row.loadingVersions = false
  }
}

async function loadTemplateMcpVersions(row: McpBindingRow): Promise<void> {
  if (row.mcpTemplateId == null) return
  if (row.loadingVersions || row.versions?.length) return
  row.loadingVersions = true
  try {
    row.versions = (await listCapabilityVersions('MCP_TEMPLATE', row.mcpTemplateId)).filter(
      isPublishedCapabilityVersion,
    )
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '系统 MCP 版本加载失败')
  } finally {
    row.loadingVersions = false
  }
}

function validateForm(): boolean {
  if (!props.canManage) return true
  if (!form.name.trim() || !form.systemPrompt.trim() || !form.modelId) {
    ElMessage.warning('请完整填写 Agent 名称、主模型和系统提示词')
    activeTab.value = 'basic'
    return false
  }
  if (form.skillSelectionMode === 'ALL_BOUND' && form.skillRouterModelId) {
    ElMessage.warning('ALL_BOUND 模式不能配置 Skill Router 模型')
    return false
  }
  if (form.documentScope.trim()) {
    try {
      JSON.parse(form.documentScope)
    } catch {
      ElMessage.warning('文档访问范围必须是合法 JSON')
      return false
    }
  }
  if (form.toolLimitMode === 'CUSTOM' && !form.toolWhitelist.length) {
    ElMessage.warning('指定工具模式至少需要填写一个工具名')
    return false
  }
  if (
    isTemplateVersionMode.value &&
    (templateSkillRows.value.some((row) => !row.skillVersionId) ||
      templateMcpRows.value.some((row) => !row.mcpTemplateVersionId))
  ) {
    ElMessage.warning('请为每个系统 Skill 和 MCP 引用选择已发布版本')
    return false
  }
  return true
}

function buildPayload(): AgentInput {
  const payload: AgentInput = {
    name: form.name.trim(),
    description: form.description.trim() || undefined,
    systemPrompt: form.systemPrompt.trim(),
    modelId: form.modelId,
    skillSelectionMode: form.skillSelectionMode,
    skillRouterModelId:
      form.skillSelectionMode === 'ROUTER' ? form.skillRouterModelId || undefined : undefined,
    externalMcpEnabled: form.externalMcpEnabled,
    tokenBudget: form.tokenBudget,
    documentScope: form.documentScope.trim() || undefined,
    toolWhitelist:
      form.toolLimitMode === 'ALL'
        ? null
        : form.toolLimitMode === 'NONE'
          ? []
          : [...new Set(form.toolWhitelist.map((tool) => tool.trim()).filter(Boolean))],
    maxIterations: form.maxIterations,
    executionTimeoutSeconds: form.executionTimeoutSeconds,
  }
  if (!activeAgentId.value) payload.spaceId = props.spaceId
  else payload.status = form.enabled ? 1 : 0
  return payload
}

function buildTemplateVersionPayload(): AgentTemplateVersionCreateInput {
  return {
    displayName: form.name.trim(),
    description: form.description.trim() || undefined,
    systemPrompt: form.systemPrompt.trim(),
    modelId: form.modelId,
    skillSelectionMode: form.skillSelectionMode,
    skillRouterModelId:
      form.skillSelectionMode === 'ROUTER' ? form.skillRouterModelId || null : null,
    externalMcpEnabled: form.externalMcpEnabled,
    tokenBudget: form.tokenBudget ?? null,
    toolWhitelist:
      form.toolLimitMode === 'ALL'
        ? null
        : form.toolLimitMode === 'NONE'
          ? []
          : [...new Set(form.toolWhitelist.map((tool) => tool.trim()).filter(Boolean))],
    maxIterations: form.maxIterations,
    executionTimeoutSeconds: form.executionTimeoutSeconds,
    skills: templateSkillRows.value
      .filter((row) => row.skillVersionId)
      .map((row) => ({ skillId: row.skillId, skillVersionId: row.skillVersionId! })),
    mcps: templateMcpRows.value
      .filter((row) => row.mcpTemplateVersionId)
      .map((row) => ({
        mcpTemplateVersionId: row.mcpTemplateVersionId!,
        toolWhitelist: parseToolWhitelist(row.toolWhitelistText),
      })),
  }
}

function parseToolWhitelist(value: string): string[] | null {
  const tools = value
    .split(/\r?\n/)
    .map((tool) => tool.trim())
    .filter(Boolean)
  return tools.length ? [...new Set(tools)] : null
}

async function saveConfiguration(): Promise<void> {
  if (!validateForm()) return
  saving.value = true
  const bindingErrors: string[] = []
  try {
    if (isTemplateVersionMode.value) {
      if (!props.templateId) {
        ElMessage.error('缺少 Agent 模板 ID')
        return
      }
      const payload = buildTemplateVersionPayload()
      if (props.templateVersionId) {
        await updateAgentTemplateVersion(props.templateVersionId, payload)
      } else {
        await createAgentTemplateVersion(props.templateId, payload)
      }
      emit('saved')
      ElMessage.success('Agent 模板草稿版本创建成功')
      emit('update:open', false)
      return
    }
    let savedAgent: AgentDetail | null = null
    if (props.canManage) {
      const payload = buildPayload()
      savedAgent = activeAgentId.value
        ? await updateAgent(activeAgentId.value, payload)
        : await createAgent(payload)
      activeAgentId.value = savedAgent.id
      currentConfigVersion.value = savedAgent.configVersion
    }
    if (!activeAgentId.value) return

    if (props.canBindSkill) {
      try {
        await replaceAgentSkills(
          activeAgentId.value,
          skillRows.value.map((row) => row.skillVersionId),
        )
      } catch (error) {
        bindingErrors.push(
          error instanceof Error ? `Skill：${error.message}` : 'Skill 绑定保存失败',
        )
      }
    }
    if (props.canBindMcp) {
      try {
        await replaceAgentMcpBindings(
          activeAgentId.value,
          mcpRows.value.map((row) => ({
            mcpServerId: row.mcpServerId,
            toolWhitelist: row.mode === 'ALL' ? null : row.mode === 'NONE' ? [] : row.toolWhitelist,
          })),
        )
      } catch (error) {
        bindingErrors.push(error instanceof Error ? `MCP：${error.message}` : 'MCP 绑定保存失败')
      }
    }

    emit('saved')
    if (bindingErrors.length) {
      ElMessage.warning(`基础配置已保存，但部分绑定失败：${bindingErrors.join('；')}`)
      return
    }
    ElMessage.success('Agent 配置已保存，仅影响后续执行')
    emit('update:open', false)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Agent 配置保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.agent-config {
  display: grid;
  gap: var(--adw-space-4);
}
.agent-config__form,
.agent-config__section {
  display: grid;
  gap: var(--adw-space-3);
}
.agent-template-source {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--adw-space-4);
  padding: var(--adw-space-3) var(--adw-space-4);
  border: 1px solid var(--adw-color-primary-soft);
  border-radius: var(--adw-radius-md);
  background: var(--adw-color-primary-soft);
}
.agent-template-source > div {
  display: grid;
  gap: 3px;
}
.agent-template-source span,
.agent-upgrade__summary dt {
  color: var(--adw-text-secondary);
  font-size: 12px;
}
.agent-upgrade {
  display: grid;
  gap: var(--adw-space-4);
}
.agent-upgrade__summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--adw-space-3);
  margin: 0;
}
.agent-upgrade__summary > div {
  display: grid;
  gap: 5px;
  padding: var(--adw-space-3);
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-sm);
}
.agent-upgrade__summary dd {
  margin: 0;
  color: var(--adw-text-primary);
  font-weight: 600;
}
.agent-config__two-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--adw-space-4);
}
.agent-config__two-columns :deep(.el-input-number),
.agent-config__full-width {
  width: 100%;
}
.agent-config__hint {
  display: block;
  margin-top: 6px;
  color: var(--adw-text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}
.agent-config__option-meta {
  float: right;
  margin-left: var(--adw-space-4);
  color: var(--adw-text-tertiary);
}
.agent-mode-grid {
  display: grid;
  width: 100%;
  grid-template-columns: 1fr 1fr;
  gap: var(--adw-space-3);
}
.agent-mode-card {
  display: grid;
  gap: 7px;
  padding: var(--adw-space-4);
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-md);
  color: var(--adw-text-primary);
  background: var(--adw-surface);
  text-align: left;
  cursor: pointer;
}
.agent-mode-card span {
  color: var(--adw-text-secondary);
  font-size: 12px;
  line-height: 1.55;
}
.agent-mode-card.active {
  border-color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
  box-shadow: 0 0 0 1px var(--adw-color-primary);
}
.agent-mode-card:disabled {
  cursor: default;
  opacity: 0.72;
}
.agent-config__section {
  padding: var(--adw-space-4);
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-md);
  background: var(--adw-surface-muted);
}
.agent-config__section > header,
.agent-mcp-row > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--adw-space-4);
}
.agent-config__section > header div,
.agent-mcp-row > header div {
  display: grid;
  gap: 4px;
}
.agent-config__section > header span,
.agent-mcp-row code,
.agent-binding-row code {
  color: var(--adw-text-tertiary);
  font-family: inherit;
  font-size: 12px;
}
.agent-config__advanced {
  border-top: 0;
}
.agent-binding-add {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: var(--adw-space-3);
}
.agent-binding-list,
.agent-mcp-list {
  display: grid;
  gap: var(--adw-space-3);
}
.agent-binding-row {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) 150px auto;
  align-items: center;
  gap: var(--adw-space-3);
  padding: var(--adw-space-3);
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-sm);
  background: var(--adw-surface);
}
.agent-binding-row > div {
  display: grid;
  min-width: 0;
  gap: 3px;
}
.agent-binding-row strong,
.agent-binding-row code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.agent-mcp-row {
  display: grid;
  gap: var(--adw-space-3);
  padding: var(--adw-space-4);
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-sm);
  background: var(--adw-surface);
}
.agent-mcp-row__policy {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--adw-space-4);
}
.agent-mcp-row__policy > span {
  color: var(--adw-text-secondary);
  font-size: 13px;
}
.agent-config__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--adw-space-4);
}
.agent-config__footer > span {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
@media (max-width: 680px) {
  .agent-config__two-columns,
  .agent-mode-grid,
  .agent-binding-row {
    grid-template-columns: 1fr;
  }
  .agent-upgrade__summary {
    grid-template-columns: 1fr;
  }
  .agent-mcp-row__policy {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
