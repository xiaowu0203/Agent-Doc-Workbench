<template>
  <section class="platform-user-page">
    <PageHeader title="用户管理" description="管理平台账号、组织归属与平台管理员身份">
      <template #breadcrumb><span class="breadcrumb">平台管理 / 用户管理</span></template>
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="openCreate">创建用户</el-button>
      </template>
    </PageHeader>

    <el-alert
      title="用户账号属于平台；Space 访问仍需在对应空间的成员管理中显式授予。"
      type="info"
      :closable="false"
      show-icon
    />

    <div class="stat-grid">
      <article class="stat-card surface-card">
        <span>用户总数</span><strong>{{ stats.total }}</strong>
      </article>
      <article class="stat-card surface-card stat-card--success">
        <span>已启用</span><strong>{{ stats.enabled }}</strong>
      </article>
      <article class="stat-card surface-card stat-card--danger">
        <span>已停用</span><strong>{{ stats.disabled }}</strong>
      </article>
      <article class="stat-card surface-card stat-card--warning">
        <span>未分配部门</span><strong>{{ stats.unassignedDepartment }}</strong>
      </article>
    </div>

    <div class="toolbar surface-card">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索姓名、用户名或邮箱"
        @clear="applyFilters"
        @keyup.enter="applyFilters"
      >
        <template #prefix
          ><el-icon><Search /></el-icon
        ></template>
      </el-input>
      <el-select v-model="departmentFilter" @change="applyFilters">
        <el-option label="全部部门" value="ALL" />
        <el-option label="未分配部门" value="UNASSIGNED" />
        <el-option
          v-for="department in departments"
          :key="String(department.id)"
          :label="department.name"
          :value="String(department.id)"
        />
      </el-select>
      <el-select v-model="statusFilter" @change="applyFilters">
        <el-option label="全部状态" value="ALL" />
        <el-option label="已启用" value="1" />
        <el-option label="已停用" value="0" />
      </el-select>
      <el-select v-model="roleFilter" @change="applyFilters">
        <el-option label="全部平台身份" value="ALL" />
        <el-option label="平台超级管理员" :value="PLATFORM_ROLES.SUPER_ADMIN" />
      </el-select>
      <el-button :icon="Refresh" aria-label="刷新用户" @click="refreshAll" />
    </div>

    <DataState
      :loading="loading"
      :error="errorMessage"
      :empty="!loading && users.length === 0"
      loading-text="正在加载平台用户"
      :empty-text="hasFilters ? '没有匹配的用户' : '尚无平台用户'"
      @retry="loadUsers"
    >
      <div class="user-table surface-card">
        <el-table :data="users" row-key="id">
          <el-table-column label="用户" min-width="220">
            <template #default="{ row }">
              <div class="user-cell">
                <span class="avatar">{{ initials(row) }}</span>
                <div>
                  <strong>{{ row.nickname || row.username }}</strong
                  ><small>{{ row.username }}</small>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="邮箱" min-width="190">
            <template #default="{ row }">{{ row.email || '—' }}</template>
          </el-table-column>
          <el-table-column label="所属部门" min-width="150">
            <template #default="{ row }">
              <span>{{ row.department?.name || '未分配' }}</span>
              <small v-if="row.jobTitle" class="cell-subtitle">{{ row.jobTitle }}</small>
            </template>
          </el-table-column>
          <el-table-column label="空间与角色" min-width="230">
            <template #default="{ row }">
              <div v-if="membershipsFor(row.id).length" class="tag-list">
                <el-tooltip
                  v-for="membership in membershipsFor(row.id).slice(0, 2)"
                  :key="`${membership.spaceId}-${membership.role.id}`"
                  :content="`${membership.spaceName} · ${membership.role.displayName}`"
                >
                  <el-tag effect="plain"
                    >{{ membership.spaceName }} · {{ membership.role.displayName }}</el-tag
                  >
                </el-tooltip>
                <el-tag v-if="membershipsFor(row.id).length > 2" type="info">
                  +{{ membershipsFor(row.id).length - 2 }}
                </el-tag>
              </div>
              <span v-else class="muted">尚未加入空间</span>
            </template>
          </el-table-column>
          <el-table-column label="平台身份" min-width="150">
            <template #default="{ row }">
              <el-tag v-if="isSuperAdmin(row)" type="danger" effect="light">超级管理员</el-tag>
              <span v-else class="muted">普通用户</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="105">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" effect="light">
                {{ row.status === 1 ? '已启用' : '已停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最后登录" min-width="165">
            <template #default="{ row }">{{ formatDate(row.lastLoginAt) }}</template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="165">
            <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="primary" @click="openPasswordReset(row)">重置密码</el-button>
              <el-button
                link
                :type="row.status === 1 ? 'danger' : 'success'"
                @click="toggleStatus(row)"
              >
                {{ row.status === 1 ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </DataState>

    <footer v-if="page.total > 0" class="pagination">
      <span>共 {{ page.total }} 条</span>
      <el-pagination
        v-model:current-page="page.pageNum"
        v-model:page-size="page.pageSize"
        background
        layout="sizes, prev, pager, next"
        :page-sizes="[10, 20, 50, 100]"
        :total="page.total"
        @current-change="loadUsers"
        @size-change="handlePageSizeChange"
      />
    </footer>

    <el-drawer v-model="drawerOpen" :title="editingUser ? '编辑用户' : '创建用户'" size="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="Boolean(editingUser)" />
        </el-form-item>
        <el-form-item v-if="!editingUser" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="姓名" prop="nickname"
          ><el-input v-model="form.nickname"
        /></el-form-item>
        <el-form-item label="邮箱" prop="email"><el-input v-model="form.email" /></el-form-item>
        <div class="form-grid">
          <el-form-item label="所属部门">
            <el-select v-model="form.departmentId" clearable placeholder="未分配部门">
              <el-option
                v-for="department in enabledDepartments"
                :key="String(department.id)"
                :label="department.name"
                :value="String(department.id)"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="职位"><el-input v-model="form.jobTitle" /></el-form-item>
        </div>
        <el-form-item label="账号状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="平台身份">
          <el-checkbox v-model="form.superAdmin">平台超级管理员</el-checkbox>
          <p class="form-help">可查看全部空间及使用平台管理能力，不自动获得空间写权限。</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveUser">保存</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="passwordDialogOpen" title="重置密码" width="440px">
      <p class="dialog-copy">重置后该用户的 Refresh Token 将立即失效。</p>
      <el-input
        v-model="newPassword"
        type="password"
        show-password
        placeholder="输入 6-64 位新密码"
      />
      <template #footer>
        <el-button @click="passwordDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="resettingPassword" @click="submitPasswordReset">
          确认重置
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  ElAlert,
  ElButton,
  ElCheckbox,
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
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTooltip,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { normalizeApiError } from '@/api/errors'
import {
  createPlatformUser,
  getPlatformUserStats,
  listDepartments,
  queryPlatformUserMemberships,
  replacePlatformUserRoles,
  resetPlatformUserPassword,
  searchPlatformUsers,
  updatePlatformUser,
  updatePlatformUserStatus,
} from '@/features/platform-management/api/platform-management-api'
import type {
  Department,
  PlatformUser,
  PlatformUserMembership,
  PlatformUserStats,
} from '@/features/platform-management/types'
import type { EntityId } from '@/features/workspace/types'
import DataState from '@/shared/components/DataState.vue'
import PageHeader from '@/shared/components/PageHeader.vue'
import { PLATFORM_ROLES } from '@/shared/constants/platform-roles'
import { useAuthStore } from '@/stores/auth'

const keyword = ref('')
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const departmentFilter = ref('ALL')
const statusFilter = ref('ALL')
const roleFilter = ref('ALL')
const users = ref<PlatformUser[]>([])
const departments = ref<Department[]>([])
const memberships = ref<PlatformUserMembership[]>([])
const stats = reactive<PlatformUserStats>({
  total: 0,
  enabled: 0,
  disabled: 0,
  unassignedDepartment: 0,
})
const page = reactive({ total: 0, pageNum: 1, pageSize: 10 })
const loading = ref(false)
const errorMessage = ref('')
const drawerOpen = ref(false)
const editingUser = ref<PlatformUser | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const passwordDialogOpen = ref(false)
const passwordUser = ref<PlatformUser | null>(null)
const newPassword = ref('')
const resettingPassword = ref(false)
let requestController: AbortController | null = null

const form = reactive({
  username: '',
  password: '',
  nickname: '',
  email: '',
  departmentId: '' as string,
  jobTitle: '',
  enabled: true,
  superAdmin: false,
})
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_]{3,32}$/,
      message: '用户名为 3-32 位字母、数字或下划线',
      trigger: 'blur',
    },
  ],
  password: [{ min: 6, max: 64, message: '密码长度为 6-64 位', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}

const enabledDepartments = computed(() =>
  departments.value.filter((department) => department.status === 1),
)
const hasFilters = computed(
  () =>
    keyword.value.trim() ||
    departmentFilter.value !== 'ALL' ||
    statusFilter.value !== 'ALL' ||
    roleFilter.value !== 'ALL',
)

onMounted(() => {
  if (typeof route.query.departmentId === 'string' && route.query.departmentId) {
    departmentFilter.value = route.query.departmentId
  }
  void refreshAll()
})
onBeforeUnmount(() => requestController?.abort())

async function refreshAll(): Promise<void> {
  try {
    const [departmentList, userStats] = await Promise.all([
      listDepartments(),
      getPlatformUserStats(),
    ])
    departments.value = departmentList
    Object.assign(stats, userStats)
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  }
  await loadUsers()
}

async function loadUsers(): Promise<void> {
  requestController?.abort()
  const controller = new AbortController()
  requestController = controller
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await searchPlatformUsers({
      keyword: keyword.value.trim(),
      departmentId:
        departmentFilter.value === 'UNASSIGNED'
          ? 0
          : departmentFilter.value === 'ALL'
            ? undefined
            : departmentFilter.value,
      status: statusFilter.value === 'ALL' ? undefined : (Number(statusFilter.value) as 0 | 1),
      platformRoleKey: roleFilter.value === 'ALL' ? undefined : roleFilter.value,
      pageNum: page.pageNum,
      pageSize: page.pageSize,
      signal: controller.signal,
    })
    users.value = result.records
    Object.assign(page, result)
    memberships.value = await queryPlatformUserMemberships(
      result.records.map((user) => user.id),
      controller.signal,
    )
  } catch (error) {
    if (!controller.signal.aborted) errorMessage.value = normalizeApiError(error).message
  } finally {
    if (requestController === controller) loading.value = false
  }
}

function applyFilters(): void {
  page.pageNum = 1
  void loadUsers()
}
function handlePageSizeChange(): void {
  page.pageNum = 1
  void loadUsers()
}
function membershipsFor(userId: EntityId): PlatformUserMembership[] {
  return memberships.value.filter((membership) => String(membership.userId) === String(userId))
}
function toPlatformUser(value: unknown): PlatformUser {
  return value as PlatformUser
}
function isSuperAdmin(value: unknown): boolean {
  const user = toPlatformUser(value)
  return user.platformRoles.includes(PLATFORM_ROLES.SUPER_ADMIN)
}
function initials(value: unknown): string {
  const user = toPlatformUser(value)
  return (user.nickname || user.username).slice(0, 1)
}

function resetForm(): void {
  Object.assign(form, {
    username: '',
    password: '',
    nickname: '',
    email: '',
    departmentId: '',
    jobTitle: '',
    enabled: true,
    superAdmin: false,
  })
  formRef.value?.clearValidate()
}
function openCreate(): void {
  editingUser.value = null
  resetForm()
  drawerOpen.value = true
}
function openEdit(value: unknown): void {
  const user = toPlatformUser(value)
  editingUser.value = user
  Object.assign(form, {
    username: user.username,
    password: '',
    nickname: user.nickname || user.username,
    email: user.email || '',
    departmentId: user.department ? String(user.department.id) : '',
    jobTitle: user.jobTitle || '',
    enabled: user.status === 1,
    superAdmin: isSuperAdmin(user),
  })
  drawerOpen.value = true
}

async function saveUser(): Promise<void> {
  if (!formRef.value || !(await formRef.value.validate().catch(() => false))) return
  if (!editingUser.value && form.password.length < 6) {
    ElMessage.warning('请输入 6-64 位初始密码')
    return
  }
  if (form.superAdmin && !form.enabled) {
    ElMessage.warning('禁用用户不能设为平台超级管理员')
    return
  }
  saving.value = true
  try {
    const currentUserLosesAccess = Boolean(
      editingUser.value &&
      String(editingUser.value.id) === String(authStore.user?.id) &&
      (!form.enabled || !form.superAdmin),
    )
    const profilePayload = {
      nickname: form.nickname.trim(),
      email: form.email.trim() || undefined,
      departmentId: form.departmentId || undefined,
      jobTitle: form.jobTitle.trim() || undefined,
    }
    const status = (form.enabled ? 1 : 0) as 0 | 1
    const user = editingUser.value
      ? await updatePlatformUser(editingUser.value.id, profilePayload)
      : await createPlatformUser({
          ...profilePayload,
          username: form.username.trim(),
          password: form.password,
          status,
          superAdmin: form.superAdmin,
        })
    if (user.status !== status) await updatePlatformUserStatus(user.id, status)
    const roles = form.superAdmin ? [PLATFORM_ROLES.SUPER_ADMIN] : []
    if (isSuperAdmin(user) !== form.superAdmin) await replacePlatformUserRoles(user.id, roles)
    if (currentUserLosesAccess) {
      await authStore.logout()
      await router.replace('/login')
      return
    }
    ElMessage.success(editingUser.value ? '用户已更新' : '用户已创建')
    drawerOpen.value = false
    await refreshAll()
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(value: unknown): Promise<void> {
  const user = toPlatformUser(value)
  const status = user.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      status === 0 ? `确定停用“${user.nickname}”吗？` : `确定启用“${user.nickname}”吗？`,
      status === 0 ? '停用用户' : '启用用户',
      { type: status === 0 ? 'warning' : 'info' },
    )
    await updatePlatformUserStatus(user.id, status)
    if (status === 0 && String(user.id) === String(authStore.user?.id)) {
      await authStore.logout()
      await router.replace('/login')
      return
    }
    ElMessage.success(status === 1 ? '用户已启用' : '用户已停用')
    await refreshAll()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(normalizeApiError(error).message)
  }
}

function openPasswordReset(value: unknown): void {
  const user = toPlatformUser(value)
  passwordUser.value = user
  newPassword.value = ''
  passwordDialogOpen.value = true
}
async function submitPasswordReset(): Promise<void> {
  if (!passwordUser.value || newPassword.value.length < 6 || newPassword.value.length > 64) {
    ElMessage.warning('请输入 6-64 位新密码')
    return
  }
  resettingPassword.value = true
  try {
    await resetPlatformUserPassword(passwordUser.value.id, newPassword.value)
    if (String(passwordUser.value.id) === String(authStore.user?.id)) {
      await authStore.logout()
      await router.replace('/login')
      return
    }
    ElMessage.success('密码已重置')
    passwordDialogOpen.value = false
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    resettingPassword.value = false
  }
}

function formatDate(value: string | null): string {
  if (!value) return '从未登录'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}
</script>

<style scoped>
.platform-user-page {
  display: grid;
  gap: var(--adw-space-5);
}
.breadcrumb {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--adw-space-4);
}
.stat-card {
  display: grid;
  gap: 8px;
  padding: 20px 22px;
  border-left: 4px solid var(--adw-color-primary);
}
.stat-card span {
  color: var(--adw-text-secondary);
  font-size: 13px;
}
.stat-card strong {
  font-size: 28px;
}
.stat-card--success {
  border-left-color: var(--adw-color-success);
}
.stat-card--danger {
  border-left-color: var(--adw-color-danger);
}
.stat-card--warning {
  border-left-color: var(--adw-color-warning);
}
.toolbar {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) 180px 150px 180px auto;
  gap: 12px;
  padding: 16px;
}
.user-table {
  overflow: hidden;
}
.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.user-cell div {
  display: grid;
  gap: 2px;
}
.user-cell small,
.cell-subtitle {
  display: block;
  color: var(--adw-text-tertiary);
}
.avatar {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 50%;
  color: white;
  background: var(--adw-color-primary);
}
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.muted {
  color: var(--adw-text-tertiary);
}
.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--adw-text-secondary);
  font-size: 13px;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.form-grid :deep(.el-select) {
  width: 100%;
}
.form-help {
  margin: 4px 0 0;
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.dialog-copy {
  color: var(--adw-text-secondary);
}
@media (max-width: 1100px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .toolbar {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 720px) {
  .stat-grid,
  .toolbar,
  .form-grid {
    grid-template-columns: 1fr;
  }
  .pagination {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }
}
</style>
