<template>
  <section class="department-page">
    <PageHeader title="部门管理" description="维护平台组织树、负责人和用户归属">
      <template #breadcrumb><span class="breadcrumb">平台管理 / 部门管理</span></template>
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="openCreate(null)">新建部门</el-button>
      </template>
    </PageHeader>

    <el-alert
      title="部门仅用于组织归属；用户访问文档仍需加入对应 Space 并获得空间角色。"
      type="info"
      :closable="false"
      show-icon
    />

    <div class="stat-grid">
      <article class="stat-card surface-card">
        <span>部门</span><strong>{{ departments.length }}</strong>
      </article>
      <article class="stat-card surface-card stat-card--success">
        <span>成员</span><strong>{{ totalMembers }}</strong>
      </article>
      <article class="stat-card surface-card stat-card--warning">
        <span>未分配用户</span><strong>{{ userStats.unassignedDepartment }}</strong>
      </article>
    </div>

    <DataState
      :loading="loading"
      :error="errorMessage"
      :empty="!loading && departments.length === 0"
      loading-text="正在加载组织架构"
      empty-text="尚未创建部门"
      @retry="loadPage"
    >
      <div class="department-layout">
        <aside class="tree-panel surface-card">
          <div class="panel-heading">
            <div>
              <h2>组织架构</h2>
              <span>{{ departments.length }} 个部门</span>
            </div>
            <el-button :icon="Refresh" circle aria-label="刷新部门" @click="loadPage" />
          </div>
          <el-input v-model="treeKeyword" clearable placeholder="搜索部门">
            <template #prefix
              ><el-icon><Search /></el-icon
            ></template>
          </el-input>
          <el-tree
            ref="treeRef"
            class="department-tree"
            node-key="id"
            :data="departmentTree"
            :props="{ label: 'name', children: 'children' }"
            :filter-node-method="filterTreeNode"
            :expand-on-click-node="false"
            default-expand-all
            highlight-current
            @node-click="selectDepartment"
          >
            <template #default="{ data }">
              <span class="tree-node"
                ><span>{{ data.name }}</span
                ><small>{{ data.memberCount }} 人</small></span
              >
            </template>
          </el-tree>
        </aside>

        <main v-if="selectedDepartment" class="detail-panel surface-card">
          <header class="detail-heading">
            <div>
              <div class="title-row">
                <h2>{{ selectedDepartment.name }}</h2>
                <el-tag :type="selectedDepartment.status === 1 ? 'success' : 'info'">
                  {{ selectedDepartment.status === 1 ? '已启用' : '已停用' }}
                </el-tag>
              </div>
              <p>{{ departmentPath(selectedDepartment) }}</p>
            </div>
            <div>
              <el-button :icon="Plus" @click="openCreate(selectedDepartment)">添加子部门</el-button>
              <el-button :icon="EditPen" @click="openEdit(selectedDepartment)">编辑</el-button>
              <el-button type="danger" plain :icon="Delete" @click="deleteSelected">删除</el-button>
            </div>
          </header>

          <dl class="department-facts">
            <div>
              <dt>部门编码</dt>
              <dd>{{ selectedDepartment.code }}</dd>
            </div>
            <div>
              <dt>负责人</dt>
              <dd>{{ selectedDepartment.leaderName || '未设置' }}</dd>
            </div>
            <div>
              <dt>直属成员</dt>
              <dd>{{ selectedDepartment.memberCount }}</dd>
            </div>
            <div>
              <dt>子部门</dt>
              <dd>{{ selectedDepartment.childCount }}</dd>
            </div>
            <div>
              <dt>更新时间</dt>
              <dd>{{ formatDate(selectedDepartment.updatedAt) }}</dd>
            </div>
          </dl>

          <section class="member-section">
            <div class="section-heading">
              <div>
                <h3>部门成员</h3>
                <span>直属成员最多展示 100 人</span>
              </div>
              <el-button link type="primary" @click="goToUsers">前往用户管理</el-button>
            </div>
            <el-table v-loading="membersLoading" :data="departmentMembers" row-key="id">
              <el-table-column label="成员" min-width="180">
                <template #default="{ row }"
                  ><strong>{{ row.nickname }}</strong
                  ><small class="cell-subtitle">{{ row.username }}</small></template
                >
              </el-table-column>
              <el-table-column label="邮箱" min-width="190"
                ><template #default="{ row }">{{ row.email || '—' }}</template></el-table-column
              >
              <el-table-column label="职位" min-width="150"
                ><template #default="{ row }">{{ row.jobTitle || '—' }}</template></el-table-column
              >
              <el-table-column label="状态" width="100">
                <template #default="{ row }"
                  ><el-tag :type="row.status === 1 ? 'success' : 'danger'">{{
                    row.status === 1 ? '已启用' : '已停用'
                  }}</el-tag></template
                >
              </el-table-column>
            </el-table>
          </section>
        </main>

        <aside v-if="selectedDepartment" class="space-panel surface-card">
          <div class="panel-heading">
            <div>
              <h2>相关空间</h2>
              <span>由成员关系汇总</span>
            </div>
          </div>
          <div v-if="relatedSpaces.length" class="space-list">
            <div v-for="space in relatedSpaces" :key="String(space.id)">
              <strong>{{ space.name }}</strong
              ><span>{{ space.memberCount }} 位部门成员参与</span>
            </div>
          </div>
          <p v-else class="empty-copy">直属成员尚未加入任何空间</p>
        </aside>
      </div>
    </DataState>

    <el-drawer
      v-model="drawerOpen"
      :title="editingDepartment ? '编辑部门' : '新建部门'"
      size="500px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="部门名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="部门编码" prop="code">
          <el-input
            v-model="form.code"
            :disabled="Boolean(editingDepartment)"
            placeholder="例如 PRODUCT_RD"
          />
        </el-form-item>
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="form.parentId"
            clearable
            check-strictly
            :data="parentDepartmentOptions"
            :props="{ label: 'name', children: 'children', value: 'id' }"
            placeholder="无上级部门"
          />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="form.leaderUserId" clearable filterable placeholder="未设置负责人">
            <el-option
              v-for="user in leaderCandidates"
              :key="String(user.id)"
              :label="`${user.nickname || user.username} (${user.username})`"
              :value="String(user.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"
          ><el-input-number v-model="form.sortOrder" :min="0"
        /></el-form-item>
        <el-form-item label="状态"
          ><el-switch v-model="form.enabled" active-text="启用" inactive-text="停用"
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDepartment">保存</el-button>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { Delete, EditPen, Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  ElAlert,
  ElButton,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTree,
  ElTreeSelect,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { normalizeApiError } from '@/api/errors'
