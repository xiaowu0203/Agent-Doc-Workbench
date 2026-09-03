<template>
  <el-drawer
    :model-value="open"
    append-to-body
    destroy-on-close
    class="agent-config-drawer"
    :title="agentId ? 'Agent 配置' : '新建 Agent'"
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

        <el-tabs v-model="activeTab">
          <el-tab-pane label="基础与执行" name="basic">
            <el-form label-position="top" class="agent-config__form">
              <div class="agent-config__two-columns">
                <el-form-item label="Agent 名称" required>
                  <el-input
                    v-model="form.name"
                    maxlength="100"
                    placeholder="例如：文档审计 Agent"
                    :disabled="!canManage"
                  />
                </el-form-item>
                <el-form-item label="执行状态">
                  <el-switch
                    v-model="form.enabled"
                    active-text="已启用"
                    inactive-text="已停用"
                    :disabled="!canManage || !activeAgentId"
                  />
                </el-form-item>
              </div>

              <el-form-item label="Agent 描述">
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
                  <el-form-item label="文档访问范围 JSON">
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

          <el-tab-pane :label="`Skill 绑定 (${skillRows.length})`" name="skills">
            <section class="agent-config__section">
              <header>
                <div>
                  <strong>绑定不可变 Skill 版本</strong>
                  <span>工具统计包含这里所有 Skill 声明的工具，不受 ROUTER 当次选择影响。</span>
                </div>
              </header>

              <div v-if="canBindSkill && canReadSkill" class="agent-binding-add">
                <el-select v-model="skillToAdd" filterable placeholder="选择一个已启用 Skill">
                  <el-option
                    v-for="skill in availableSkills"
                    :key="String(skill.id)"
                    :label="skill.displayName"
                    :value="skill.id"
                  >
                    <span>{{ skill.displayName }}</span>
                    <small class="agent-config__option-meta">{{ skill.name }}</small>
                  </el-option>
                </el-select>
                <el-button :disabled="!skillToAdd" :loading="addingSkill" @click="addSkill">
                  添加绑定
                </el-button>
              </div>

              <el-empty v-if="!skillRows.length" description="尚未绑定 Skill" :image-size="76" />
              <div v-else class="agent-binding-list">
                <article
                  v-for="row in skillRows"
                  :key="String(row.skillId)"
                  class="agent-binding-row"
                >
                  <div>
                    <strong>{{ row.skillDisplayName }}</strong>
                    <code>{{ row.skillName }}</code>
                  </div>
                  <el-select
                    v-model="row.skillVersionId"
                    :loading="row.loadingVersions"
                    :disabled="!canBindSkill"
                    placeholder="选择已发布版本"
                    @visible-change="(visible: boolean) => visible && loadVersions(row)"
                  >
                    <el-option
                      v-for="version in row.versions"
                      :key="String(version.id)"
                      :label="`v${version.versionNo}`"
                      :value="version.id"
                    />
                  </el-select>
                  <el-button
                    v-if="canBindSkill"
                    type="danger"
                    link
                    @click="removeSkill(row.skillId)"
                  >
                    移除
                  </el-button>
                </article>
              </div>
              <el-alert
                v-if="canBindSkill && !canReadSkill"
                title="当前账号可以修改绑定，但缺少 skill:read，无法选择新的 Skill。"
                type="warning"
                :closable="false"
              />
            </section>
          </el-tab-pane>

          <el-tab-pane :label="`MCP 绑定 (${mcpRows.length})`" name="mcp">
            <section class="agent-config__section">
              <header class="agent-config__switch-heading">
                <div>
                  <strong>外部 MCP</strong>
                  <span>总开关关闭时保留绑定配置，但执行时不会连接外部服务。</span>
                </div>
                <el-switch v-model="form.externalMcpEnabled" :disabled="!canManage" />
              </header>

              <div v-if="canBindMcp && canReadMcp" class="agent-binding-add">
                <el-select v-model="mcpToAdd" filterable placeholder="选择一个已启用 MCP 服务">
                  <el-option
                    v-for="server in availableMcpServers"
                    :key="String(server.id)"
                    :label="server.displayName"
                    :value="server.id"
                  >
                    <span>{{ server.displayName }}</span>
                    <small class="agent-config__option-meta">{{ server.serverKey }}</small>
                  </el-option>
                </el-select>
                <el-button :disabled="!mcpToAdd" @click="addMcp">添加绑定</el-button>
              </div>

              <el-empty v-if="!mcpRows.length" description="尚未绑定外部 MCP" :image-size="76" />
              <div v-else class="agent-mcp-list">
                <article
                  v-for="row in mcpRows"
                  :key="String(row.mcpServerId)"
                  class="agent-mcp-row"
                >
                  <header>
                    <div>
                      <strong>{{ row.displayName }}</strong>
                      <code>{{ row.serverKey }}</code>
                    </div>
                    <el-button
                      v-if="canBindMcp"
                      type="danger"
                      link
                      @click="removeMcp(row.mcpServerId)"
                    >
                      移除
                    </el-button>
                  </header>
                  <div class="agent-mcp-row__policy">
                    <span>工具白名单</span>
                    <el-radio-group
                      v-model="row.mode"
                      size="small"
                      :disabled="!canBindMcp"
                      @change="handleMcpModeChange(row)"
                    >
                      <el-radio-button value="ALL">全部发现工具</el-radio-button>
                      <el-radio-button value="CUSTOM">指定工具</el-radio-button>
                      <el-radio-button value="NONE">禁用全部</el-radio-button>
                    </el-radio-group>
                  </div>
                  <el-select
                    v-if="row.mode === 'CUSTOM'"
                    v-model="row.toolWhitelist"
                    class="agent-config__full-width"
                    multiple
                    filterable
                    :loading="row.loadingTools"
                    :disabled="!canBindMcp"
                    placeholder="选择该服务允许调用的工具"
                    @visible-change="(visible: boolean) => visible && loadMcpTools(row)"
                  >
                    <el-option
                      v-for="tool in row.tools"
                      :key="tool.name"
                      :label="tool.name"
                      :value="tool.name"
                    >
                      <span>{{ tool.name }}</span>
                      <small class="agent-config__option-meta">{{
                        tool.description || '无描述'
                      }}</small>
                    </el-option>
                  </el-select>
                </article>
              </div>
              <el-alert
                v-if="canBindMcp && !canReadMcp"
                title="当前账号可以修改绑定，但缺少 mcp:read，无法选择新的 MCP 服务或工具。"
                type="warning"
                :closable="false"
              />
            </section>
          </el-tab-pane>
        </el-tabs>
      </div>
    </DataState>

    <template #footer>
      <div class="agent-config__footer">
        <span v-if="currentConfigVersion">当前配置版本 v{{ currentConfigVersion }}</span>
        <div>
          <el-button @click="emit('update:open', false)">{{ canSave ? '取消' : '关闭' }}</el-button>
          <el-button v-if="canSave" type="primary" :loading="saving" @click="saveConfiguration">
            保存配置
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
} from '@/features/agent/api/agent-api'
import type {
  AgentDetail,
  AgentInput,
  AgentMcpBinding,
  AgentSkillBinding,
  ModelOption,
  SkillSelectionMode,
} from '@/features/agent/types'
import { listMcpTools, searchMcpServers } from '@/features/mcp/api/mcp-api'
import type { McpServer, McpTool } from '@/features/mcp/types'
import { listSkillVersions, searchSkills } from '@/features/skill/api/skill-api'
import type { Skill, SkillVersion } from '@/features/skill/types'
import type { EntityId } from '@/features/workspace/types'
import DataState from '@/shared/components/DataState.vue'

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

