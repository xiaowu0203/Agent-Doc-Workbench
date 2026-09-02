<template>
  <section class="skill-page">
    <PageHeader title="Skill 管理" description="上传、版本化并绑定可复用的 Agent 能力">
      <template #breadcrumb
        ><span class="skill-page__breadcrumb">工作台 / Skill 管理</span></template
      >
      <template #actions>
        <el-button
          v-if="canManage"
          type="primary"
          :icon="Upload"
          :loading="importing"
          @click="chooseImportFile"
        >
          上传 Skill ZIP
        </el-button>
        <el-button v-if="canManage" :icon="Plus" @click="openCreateDialog">
          新建 Skill
        </el-button>
        <input
          ref="importFileInput"
          class="visually-hidden"
          type="file"
          accept=".zip,application/zip"
          @change="handleImportFile"
        />
      </template>
    </PageHeader>

    <div class="skill-toolbar surface-card">
      <el-input
        v-model="keyword"
        clearable
        class="skill-toolbar__search"
        placeholder="搜索展示名称、技术标识或描述"
        aria-label="搜索 Skill"
        @clear="applyFilters"
        @keyup.enter="applyFilters"
      >
        <template #prefix
          ><el-icon><Search /></el-icon
        ></template>
      </el-input>
      <el-select
        v-model="status"
        class="skill-toolbar__select"
        aria-label="Skill 启用状态"
        @change="applyFilters"
      >
        <el-option label="全部启用状态" value="ALL" />
        <el-option label="已启用" value="ACTIVE" />
        <el-option label="已停用" value="DISABLED" />
      </el-select>
      <el-select model-value="RECENT" class="skill-toolbar__select" aria-label="Skill 排序">
        <el-option label="最近更新" value="RECENT" />
      </el-select>
      <div class="skill-toolbar__spacer"></div>
      <div class="skill-layout-toggle" aria-label="布局切换">
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
      <span class="skill-toolbar__count">共 {{ page.total }} 个 Skill</span>
    </div>

    <DataState
      :loading="loading"
      :error="errorMessage"
      :empty="!loading && !skills.length"
      loading-text="正在加载 Skill"
      :empty-text="keyword || status !== 'ALL' ? '没有匹配的 Skill' : '当前空间还没有 Skill'"
      @retry="loadSkills"
    >
      <div class="skill-collection" :class="`skill-collection--${layout}`">
        <SkillCard
          v-for="skill in skills"
          :key="String(skill.id)"
          :skill="skill"
          :layout="layout"
          :can-manage="canManage"
          @detail="openDetail($event, 'overview')"
          @versions="openDetail($event, 'versions')"
          @edit="openEditDialog"
          @upload="chooseExistingVersionFile"
          @toggle="toggleSkill"
        />
      </div>
    </DataState>

    <footer v-if="page.total > 0" class="skill-pagination">
      <span>共 {{ page.total }} 条</span>
      <el-pagination
        v-model:current-page="page.pageNum"
        v-model:page-size="page.pageSize"
        background
        layout="sizes, prev, pager, next, jumper"
        :page-sizes="[8, 12, 24, 48]"
        :total="page.total"
        @current-change="loadSkills"
        @size-change="handlePageSizeChange"
      />
    </footer>

    <el-dialog
      v-model="metadataDialogOpen"
      append-to-body
      align-center
      :lock-scroll="true"
      class="skill-create-dialog"
      :title="editingSkill ? '编辑 Skill' : '新建 Skill'"
      :width="editingSkill ? '560px' : '920px'"
    >
      <el-form label-position="top">
        <el-form-item label="展示名称" required>
          <el-input
            v-model="metadataForm.displayName"
            maxlength="100"
            placeholder="例如：文档审计"
          />
        </el-form-item>
        <el-form-item label="技术标识" required>
          <el-input
            v-model="metadataForm.name"
            maxlength="100"
            placeholder="例如：audit-document-skill"
            :disabled="Boolean(editingSkill?.versionCount)"
          />
          <span class="skill-form-hint">
            使用 kebab-case；创建任意版本后不可修改，并须与 ZIP 顶层目录及 SKILL.md.name 一致。
          </span>
        </el-form-item>
        <el-form-item label="管理描述" required>
          <el-input
            v-model="metadataForm.description"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <template v-if="!editingSkill">
          <div class="skill-create-divider">
            <strong>首个版本</strong>
            <span>填写版本指令并构建完整目录，提交后自动生成 ZIP。</span>
          </div>
          <SkillPackageBuilder ref="createPackageBuilder" :skill-name="metadataForm.name" />
        </template>
      </el-form>
      <template #footer>
        <el-button @click="metadataDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="savingMetadata" @click="saveMetadata">
          {{ editingSkill ? '保存' : '生成 ZIP 并创建' }}
        </el-button>
      </template>
    </el-dialog>

    <input
      ref="existingVersionFileInput"
      class="visually-hidden"
      type="file"
      accept=".zip,application/zip"
      @change="handleExistingVersionFile"
    />

    <SkillDetailDrawer
      :open="detailOpen"
      :skill="selectedSkill"
      :can-manage="canManage"
      :initial-tab="detailInitialTab"
      @close="detailOpen = false"
      @edit="openEditDialog"
      @refresh="refreshAfterMutation"
    />
  </section>