import {
  createDepartment,
  deleteDepartment,
  getPlatformUserStats,
  listDepartments,
  queryPlatformUserMemberships,
  searchPlatformUsers,
  updateDepartment,
} from '@/features/platform-management/api/platform-management-api'
import type {
  Department,
  PlatformUser,
  PlatformUserMembership,
  PlatformUserStats,
} from '@/features/platform-management/types'
import DataState from '@/shared/components/DataState.vue'
import PageHeader from '@/shared/components/PageHeader.vue'

interface DepartmentTreeNode extends Department {
  children: DepartmentTreeNode[]
}
interface DepartmentTreeInstance {
  filter: (value: string) => void
  setCurrentKey: (key: string | number) => void
}

const router = useRouter()
const departments = ref<Department[]>([])
const selectedDepartment = ref<Department | null>(null)
const departmentMembers = ref<PlatformUser[]>([])
const memberMemberships = ref<PlatformUserMembership[]>([])
const leaderCandidates = ref<PlatformUser[]>([])
const userStats = reactive<PlatformUserStats>({
  total: 0,
  enabled: 0,
  disabled: 0,
  unassignedDepartment: 0,
})
const loading = ref(false)
const membersLoading = ref(false)
const errorMessage = ref('')
const treeKeyword = ref('')
const treeRef = ref<DepartmentTreeInstance>()
const drawerOpen = ref(false)
const editingDepartment = ref<Department | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  code: '',
  parentId: '' as string,
  leaderUserId: '' as string,
  sortOrder: 0,
  enabled: true,
})
const rules: FormRules = {
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入部门编码', trigger: 'blur' },
    { pattern: /^[A-Z0-9_-]+$/, message: '仅允许大写字母、数字、下划线和连字符', trigger: 'blur' },
  ],
}

