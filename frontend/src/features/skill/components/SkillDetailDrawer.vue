<template>
  <el-drawer
    :model-value="open"
    class="skill-detail-drawer"
    size="760px"
    destroy-on-close
    @close="$emit('close')"
  >
    <template #header>
      <div v-if="skill" class="skill-detail__title">
        <div>
          <span>Skill 详情</span>
          <h2>{{ skill.displayName }}</h2>
          <code>{{ skill.name }}</code>
        </div>
        <el-tag :type="skill.status === 'ACTIVE' ? 'success' : 'danger'" effect="plain">
          {{ skill.status === 'ACTIVE' ? '已启用' : '已停用' }}
        </el-tag>
      </div>
    </template>

    <template v-if="skill">
      <div class="skill-detail__summary">
        <div>
          <strong>{{ skill.versionCount }}</strong
          ><span>版本</span>
        </div>
        <div>
          <strong>{{ skill.boundAgentCount }}</strong
          ><span>绑定 Agent</span>
        </div>
        <div>
          <strong>{{ skill.latestVersion?.allowedToolCount ?? 0 }}</strong
          ><span>声明工具</span>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="skill-detail__tabs">
        <el-tab-pane label="概览" name="overview">
          <section class="skill-detail__panel">
            <header>
              <h3>管理信息</h3>
              <el-button v-if="canManage" link @click="$emit('edit', skill)">编辑</el-button>
            </header>
            <dl>
              <div>
                <dt>展示名称</dt>
                <dd>{{ skill.displayName }}</dd>
              </div>
              <div>
                <dt>技术标识</dt>
                <dd>
                  <code>{{ skill.name }}</code>
                </dd>
              </div>
              <div>
                <dt>管理描述</dt>
                <dd>{{ skill.description }}</dd>
              </div>
              <div>
                <dt>最近更新</dt>
                <dd>{{ formatDate(skill.updatedAt) }}</dd>
              </div>
            </dl>
          </section>
          <el-alert
            title="版本激活描述来自每个 ZIP 中的 SKILL.md，与可编辑的管理描述相互独立。"
            type="info"
            :closable="false"
            show-icon
          />
        </el-tab-pane>

        <el-tab-pane :label="`版本记录 ${versions.length}`" name="versions">
          <div class="skill-detail__toolbar">
            <span>上传后生成不可变草稿，发布后可供 Agent 绑定。</span>
            <div v-if="canManage">
              <el-button :disabled="skill.status === 'DISABLED'" @click="openOnlineDialog">
                在线创建
              </el-button>
              <el-button
                type="primary"
                :icon="Upload"
                :disabled="skill.status === 'DISABLED'"
                @click="chooseVersionFile"
              >
                上传 ZIP
              </el-button>
              <input
                ref="versionFileInput"
                class="visually-hidden"
                type="file"
                accept=".zip,application/zip"
                @change="handleVersionFile"
              />
            </div>
          </div>
          <DataState
            :loading="loading"
            :error="errorMessage"
            :empty="!loading && !versions.length"
            loading-text="正在加载版本"
            empty-text="还没有版本，上传 ZIP 创建首个草稿"
            @retry="loadDetail"
          >
            <div class="skill-version-list">
              <article v-for="version in versions" :key="String(version.id)" class="skill-version">
                <header>
                  <div>
                    <strong>v{{ version.versionNo }}</strong>
                    <el-tag
                      :type="version.status === 'PUBLISHED' ? 'success' : 'warning'"
                      effect="plain"
                      size="small"
                    >
                      {{ version.status === 'PUBLISHED' ? '已发布' : '草稿' }}
                    </el-tag>
                  </div>
                  <div class="skill-version__actions">
                    <el-button link @click="downloadVersion(version)">下载</el-button>
                    <el-button
                      v-if="canManage && version.status === 'DRAFT'"
                      type="primary"
                      link
                      :loading="publishingId === version.id"
                      @click="publishVersion(version)"
                    >
                      发布
                    </el-button>
                  </div>
                </header>
                <p>{{ version.activationDescription }}</p>
                <div class="skill-version__meta">
                  <span>{{ formatBytes(version.packageSize) }}</span>
                  <span>{{ version.allowedTools.length }} 个工具</span>
                  <span>{{ version.readableResourcePaths.length }} 个可读资源</span>
                  <span>{{ formatDate(version.publishedAt || version.createdAt) }}</span>
                </div>
                <details v-if="version.allowedTools.length || version.readableResourcePaths.length">
                  <summary>查看工具与资源清单</summary>
                  <div v-if="version.allowedTools.length" class="skill-version__tokens">
                    <code v-for="tool in version.allowedTools" :key="tool">{{ tool }}</code>
                  </div>
                  <ul v-if="version.readableResourcePaths.length">
                    <li v-for="path in version.readableResourcePaths" :key="path">{{ path }}</li>
                  </ul>
                </details>
              </article>
            </div>
          </DataState>
        </el-tab-pane>

        <el-tab-pane :label="`Agent 绑定 ${bindings.length}`" name="bindings">
          <DataState
            :loading="loading"
            :error="errorMessage"
            :empty="!loading && !bindings.length"
            loading-text="正在加载绑定关系"
            empty-text="当前没有 Agent 使用这个 Skill"
            @retry="loadDetail"
          >
            <div class="skill-binding-list">
              <article v-for="binding in bindings" :key="String(binding.id)">
                <span class="skill-binding-list__icon"
                  ><el-icon><User /></el-icon
                ></span>
                <div>
                  <strong>{{ binding.agentName }}</strong
                  ><span>绑定版本 v{{ binding.versionNo }}</span>
                </div>
                <el-tag
                  :type="binding.agentStatus === 'ENABLED' ? 'success' : 'info'"
                  effect="plain"
                >
                  {{ binding.agentStatus === 'ENABLED' ? 'Agent 已启用' : 'Agent 已停用' }}
                </el-tag>
              </article>
            </div>
          </DataState>
        </el-tab-pane>
      </el-tabs>
    </template>
  </el-drawer>

  <el-dialog
    v-model="onlineDialogOpen"
    append-to-body
    align-center
    :lock-scroll="true"
    class="skill-package-dialog"
    title="在线创建 Skill 版本"
    width="900px"
  >
    <el-form label-position="top">
      <SkillPackageBuilder ref="packageBuilder" :skill-name="skill?.name ?? ''" />
    </el-form>
    <template #footer>
      <el-button @click="onlineDialogOpen = false">取消</el-button>
      <el-button type="primary" :loading="uploading" @click="createOnlineVersion"
        >生成 ZIP 并上传</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { Upload, User } from '@element-plus/icons-vue'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElDrawer,
  ElForm,
  ElIcon,
  ElMessage,
  ElMessageBox,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus'
