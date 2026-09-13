<template>
  <el-drawer
    :model-value="open"
    append-to-body
    destroy-on-close
    size="min(680px, 94vw)"
    :title="title"
    @update:model-value="$emit('update:open', $event)"
  >
    <DataState
      :loading="loading"
      :error="loadError"
      :empty="!loading && !capabilities.length && !(type === 'SKILL' && installations.length)"
      empty-text="暂无可安装的已发布系统能力"
      @retry="loadOptions"
    >
      <section v-if="type === 'SKILL' && installations.length" class="installed-skills">
        <h3>已安装系统 Skill</h3>
        <el-table :data="installations" size="small">
          <el-table-column label="Skill" min-width="180">
            <template #default="scope"
              >{{ scope.row.displayName }}（{{ scope.row.skillName }}）</template
            >
          </el-table-column>
          <el-table-column label="当前版本" width="100">
            <template #default="scope">v{{ scope.row.versionNo }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="scope">{{ scope.row.enabled ? '已启用' : '已停用' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="210">
            <template #default="scope">
              <el-button
                v-if="scope.row.upgradeAvailable"
                link
                type="primary"
                @click="upgradeSkill(scope.row as SpaceSkillInstallation)"
                >升级至 v{{ scope.row.latestPublishedVersionNo }}</el-button
              >
              <el-button
                link
                type="primary"
                @click="toggleInstalledSkill(scope.row as SpaceSkillInstallation)"
                >{{ scope.row.enabled ? '停用' : '启用' }}</el-button
              >
              <el-button
                link
                type="danger"
                @click="uninstallSkill(scope.row as SpaceSkillInstallation)"
                >卸载</el-button
              >
            </template>
          </el-table-column>
        </el-table>
      </section>
      <el-form label-position="top">
        <el-form-item label="系统能力" required>
          <el-select
            v-model="selectedCapabilityId"
            filterable
            placeholder="请选择系统能力"
            style="width: 100%"
            @change="loadVersions"
          >
            <el-option
              v-for="item in capabilities"
              :key="String(item.id)"
              :label="`${item.displayName}（${item.technicalKey}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="安装版本" required>
          <el-select
            v-model="selectedVersionId"
            :loading="versionsLoading"
            placeholder="请选择已发布版本"
            style="width: 100%"
            @change="checkAgentDependencies"
          >
            <el-option
              v-for="version in versions"
              :key="String(version.id)"
              :label="`v${version.versionNo}${version.displayName ? ` · ${version.displayName}` : ''}`"
              :value="version.id"
            />
          </el-select>
        </el-form-item>
        <template v-if="type === 'AGENT_TEMPLATE'">
          <el-form-item label="空间 Agent 名称">
            <el-input v-model="agentName" maxlength="100" placeholder="留空使用模板版本名称" />
          </el-form-item>
          <el-form-item label="文档访问范围">
            <el-input
              v-model="documentScope"
              type="textarea"
              :rows="4"
              placeholder="可选，填写空间侧文档范围 JSON"
            />
          </el-form-item>
          <el-alert
            v-if="dependencyLoading"
            type="info"
            :closable="false"
            show-icon
            title="正在检查当前空间的 Skill/MCP 依赖"
          />
          <el-alert
            v-else-if="missingDependencies.length"
            type="warning"
            :closable="false"
            show-icon
          >
            <template #title>
              缺少 {{ missingDependencies.length }} 项依赖，请先安装：{{
                missingDependencies.join('、')
              }}
            </template>
          </el-alert>
          <el-alert
            v-else-if="selectedAgentVersion"
            type="success"
            :closable="false"
            show-icon
            title="当前空间已满足该版本的全部 Skill/MCP 依赖"
          />
        </template>
        <template v-if="type === 'MCP_TEMPLATE' && selectedMcpVersion?.authType !== 'NONE'">
          <el-form-item
            :label="
              selectedMcpVersion?.authType === 'QUERY_PARAM' ? 'Query API Key' : 'Bearer Token'
            "
            required
          >
            <el-input
              v-model="authToken"
              type="password"
              show-password
              autocomplete="new-password"
              maxlength="4096"
            />
            <span class="install-hint">凭证只保存到当前空间，系统模板不会保存或回显凭证。</span>
          </el-form-item>
        </template>
      </el-form>
    </DataState>
    <template #footer>
      <el-button @click="$emit('update:open', false)">取消</el-button>
      <el-button
        type="primary"
        :loading="installing"
        :disabled="!selectedVersionId || dependencyLoading || missingDependencies.length > 0"
        @click="install"
        >确认安装</el-button
      >
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus'