const departmentTree = computed(() => buildTree(departments.value))
const parentDepartmentOptions = computed(() =>
  buildTree(
    departments.value.filter(
      (department) =>
        !editingDepartment.value || !isDescendantOrSelf(department.id, editingDepartment.value.id),
    ),
  ),
)
const totalMembers = computed(() =>
  departments.value.reduce((total, department) => total + department.memberCount, 0),
)
const relatedSpaces = computed(() => {
  const spaces = new Map<string, { id: string | number; name: string; memberIds: Set<string> }>()
  for (const membership of memberMemberships.value) {
    const key = String(membership.spaceId)
    const existing = spaces.get(key) ?? {
      id: membership.spaceId,
      name: membership.spaceName,
      memberIds: new Set<string>(),
    }
    existing.memberIds.add(String(membership.userId))
    spaces.set(key, existing)
  }
  return [...spaces.values()].map((space) => ({
    id: space.id,
    name: space.name,
    memberCount: space.memberIds.size,
  }))
})

watch(treeKeyword, (value) => treeRef.value?.filter(value))
onMounted(() => {
  void loadPage()
})

async function loadPage(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const [departmentList, stats, leaders] = await Promise.all([
      listDepartments(),
      getPlatformUserStats(),
      searchPlatformUsers({ status: 1, pageSize: 100 }),
    ])
    departments.value = departmentList
    Object.assign(userStats, stats)
    leaderCandidates.value = leaders.records
    const selectedId = selectedDepartment.value?.id
    selectedDepartment.value =
      departmentList.find((item) => String(item.id) === String(selectedId)) ??
      departmentList[0] ??
      null
    await nextTick()
    if (selectedDepartment.value) treeRef.value?.setCurrentKey(selectedDepartment.value.id)
    await loadDepartmentMembers()
  } catch (error) {
    errorMessage.value = normalizeApiError(error).message
  } finally {
    loading.value = false
  }
}

async function selectDepartment(department: Department): Promise<void> {
  selectedDepartment.value = department
  await loadDepartmentMembers()
}

async function loadDepartmentMembers(): Promise<void> {
  if (!selectedDepartment.value) {
    departmentMembers.value = []
    memberMemberships.value = []
    return
  }
  membersLoading.value = true
  try {
    const result = await searchPlatformUsers({
      departmentId: selectedDepartment.value.id,
      pageSize: 100,
    })
    departmentMembers.value = result.records
    memberMemberships.value = await queryPlatformUserMemberships(
      result.records.map((user) => user.id),
    )
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    membersLoading.value = false
  }
}

function openCreate(parent: Department | null): void {
  editingDepartment.value = null
  Object.assign(form, {
    name: '',
    code: '',
    parentId: parent ? String(parent.id) : '',
    leaderUserId: '',
    sortOrder: 0,
    enabled: true,
  })
  formRef.value?.clearValidate()
  drawerOpen.value = true
}
function openEdit(department: Department): void {
  editingDepartment.value = department
  Object.assign(form, {
    name: department.name,
    code: department.code,
    parentId: Number(department.parentId) === 0 ? '' : String(department.parentId),
    leaderUserId: department.leaderUserId ? String(department.leaderUserId) : '',
    sortOrder: department.sortOrder,
    enabled: department.status === 1,
  })
  drawerOpen.value = true
}

async function saveDepartment(): Promise<void> {
  if (!formRef.value || !(await formRef.value.validate().catch(() => false))) return
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      parentId: form.parentId || 0,
      leaderUserId: form.leaderUserId || undefined,
      sortOrder: form.sortOrder,
      status: (form.enabled ? 1 : 0) as 0 | 1,
    }
    const saved = editingDepartment.value
      ? await updateDepartment(editingDepartment.value.id, payload)
      : await createDepartment({ ...payload, code: form.code.trim() })
    selectedDepartment.value = saved
    drawerOpen.value = false
    ElMessage.success(editingDepartment.value ? '部门已更新' : '部门已创建')
    await loadPage()
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    saving.value = false
  }
}

async function deleteSelected(): Promise<void> {
  if (!selectedDepartment.value) return
  try {
    await ElMessageBox.confirm(
      `确定删除“${selectedDepartment.value.name}”吗？仅空部门可删除。`,
      '删除部门',
      { type: 'warning' },
    )
    await deleteDepartment(selectedDepartment.value.id)
    selectedDepartment.value = null
    ElMessage.success('部门已删除')
    await loadPage()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(normalizeApiError(error).message)
  }
}