</template>

<script setup lang="ts">
import { Grid, List, Plus, Search, Upload } from '@element-plus/icons-vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
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
  disableSkill,
  enableSkill,
  importSkillPackage,
  searchSkills,
  updateSkill,
  uploadSkillVersion,
} from '@/features/skill/api/skill-api'
import SkillCard from '@/features/skill/components/SkillCard.vue'
import SkillDetailDrawer from '@/features/skill/components/SkillDetailDrawer.vue'
import SkillPackageBuilder from '@/features/skill/components/SkillPackageBuilder.vue'
import type { Skill, SkillPage, SkillStatus } from '@/features/skill/types'
import type { EntityId } from '@/features/workspace/types'
import DataState from '@/shared/components/DataState.vue'
import PageHeader from '@/shared/components/PageHeader.vue'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

const route = useRoute()
const workspaceStore = useWorkspaceStore()
const keyword = ref('')
const status = ref<'ALL' | SkillStatus>('ALL')
const layout = ref<'grid' | 'list'>('grid')
const loading = ref(false)
const importing = ref(false)
const errorMessage = ref('')
const skills = ref<Skill[]>([])
const page = reactive<SkillPage>({ records: [], total: 0, pageNum: 1, pageSize: 12 })
const metadataDialogOpen = ref(false)
const savingMetadata = ref(false)
const editingSkill = ref<Skill | null>(null)
const metadataForm = reactive({ name: '', displayName: '', description: '' })
const importFileInput = ref<HTMLInputElement | null>(null)
const existingVersionFileInput = ref<HTMLInputElement | null>(null)
const uploadTarget = ref<Skill | null>(null)
const detailOpen = ref(false)
const selectedSkill = ref<Skill | null>(null)
const detailInitialTab = ref<'overview' | 'versions' | 'bindings'>('overview')
const createPackageBuilder = ref<{
  buildPackage: () => Promise<File>
  reset: () => void
} | null>(null)
let requestController: AbortController | null = null

const spaceId = computed<EntityId>(() => String(route.params.spaceId))
const canManage = computed(() => workspaceStore.hasPermission(SPACE_PERMISSIONS.SKILL_MANAGE))

onMounted(loadSkills)
onBeforeUnmount(() => requestController?.abort())
watch(spaceId, () => {
  page.pageNum = 1
  detailOpen.value = false
  void loadSkills()
})

async function loadSkills(): Promise<void> {
  requestController?.abort()
  const controller = new AbortController()
  requestController = controller
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await searchSkills(spaceId.value, {
      keyword: keyword.value.trim(),
      status: status.value === 'ALL' ? undefined : status.value,
      pageNum: page.pageNum,
      pageSize: page.pageSize,
      signal: controller.signal,
    })
    skills.value = result.records
    Object.assign(page, result)
    syncSelectedSkill()
  } catch (error) {
    if (controller.signal.aborted) return
    errorMessage.value = error instanceof Error ? error.message : 'Skill 列表加载失败'
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}

function applyFilters(): void {
  page.pageNum = 1
  void loadSkills()
}

function handlePageSizeChange(): void {
  page.pageNum = 1
  void loadSkills()
}

function openCreateDialog(): void {
  editingSkill.value = null
  Object.assign(metadataForm, { name: '', displayName: '', description: '' })
  metadataDialogOpen.value = true
  createPackageBuilder.value?.reset()
}

function openEditDialog(skill: Skill): void {
  editingSkill.value = skill
  Object.assign(metadataForm, {
    name: skill.name,
    displayName: skill.displayName,
    description: skill.description,
  })
  metadataDialogOpen.value = true
}

async function saveMetadata(): Promise<void> {
  const name = metadataForm.name.trim()
  const displayName = metadataForm.displayName.trim()
  const description = metadataForm.description.trim()
  if (!name || !displayName || !description) {
    ElMessage.warning('请完整填写 Skill 信息')
    return
  }
  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(name)) {
    ElMessage.warning('技术标识必须使用 kebab-case')
    return
  }
  savingMetadata.value = true
  try {
    if (editingSkill.value) {
      await updateSkill(editingSkill.value.id, { name, displayName, description })
      ElMessage.success('Skill 信息已更新')
    } else {
      if (!createPackageBuilder.value) return
      const file = await createPackageBuilder.value.buildPackage()
      const result = await importSkillPackage(spaceId.value, file, { displayName, description })
      ElMessage.success('Skill 与首个草稿版本已创建')
      selectedSkill.value = result.skill
      detailInitialTab.value = 'versions'
      detailOpen.value = true
    }
    metadataDialogOpen.value = false
    await refreshAfterMutation()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Skill 保存失败')
  } finally {
    savingMetadata.value = false
  }
}