import { ref, watch } from 'vue'

import DataState from '@/shared/components/DataState.vue'
import {
  downloadSkillVersion,
  listSkillAgentBindings,
  listSkillVersions,
  publishSkillVersion,
  uploadSkillVersion,
} from '@/features/skill/api/skill-api'
import SkillPackageBuilder from '@/features/skill/components/SkillPackageBuilder.vue'
import type { Skill, SkillAgentBinding, SkillVersion } from '@/features/skill/types'

const props = defineProps<{
  open: boolean
  skill: Skill | null
  canManage: boolean
  initialTab?: 'overview' | 'versions' | 'bindings'
}>()

const emit = defineEmits<{
  close: []
  edit: [skill: Skill]
  refresh: []
}>()

const activeTab = ref<'overview' | 'versions' | 'bindings'>('overview')
const loading = ref(false)
const uploading = ref(false)
const errorMessage = ref('')
const versions = ref<SkillVersion[]>([])
const bindings = ref<SkillAgentBinding[]>([])
const publishingId = ref<SkillVersion['id'] | null>(null)
const versionFileInput = ref<HTMLInputElement | null>(null)
const onlineDialogOpen = ref(false)
const packageBuilder = ref<{ buildPackage: () => Promise<File>; reset: () => void } | null>(null)

watch(
  () => [props.open, props.skill?.id] as const,
  ([open]) => {
    if (!open || !props.skill) return
    activeTab.value = props.initialTab ?? 'overview'
    void loadDetail()
  },
  { immediate: true },
)

async function loadDetail(): Promise<void> {
  if (!props.skill) return
  loading.value = true
  errorMessage.value = ''
  try {
    ;[versions.value, bindings.value] = await Promise.all([
      listSkillVersions(props.skill.id),
      listSkillAgentBindings(props.skill.id),
    ])
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Skill 详情加载失败'
  } finally {
    loading.value = false
  }
}

function chooseVersionFile(): void {
  versionFileInput.value?.click()
}

async function handleVersionFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  await uploadVersion(file)
}

async function uploadVersion(file: globalThis.File): Promise<void> {
  if (!props.skill) return
  uploading.value = true
  try {
    await uploadSkillVersion(props.skill.id, file)
    ElMessage.success('草稿版本上传成功')
    await loadDetail()
    emit('refresh')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '版本上传失败')
  } finally {
    uploading.value = false
  }
}

function openOnlineDialog(): void {
  onlineDialogOpen.value = true
  packageBuilder.value?.reset()
}