import { normalizeApiError } from '@/api/errors'
import { searchMcpServers } from '@/features/mcp/api/mcp-api'
import {
  installAgentTemplate,
  installMcpTemplate,
  installSystemSkill,
  listSpaceSkillInstallations,
  listCapabilityVersions,
  searchSystemCapabilities,
  uninstallSystemSkill,
  updateSystemSkillInstallation,
} from '@/features/system-capability/api/system-capability-api'
import type {
  CapabilityVersion,
  SpaceSkillInstallation,
  SystemCapability,
  SystemCapabilityType,
} from '@/features/system-capability/types'
import type { EntityId } from '@/features/workspace/types'
import DataState from '@/shared/components/DataState.vue'

const props = defineProps<{ open: boolean; spaceId: EntityId; type: SystemCapabilityType }>()
const emit = defineEmits<{ 'update:open': [open: boolean]; installed: [] }>()

const loading = ref(false)
const versionsLoading = ref(false)
const installing = ref(false)
const loadError = ref('')
const capabilities = ref<SystemCapability[]>([])
const versions = ref<CapabilityVersion[]>([])
const installations = ref<SpaceSkillInstallation[]>([])
const selectedCapabilityId = ref<EntityId | null>(null)
const selectedVersionId = ref<EntityId | null>(null)
const agentName = ref('')
const documentScope = ref('')
const authToken = ref('')
const dependencyLoading = ref(false)
const missingDependencies = ref<string[]>([])

const title = computed(
  () =>
    ({
      SKILL: '安装系统 Skill',
      AGENT_TEMPLATE: '安装系统 Agent 模板',
      MCP_TEMPLATE: '安装系统 MCP 模板',
    })[props.type],
)
const selectedVersion = computed(
  () => versions.value.find((item) => String(item.id) === String(selectedVersionId.value)) || null,
)
const selectedAgentVersion = computed(() =>
  props.type === 'AGENT_TEMPLATE' ? selectedVersion.value : null,
)
const selectedMcpVersion = computed(() =>
  props.type === 'MCP_TEMPLATE' ? selectedVersion.value : null,
)

watch(
  () => props.open,
  (open) => {
    if (open) void loadOptions()
    else reset()
  },
)

async function loadOptions(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const [page, installed] = await Promise.all([
      searchSystemCapabilities({ type: props.type, status: 1, pageNum: 1, pageSize: 100 }),
      props.type === 'SKILL' ? listSpaceSkillInstallations(props.spaceId) : Promise.resolve([]),
    ])
    installations.value = installed
    const installedIds = new Set(installed.map((item) => String(item.skillId)))
    capabilities.value = page.records.filter(
      (item) => item.latestPublishedVersionId !== null && !installedIds.has(String(item.id)),
    )
  } catch (error) {
    loadError.value = normalizeApiError(error).message
  } finally {
    loading.value = false
  }
}

async function upgradeSkill(item: SpaceSkillInstallation): Promise<void> {
  try {
    await updateSystemSkillInstallation(props.spaceId, item.id, {
      skillVersionId: item.latestPublishedVersionId,
    })
    ElMessage.success('系统 Skill 已升级')
    await loadOptions()
    emit('installed')
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  }
}

async function toggleInstalledSkill(item: SpaceSkillInstallation): Promise<void> {
  try {
    await updateSystemSkillInstallation(props.spaceId, item.id, { enabled: !item.enabled })
    ElMessage.success(`系统 Skill 已${item.enabled ? '停用' : '启用'}`)
    await loadOptions()
    emit('installed')
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  }
}