function chooseImportFile(): void {
  importFileInput.value?.click()
}

async function handleImportFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  importing.value = true
  try {
    const result = await importSkillPackage(spaceId.value, file)
    ElMessage.success(`${result.skill.name} 已导入，首个版本为草稿`)
    page.pageNum = 1
    await loadSkills()
    openDetail(result.skill, 'versions')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Skill ZIP 导入失败')
  } finally {
    importing.value = false
  }
}

function chooseExistingVersionFile(skill: Skill): void {
  uploadTarget.value = skill
  existingVersionFileInput.value?.click()
}

async function handleExistingVersionFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !uploadTarget.value) return
  try {
    await uploadSkillVersion(uploadTarget.value.id, file)
    ElMessage.success('草稿版本上传成功')
    await refreshAfterMutation()
    openDetail(uploadTarget.value, 'versions')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '版本上传失败')
  } finally {
    uploadTarget.value = null
  }
}

async function toggleSkill(skill: Skill): Promise<void> {
  const disabling = skill.status === 'ACTIVE'
  try {
    await ElMessageBox.confirm(
      disabling
        ? '停用后不可上传新版本或新增 Agent 绑定，是否继续？'
        : '启用后可继续上传版本和绑定 Agent。',
      disabling ? '停用 Skill' : '启用 Skill',
      { type: disabling ? 'warning' : 'info' },
    )
    if (disabling) await disableSkill(skill.id)
    else await enableSkill(skill.id)
    ElMessage.success(disabling ? 'Skill 已停用' : 'Skill 已启用')
    await refreshAfterMutation()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '状态更新失败')
  }
}

function openDetail(skill: Skill, tab: 'overview' | 'versions' | 'bindings'): void {
  selectedSkill.value = skills.value.find((item) => String(item.id) === String(skill.id)) ?? skill
  detailInitialTab.value = tab
  detailOpen.value = true
}

async function refreshAfterMutation(): Promise<void> {
  await loadSkills()
  syncSelectedSkill()
}

function syncSelectedSkill(): void {
  if (!selectedSkill.value) return
  selectedSkill.value =
    skills.value.find((item) => String(item.id) === String(selectedSkill.value?.id)) ??
    selectedSkill.value
}
</script>

<style scoped>
.skill-page {
  display: grid;
  gap: var(--adw-space-6);
}
.skill-page__breadcrumb {
  display: block;
  margin-bottom: var(--adw-space-3);
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.skill-toolbar {
  display: flex;
  align-items: center;
  gap: var(--adw-space-3);
  padding: var(--adw-space-4);
}
.skill-toolbar__search {
  width: min(330px, 32vw);
}
.skill-toolbar__select {
  width: 170px;
}
.skill-toolbar__spacer {
  flex: 1;
}
.skill-toolbar__count {
  color: var(--adw-text-secondary);
  font-size: 13px;
  white-space: nowrap;
}
.skill-layout-toggle {
  display: flex;
  gap: 4px;
}
.skill-layout-toggle button {
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
.skill-layout-toggle button.active {
  border-color: var(--adw-color-primary);
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}
.skill-collection {
  display: grid;
  gap: var(--adw-space-4);
}
.skill-collection--grid {
  grid-template-columns: repeat(auto-fill, minmax(285px, 1fr));
}
.skill-collection--list {
  grid-template-columns: 1fr;
}
.skill-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--adw-space-6);
  padding: var(--adw-space-2) 0;
  color: var(--adw-text-secondary);
  font-size: 13px;
}
.skill-form-hint {
  margin-top: 6px;
  color: var(--adw-text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}
.skill-create-divider {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 8px 0 18px;
  padding-top: 18px;
  border-top: 1px solid var(--adw-border-color-light);
}
.skill-create-divider strong {
  color: var(--adw-text-primary);
  font-size: 16px;
}
.skill-create-divider span {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
:global(.skill-create-dialog .el-dialog__body) {
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
@media (max-width: 900px) {
  .skill-toolbar {
    align-items: stretch;
    flex-wrap: wrap;
  }
  .skill-toolbar__search {
    width: 100%;
  }
  .skill-toolbar__spacer {
    display: none;
  }
}
@media (max-width: 620px) {
  .skill-toolbar__select {
    width: calc(50% - 6px);
  }
  .skill-pagination {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