async function createOnlineVersion(): Promise<void> {
  if (!props.skill || !packageBuilder.value) return
  try {
    const file = await packageBuilder.value.buildPackage()
    await uploadVersion(file)
    onlineDialogOpen.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Skill ZIP 生成失败')
  }
}

async function publishVersion(version: SkillVersion): Promise<void> {
  if (!props.skill) return
  await ElMessageBox.confirm(
    `发布 v${version.versionNo} 后版本内容不可修改，是否继续？`,
    '发布版本',
    {
      type: 'warning',
    },
  )
  publishingId.value = version.id
  try {
    await publishSkillVersion(props.skill.id, version.id)
    ElMessage.success(`v${version.versionNo} 已发布`)
    await loadDetail()
    emit('refresh')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '版本发布失败')
  } finally {
    publishingId.value = null
  }
}

async function downloadVersion(version: SkillVersion): Promise<void> {
  if (!props.skill) return
  try {
    await downloadSkillVersion(
      props.skill.id,
      version.id,
      `${props.skill.name}-${version.versionNo}.zip`,
    )
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '版本下载失败')
  }
}

function formatDate(value: string | null): string {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

function formatBytes(value: number): string {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}
</script>

<style scoped>
.skill-detail__title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  padding-right: 12px;
}
.skill-detail__title span {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.skill-detail__title h2 {
  margin: 4px 0 2px;
  color: var(--adw-text-primary);
  font-size: 22px;
}
.skill-detail__title code {
  color: var(--adw-text-secondary);
  font-family: inherit;
  font-size: 12px;
}
.skill-detail__summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}
.skill-detail__summary div {
  display: grid;
  gap: 4px;
  padding: 16px;
  border: 1px solid var(--adw-border-color-light);
  border-radius: 8px;
  background: var(--adw-surface-muted);
}
.skill-detail__summary strong {
  color: var(--adw-text-primary);
  font-size: 24px;
}
.skill-detail__summary span {
  color: var(--adw-text-secondary);
  font-size: 12px;
}
.skill-detail__tabs {
  min-height: 450px;
}
.skill-detail__panel {
  padding: 8px 0 20px;
}
.skill-detail__panel header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.skill-detail__panel h3 {
  margin: 0;
  font-size: 16px;
}
.skill-detail__panel dl {
  display: grid;
  gap: 0;
  margin: 12px 0 0;
  border: 1px solid var(--adw-border-color-light);
  border-radius: 8px;
}
.skill-detail__panel dl div {
  display: grid;
  grid-template-columns: 120px 1fr;
  padding: 13px 16px;
  border-bottom: 1px solid var(--adw-border-color-light);
}
.skill-detail__panel dl div:last-child {
  border-bottom: 0;
}
.skill-detail__panel dt {
  color: var(--adw-text-secondary);
}
.skill-detail__panel dd {
  margin: 0;
  color: var(--adw-text-primary);
}
.skill-detail__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  color: var(--adw-text-secondary);
  font-size: 13px;
}
.skill-detail__toolbar > div {
  display: flex;
  gap: 8px;
}
.skill-version-list {
  display: grid;
  gap: 12px;
}
.skill-version {
  padding: 16px;
  border: 1px solid var(--adw-border-color);
  border-radius: 9px;
}
.skill-version header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.skill-version header > div {
  display: flex;
  align-items: center;
  gap: 10px;
}
.skill-version p {
  margin: 12px 0;
  color: var(--adw-text-secondary);
  line-height: 1.55;
}
.skill-version__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.skill-version details {
  margin-top: 12px;
  color: var(--adw-text-secondary);
  font-size: 12px;
}
.skill-version summary {
  cursor: pointer;
}
.skill-version__tokens {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}
.skill-version__tokens code {
  padding: 3px 7px;
  border-radius: 4px;
  background: var(--adw-color-primary-soft);
  color: var(--adw-color-primary);
}
.skill-version ul {
  margin-bottom: 0;
  padding-left: 20px;
}
.skill-binding-list {
  display: grid;
  gap: 10px;
}
.skill-binding-list article {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--adw-border-color);
  border-radius: 8px;
}
.skill-binding-list article > div {
  display: grid;
  flex: 1;
  gap: 3px;
}
.skill-binding-list article span {
  color: var(--adw-text-secondary);
  font-size: 12px;
}
.skill-binding-list__icon {
  display: inline-flex;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}
:global(.skill-package-dialog .el-dialog__body) {
  max-height: calc(100vh - 190px);
  overflow-y: auto;
}
.visually-hidden {
  position: fixed;
  width: 1px;
  height: 1px;
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
}
@media (max-width: 720px) {
  .skill-detail__summary {
    grid-template-columns: 1fr;
  }
  .skill-detail__toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