function buildTree(items: Department[]): DepartmentTreeNode[] {
  const nodes = new Map(
    items.map((item) => [String(item.id), { ...item, children: [] as DepartmentTreeNode[] }]),
  )
  const roots: DepartmentTreeNode[] = []
  for (const node of nodes.values()) {
    const parent = nodes.get(String(node.parentId))
    if (parent && String(node.parentId) !== '0') parent.children.push(node)
    else roots.push(node)
  }
  return roots
}
function isDescendantOrSelf(candidateId: string | number, departmentId: string | number): boolean {
  let cursor = departments.value.find((item) => String(item.id) === String(candidateId))
  while (cursor) {
    if (String(cursor.id) === String(departmentId)) return true
    if (String(cursor.parentId) === '0') return false
    cursor = departments.value.find((item) => String(item.id) === String(cursor?.parentId))
  }
  return false
}
function filterTreeNode(value: string, data: Department): boolean {
  return (
    !value.trim() || `${data.name} ${data.code}`.toLowerCase().includes(value.trim().toLowerCase())
  )
}
function departmentPath(department: Department): string {
  const names = [department.name]
  let parentId = department.parentId
  while (String(parentId) !== '0') {
    const parent = departments.value.find((item) => String(item.id) === String(parentId))
    if (!parent) break
    names.unshift(parent.name)
    parentId = parent.parentId
  }
  return names.join(' / ')
}
function formatDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}
function goToUsers(): void {
  void router.push({
    path: '/system/users',
    query: { departmentId: String(selectedDepartment.value?.id ?? '') },
  })
}
</script>

<style scoped>
.department-page {
  display: grid;
  gap: var(--adw-space-5);
}
.breadcrumb {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--adw-space-4);
}
.stat-card {
  display: grid;
  gap: 8px;
  padding: 20px 22px;
  border-left: 4px solid var(--adw-color-primary);
}
.stat-card span,
.panel-heading span,
.section-heading span {
  color: var(--adw-text-secondary);
  font-size: 13px;
}
.stat-card strong {
  font-size: 28px;
}
.stat-card--success {
  border-left-color: var(--adw-color-success);
}
.stat-card--warning {
  border-left-color: var(--adw-color-warning);
}
.department-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr) 260px;
  gap: var(--adw-space-4);
  align-items: stretch;
}
.tree-panel,
.detail-panel,
.space-panel {
  min-height: 580px;
  padding: 18px;
}
.panel-heading,
.detail-heading,
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.panel-heading {
  margin-bottom: 16px;
}
.panel-heading h2,
.detail-heading h2,
.section-heading h3 {
  margin: 0;
}
.panel-heading div,
.section-heading div {
  display: grid;
  gap: 4px;
}
.department-tree {
  margin-top: 14px;
}
.tree-node {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  padding-right: 8px;
}
.tree-node small {
  color: var(--adw-text-tertiary);
}
.detail-heading {
  padding-bottom: 18px;
  border-bottom: 1px solid var(--adw-border-color);
}
.detail-heading p {
  margin: 7px 0 0;
  color: var(--adw-text-secondary);
}
.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.department-facts {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
  margin: 20px 0;
}
.department-facts div {
  display: grid;
  gap: 6px;
}
.department-facts dt {
  color: var(--adw-text-secondary);
  font-size: 13px;
}
.department-facts dd {
  margin: 0;
  font-weight: 600;
}
.member-section {
  border-top: 1px solid var(--adw-border-color);
  padding-top: 18px;
}
.section-heading {
  margin-bottom: 12px;
}
.cell-subtitle {
  display: block;
  color: var(--adw-text-tertiary);
}
.space-list {
  display: grid;
  gap: 10px;
}
.space-list div {
  display: grid;
  gap: 4px;
  padding: 12px;
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
}
.space-list span,
.empty-copy {
  color: var(--adw-text-secondary);
  font-size: 12px;
}
:deep(.el-tree-select),
:deep(.el-select) {
  width: 100%;
}
@media (max-width: 1200px) {
  .department-layout {
    grid-template-columns: 280px 1fr;
  }
  .space-panel {
    grid-column: 1 / -1;
    min-height: auto;
  }
  .space-list {
    grid-template-columns: repeat(3, 1fr);
  }
}
@media (max-width: 760px) {
  .stat-grid,
  .department-layout,
  .department-facts {
    grid-template-columns: 1fr;
  }
  .tree-panel,
  .detail-panel,
  .space-panel {
    min-height: auto;
  }
  .detail-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .space-list {
    grid-template-columns: 1fr;
  }
}
</style>