const props = defineProps<{
  open: boolean
  agentId: EntityId | null
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

const activeTab = ref('basic')
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const activeAgentId = ref<EntityId | null>(null)
const currentConfigVersion = ref<number | null>(null)
const models = ref<ModelOption[]>([])
const skills = ref<Skill[]>([])
const mcpServers = ref<McpServer[]>([])
const skillRows = ref<SkillRow[]>([])
const mcpRows = ref<McpRow[]>([])
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

watch(
  () => props.open,
  (open) => {
    if (open) void loadConfiguration()
    else loadSequence++
  },
)

async function loadConfiguration(): Promise<void> {
  const sequence = ++loadSequence
  loading.value = true
  loadError.value = ''
  activeTab.value = 'basic'
  activeAgentId.value = props.agentId
  currentConfigVersion.value = null
  skillRows.value = []
  mcpRows.value = []
  skillToAdd.value = null
  mcpToAdd.value = null
  resetForm()
  try {
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
        props.canReadSkill ? loadAllSkills() : Promise.resolve<Skill[]>([]),
        props.canReadMcp ? loadAllMcpServers() : Promise.resolve<McpServer[]>([]),
      ])
    if (sequence !== loadSequence) return
    models.value = modelOptions
    skills.value = skillOptions
    mcpServers.value = serverOptions
    if (detail) applyDetail(detail)
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

async function saveConfiguration(): Promise<void> {
  if (!validateForm()) return
  saving.value = true
  const bindingErrors: string[] = []
  try {
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
  .agent-mcp-row__policy {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