async function uninstallSkill(item: SpaceSkillInstallation): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定卸载“${item.displayName}”吗？存在 Agent 绑定时服务端会拒绝卸载。`,
      '卸载系统 Skill',
      { type: 'warning', confirmButtonText: '卸载' },
    )
    await uninstallSystemSkill(props.spaceId, item.id)
    ElMessage.success('系统 Skill 已卸载')
    await loadOptions()
    emit('installed')
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(normalizeApiError(error).message)
  }
}

async function loadVersions(): Promise<void> {
  selectedVersionId.value = null
  versions.value = []
  if (!selectedCapabilityId.value) return
  versionsLoading.value = true
  try {
    versions.value = (await listCapabilityVersions(props.type, selectedCapabilityId.value)).filter(
      (item) => item.status === 1 || item.status === 'PUBLISHED',
    )
    selectedVersionId.value = versions.value[0]?.id || null
    await checkAgentDependencies()
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    versionsLoading.value = false
  }
}

async function checkAgentDependencies(): Promise<void> {
  missingDependencies.value = []
  if (props.type !== 'AGENT_TEMPLATE' || !selectedAgentVersion.value) return
  dependencyLoading.value = true
  try {
    const [skillInstallations, mcpPage, skillCatalog, mcpCatalog] = await Promise.all([
      listSpaceSkillInstallations(props.spaceId),
      searchMcpServers(props.spaceId, { status: 1, pageNum: 1, pageSize: 100 }),
      searchSystemCapabilities({ type: 'SKILL', pageNum: 1, pageSize: 100 }),
      searchSystemCapabilities({ type: 'MCP_TEMPLATE', pageNum: 1, pageSize: 100 }),
    ])
    const installedSkillVersions = new Set(
      skillInstallations.filter((item) => item.enabled).map((item) => String(item.skillVersionId)),
    )
    const installedMcpVersions = new Set(
      mcpPage.records
        .filter((item) => item.templateVersionId !== null)
        .map((item) => String(item.templateVersionId)),
    )
    const skillNames = new Map(
      skillCatalog.records.map((item) => [
        String(item.id),
        `${item.displayName}（${item.technicalKey}）`,
      ]),
    )
    const mcpNames = new Map(
      mcpCatalog.records.map((item) => [
        String(item.id),
        `${item.displayName}（${item.technicalKey}）`,
      ]),
    )
    const missingSkills = (selectedAgentVersion.value.skills || [])
      .filter((item) => !installedSkillVersions.has(String(item.skillVersionId)))
      .map(
        (item) => `Skill ${skillNames.get(String(item.skillId)) || `版本 ${item.skillVersionId}`}`,
      )
    const missingMcps = (selectedAgentVersion.value.mcps || [])
      .filter((item) => !installedMcpVersions.has(String(item.mcpTemplateVersionId)))
      .map(
        (item) =>
          `MCP ${mcpNames.get(String(item.mcpTemplateId)) || `版本 ${item.mcpTemplateVersionId}`}`,
      )
    missingDependencies.value = [...missingSkills, ...missingMcps]
  } catch (error) {
    missingDependencies.value = ['依赖检查失败，请刷新后重试']
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    dependencyLoading.value = false
  }
}

async function install(): Promise<void> {
  if (!selectedCapabilityId.value || !selectedVersionId.value) return
  if (
    props.type === 'MCP_TEMPLATE' &&
    selectedMcpVersion.value?.authType !== 'NONE' &&
    !authToken.value.trim()
  ) {
    ElMessage.warning('请填写当前空间使用的 MCP 凭证')
    return
  }
  installing.value = true
  try {
    if (props.type === 'SKILL') {
      await installSystemSkill(props.spaceId, selectedCapabilityId.value, selectedVersionId.value)
    } else if (props.type === 'AGENT_TEMPLATE') {
      await installAgentTemplate(props.spaceId, {
        templateVersionId: selectedVersionId.value,
        name: agentName.value.trim() || undefined,
        documentScope: documentScope.value.trim() || undefined,
      })
    } else {
      await installMcpTemplate(props.spaceId, {
        templateVersionId: selectedVersionId.value,
        authToken: authToken.value.trim() || undefined,
      })
    }
    ElMessage.success('系统能力已安装到当前空间')
    emit('installed')
    emit('update:open', false)
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    installing.value = false
  }
}

function reset(): void {
  capabilities.value = []
  versions.value = []
  installations.value = []
  selectedCapabilityId.value = null
  selectedVersionId.value = null
  agentName.value = ''
  documentScope.value = ''
  authToken.value = ''
  dependencyLoading.value = false
  missingDependencies.value = []
  loadError.value = ''
}
</script>

<style scoped>
.install-hint {
  margin-top: 6px;
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.installed-skills {
  margin-bottom: var(--adw-space-5);
  padding-bottom: var(--adw-space-5);
  border-bottom: 1px solid var(--adw-border-color-light);
}
.installed-skills h3 {
  margin: 0 0 var(--adw-space-3);
  font-size: 16px;
}
</style>
