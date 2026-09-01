<template>
  <section class="access-page">
    <PageHeader title="角色与权限" description="配置空间角色与细粒度权限">
      <template #breadcrumb>
        <span class="access-page__breadcrumb">工作台 / 组织与权限 / 角色与权限</span>
      </template>
      <template #actions>
        <el-button
          v-if="activePage === 'roles' && canManageRoles"
          type="primary"
          :icon="Plus"
          @click="openCreateRole"
        >
          新建角色
        </el-button>
        <el-button
          v-if="activePage === 'members' && canManageMembers"
          type="primary"
          :icon="Plus"
          @click="openAddMember"
        >
          添加成员
        </el-button>
      </template>
    </PageHeader>

    <DataState :loading="loading" :error="errorMessage" loading-text="正在加载角色与成员">
      <div class="access-layout">
        <aside v-if="canReadRoles" class="role-list surface-card">
          <header class="role-list__header">
            <div>
              <h2>空间角色</h2>
              <span>{{ roles.length }} 个角色</span>
            </div>
            <el-icon><Lock /></el-icon>
          </header>

          <div v-if="roles.length" class="role-list__items">
            <button
              v-for="role in roles"
              :key="role.id"
              class="role-card"
              :class="{ 'role-card--active': selectedRole?.id === role.id }"
              type="button"
              @click="selectRole(role)"
            >
              <span class="role-card__icon" :class="`role-card__icon--${roleTone(role)}`">
                <el-icon><component :is="roleIcon(role)" /></el-icon>
              </span>
              <span class="role-card__content">
                <span class="role-card__title">
                  <strong>{{ role.displayName }}</strong>
                  <code>{{ role.roleKey }}</code>
                  <el-tag v-if="role.systemRole" size="small" effect="light">系统默认</el-tag>
                  <el-tag v-else size="small" type="success" effect="light">自定义</el-tag>
                </span>
                <span class="role-card__meta">
                  <el-icon><User /></el-icon>
                  {{ role.memberCount }} 位成员
                </span>
                <span class="role-card__description">{{ role.description || '暂无角色说明' }}</span>
              </span>
              <el-icon class="role-card__arrow"><ArrowRight /></el-icon>
            </button>
          </div>
          <div v-else class="role-list__empty">当前空间暂无角色</div>
        </aside>

        <main class="access-detail surface-card" :class="{ 'access-detail--full': !canReadRoles }">
          <template v-if="activePage === 'roles' && selectedRole">
            <header class="access-detail__header">
              <div class="access-detail__role-heading">
                <span
                  class="access-detail__role-icon"
                  :class="`role-card__icon--${roleTone(selectedRole)}`"
                >
                  <el-icon><component :is="roleIcon(selectedRole)" /></el-icon>
                </span>
                <div>
                  <h2>
                    {{ selectedRole.displayName }} <code>{{ selectedRole.roleKey }}</code>
                  </h2>
                  <p>{{ selectedRole.description || '暂无角色说明' }}</p>
                </div>
              </div>
              <div class="access-detail__actions">
                <el-button
                  v-if="detailTab === 'permissions' && canManageRoles"
                  type="primary"
                  :loading="savingPermissions"
                  :disabled="selectedRole.protectedRole"
                  @click="savePermissions"
                >
                  保存权限
                </el-button>
                <el-button
                  v-if="canManageRoles && !selectedRole.protectedRole"
                  :icon="EditPen"
                  @click="openEditRole(selectedRole)"
                >
                  编辑角色
                </el-button>
                <el-button
                  v-if="canManageRoles && !selectedRole.protectedRole"
                  type="primary"
                  :icon="Delete"
                  plain
                  @click="removeSelectedRole"
                >
                  删除角色
                </el-button>
              </div>
            </header>

            <nav class="access-tabs" aria-label="角色详情导航">
              <button
                v-if="canReadRoles"
                type="button"
                :class="{ 'access-tabs__item--active': detailTab === 'permissions' }"
                @click="detailTab = 'permissions'"
              >
                权限配置
              </button>
              <button
                v-if="canReadMembers"
                type="button"
                :class="{ 'access-tabs__item--active': detailTab === 'members' }"
                @click="detailTab = 'members'"
              >
                成员绑定
              </button>
              <button
                type="button"
                :class="{ 'access-tabs__item--active': detailTab === 'changes' }"
                @click="showChangeLogPlaceholder"
              >
                变更记录
              </button>
            </nav>

            <div v-if="detailTab === 'permissions'" class="permission-workspace">
              <el-alert
                v-if="selectedRole.protectedRole"
                title="默认角色保护中，权限不可修改；权限调整仅影响后续请求，不改变历史审计。"
                type="info"
                :closable="false"
                show-icon
              />
              <el-alert
                v-else
                title="角色权限调整后立即对后续请求生效，不改变历史审计记录。"
                type="info"
                :closable="false"
                show-icon
              />

              <div class="permission-content">
                <div class="permission-groups">
                  <section
                    v-for="group in permissionGroups"
                    :key="group.category"
                    class="permission-group"
                  >
                    <header class="permission-group__header">
                      <span
                        class="permission-group__title"
                        role="button"
                        tabindex="0"
                        :aria-expanded="!isPermissionGroupCollapsed(group.category)"
                        @click="togglePermissionGroupCollapse(group.category)"
                        @keydown.enter.prevent="togglePermissionGroupCollapse(group.category)"
                        @keydown.space.prevent="togglePermissionGroupCollapse(group.category)"
                      >
                        <el-icon>
                          <component
                            :is="
                              isPermissionGroupCollapsed(group.category) ? ArrowRight : ArrowDown
                            "
                          />
                        </el-icon>
                        {{ group.label }}
                        <small>({{ group.items.length }} 项)</small>
                      </span>
                      <el-checkbox
                        :model-value="
                          group.items.every((item) => selectedPermissionCodes.includes(item.code))
                        "
                        :indeterminate="
                          group.items.some((item) => selectedPermissionCodes.includes(item.code)) &&
                          !group.items.every((item) => selectedPermissionCodes.includes(item.code))
                        "
                        :disabled="selectedRole.protectedRole || !canManageRoles"
                        @click.stop
                        @change="togglePermissionGroup(group.items, Boolean($event))"
                      >
                        全选
                      </el-checkbox>
                    </header>
                    <div
                      v-show="!isPermissionGroupCollapsed(group.category)"
                      class="permission-group__items"
                    >
                      <el-checkbox
                        v-for="permission in group.items"
                        :key="permission.code"
                        v-model="selectedPermissionCodes"
                        :label="permission.code"
                        :disabled="selectedRole.protectedRole || !canManageRoles"
                      >
                        <span>{{ permission.code }}</span>
                        <el-tag
                          v-if="isHighPermission(permission.code)"
                          size="small"
                          type="warning"
                          effect="plain"
                        >
                          高权限
                        </el-tag>
                      </el-checkbox>
                    </div>
                  </section>
                </div>

                <aside class="permission-summary">
                  <el-icon class="permission-summary__icon"><CircleCheck /></el-icon>
                  <strong>已选择</strong>
                  <b>{{ selectedPermissionCodes.length }} / {{ permissions.length }}</b>
                  <span>项权限</span>
                  <el-divider />
                  <h3>说明</h3>
                  <p>
                    权限标识符（如：space:manage）是系统稳定标识，建议直接使用，避免因名称变更影响集成。
                  </p>
                  <h3>成员数量</h3>
                  <p>当前角色绑定 {{ selectedRole.memberCount }} 位成员。</p>
                </aside>
              </div>

              <footer class="permission-footer">
                <span>Agent 与 MCP 执行仍使用任务 Capability，不直接继承用户角色</span>
              </footer>
            </div>

            <div v-else-if="detailTab === 'members'" class="member-workspace">
              <MemberTable
                :members="displayedMembers"
                :member-users="memberUsersById"
                :roles="roles"
                :can-manage="canManageMembers"
                @change-role="changeMemberRoleForMember"
                @remove="removeMemberByUserId"
              />
            </div>

            <div v-else class="placeholder-panel">
              <el-icon><InfoFilled /></el-icon>
              <strong>变更记录待开发，敬请期待</strong>
              <span>当前版本先提供角色和成员的实时配置能力。</span>
            </div>
          </template>

          <template v-else-if="activePage === 'members'">
            <header class="access-detail__header">
              <div>
                <h2>成员管理</h2>
                <p>查看空间成员并调整成员角色</p>
              </div>
            </header>
            <MemberTable
              :members="displayedMembers"
              :member-users="memberUsersById"
              :roles="roles"
              :can-manage="canManageMembers"
              @change-role="changeMemberRoleForMember"
              @remove="removeMemberByUserId"
            />
          </template>

          <div v-else class="access-detail__empty">当前用户没有可查看的组织权限内容</div>
        </main>
      </div>
    </DataState>

    <el-dialog
      v-model="roleDialogVisible"
      :title="roleDialogMode === 'create' ? '新建角色' : '编辑角色'"
      width="560px"
    >
      <el-form label-position="top">
        <el-form-item v-if="roleDialogMode === 'create'" label="角色标识" required>
          <el-input v-model="roleForm.roleKey" placeholder="例如 content-reviewer" />
        </el-form-item>
        <el-form-item label="角色名称" required>
          <el-input v-model="roleForm.displayName" placeholder="例如 内容审核员" />
        </el-form-item>
        <el-form-item label="角色说明">
          <el-input
            v-model="roleForm.description"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
          />
        </el-form-item>
        <el-form-item v-if="roleDialogMode === 'create'" label="初始权限" required>
          <div class="role-dialog__permission-picker">
            <nav class="role-dialog__module-list" aria-label="权限模块">
              <button
                v-for="group in permissionGroups"
                :key="group.category"
                class="role-dialog__module-item"
                :class="{
                  'role-dialog__module-item--active':
                    selectedCreatePermissionGroup?.category === group.category,
                }"
                type="button"
                @click="selectedCreatePermissionCategory = group.category"
              >
                <span>{{ group.label }}</span>
                <small
                  >{{ selectedCreatePermissionCount(group.items) }}/{{ group.items.length }}</small
                >
              </button>
            </nav>
            <section v-if="selectedCreatePermissionGroup" class="role-dialog__permission-panel">
              <header class="role-dialog__permission-panel-header">
                <div>
                  <strong>{{ selectedCreatePermissionGroup.label }}</strong>
                  <span>{{ selectedCreatePermissionGroup.items.length }} 项权限</span>
                </div>
                <span class="role-dialog__permission-panel-count">
                  已选 {{ selectedCreatePermissionCount(selectedCreatePermissionGroup.items) }} 项
                </span>
              </header>
              <div class="role-dialog__permission-items">
                <el-checkbox
                  v-for="permission in selectedCreatePermissionGroup.items"
                  :key="permission.code"
                  v-model="createPermissionCodes"
                  :label="permission.code"
                >
                  {{ permission.name }}（{{ permission.code }}）
                </el-checkbox>
              </div>
            </section>
            <div v-else class="role-dialog__permission-empty">暂无可用权限</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRole" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="memberDialogVisible" title="添加成员" width="460px">
      <el-form label-position="top">
        <el-form-item label="用户 ID" required>
          <el-input v-model="memberForm.userId" placeholder="请输入用户 ID" />
          <span class="form-help">当前版本通过用户 ID 添加成员，用户搜索能力待后续开放。</span>
        </el-form-item>
        <el-form-item label="空间角色" required>
          <el-select v-model="memberForm.roleId" placeholder="请选择角色" class="form-control">
            <el-option
              v-for="role in roles"
              :key="role.id"
              :label="role.displayName"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="memberDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingMember" @click="saveMember">添加</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import {
  ArrowDown,
  ArrowRight,
  CircleCheck,
  Delete,
  EditPen,
  InfoFilled,
  Lock,
  Plus,
  User,
  UserFilled,
} from '@element-plus/icons-vue'
import {
  ElAlert,
  ElButton,
  ElCheckbox,
  ElDialog,
  ElDivider,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus'
import { computed, defineComponent, h, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { normalizeApiError } from '@/api/errors'
import {
  addMember,
  changeMemberRole,
  createRole,
  deleteRole,
  listMemberUsers,
  listMembers,
  listPermissions,
  listRoles,
  removeMember,
  replaceRolePermissions,
  updateRole,
  type Member,
  type MemberUser,
  type PermissionItem,
  type SpaceRole,
} from '@/features/access-control/api/access-control-api'
import PageHeader from '@/shared/components/PageHeader.vue'
import DataState from '@/shared/components/DataState.vue'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import type { EntityId } from '@/features/workspace/types'
import { useWorkspaceStore } from '@/stores/workspace'

const route = useRoute()
const workspaceStore = useWorkspaceStore()

const loading = ref(true)
const errorMessage = ref('')
const savingPermissions = ref(false)
const savingRole = ref(false)
const savingMember = ref(false)
const roles = ref<SpaceRole[]>([])
const permissions = ref<PermissionItem[]>([])
const members = ref<Member[]>([])
const memberUsersById = ref<Record<string, MemberUser>>({})
const selectedRoleId = ref<EntityId | null>(null)
const selectedPermissionCodes = ref<string[]>([])
const collapsedPermissionGroups = ref<Record<string, boolean>>({})
const selectedCreatePermissionCategory = ref<string | null>(null)
const detailTab = ref<'permissions' | 'members' | 'changes'>('permissions')
const roleDialogVisible = ref(false)
const memberDialogVisible = ref(false)
const roleDialogMode = ref<'create' | 'edit'>('create')
const roleForm = reactive({ roleKey: '', displayName: '', description: '' })
const createPermissionCodes = ref<string[]>([])
const memberForm = reactive<{ userId: string; roleId: EntityId | null }>({
  userId: '',
  roleId: null,
})

const activePage = computed(() => (route.name === 'space-access-members' ? 'members' : 'roles'))
const canReadRoles = computed(() => workspaceStore.hasPermission(SPACE_PERMISSIONS.ROLE_READ))
const canManageRoles = computed(() => workspaceStore.hasPermission(SPACE_PERMISSIONS.ROLE_MANAGE))
const canReadMembers = computed(() => workspaceStore.hasPermission(SPACE_PERMISSIONS.MEMBER_READ))
const canManageMembers = computed(() =>
  workspaceStore.hasPermission(SPACE_PERMISSIONS.MEMBER_MANAGE),
)
const selectedRole = computed(
  () => roles.value.find((role) => String(role.id) === String(selectedRoleId.value)) ?? null,
)
const displayedMembers = computed(() => {
  if (activePage.value !== 'roles' || detailTab.value !== 'members' || !selectedRole.value) {
    return members.value
  }
  return members.value.filter(
    (member) => String(member.role.roleId) === String(selectedRole.value?.id),
  )
})

const categoryLabels: Record<string, string> = {
  SPACE: '空间',
  MEMBER: '成员',
  ROLE: '角色',
  DOCUMENT: '文档',
  TASK: '任务与审批',
  CHANGE_REQUEST: '变更审批',
  USAGE: '用量',
  AGENT: 'Agent 与模型',
  SKILL: 'Skill',
  MCP: 'MCP 服务',
  AUDIT: '审计',
}

const permissionGroups = computed(() => {
  const groups = new Map<string, PermissionItem[]>()
  for (const permission of permissions.value) {
    const items = groups.get(permission.category) ?? []
    items.push(permission)
    groups.set(permission.category, items)
  }
  return [...groups.entries()].map(([category, items]) => ({
    category,
    label: categoryLabels[category] || category,
    items,
  }))
})
const selectedCreatePermissionGroup = computed(
  () =>
    permissionGroups.value.find(
      (group) => group.category === selectedCreatePermissionCategory.value,
    ) ??
    permissionGroups.value[0] ??
    null,
)

const highPermissionCodes = new Set([
  SPACE_PERMISSIONS.SPACE_MANAGE,
  SPACE_PERMISSIONS.MEMBER_MANAGE,
  SPACE_PERMISSIONS.ROLE_MANAGE,
  SPACE_PERMISSIONS.DOCUMENT_EDIT,
  SPACE_PERMISSIONS.TASK_CREATE,
  SPACE_PERMISSIONS.TASK_TERMINATE,
  SPACE_PERMISSIONS.AGENT_MANAGE,
  SPACE_PERMISSIONS.AGENT_BIND_SKILL,
  SPACE_PERMISSIONS.AGENT_BIND_MCP,
  SPACE_PERMISSIONS.SKILL_MANAGE,
  SPACE_PERMISSIONS.MCP_MANAGE,
  SPACE_PERMISSIONS.CHANGE_REQUEST_APPROVE,
  SPACE_PERMISSIONS.CHANGE_REQUEST_MERGE,
])

async function loadData(): Promise<void> {
  const spaceId = workspaceStore.currentSpaceId
  if (!spaceId) return
  loading.value = true
  errorMessage.value = ''
  try {
    const [roleItems, permissionItems, memberItems] = await Promise.all([
      canReadRoles.value ? listRoles(spaceId) : Promise.resolve([] as SpaceRole[]),
      canReadRoles.value ? listPermissions(spaceId) : Promise.resolve([] as PermissionItem[]),
      canReadMembers.value ? listMembers(spaceId) : Promise.resolve([] as Member[]),
    ])
    roles.value = roleItems
    permissions.value = permissionItems
    members.value = memberItems
    if (memberItems.length) {
      const users = await listMemberUsers(
        spaceId,
        memberItems.map((member) => member.userId),
      )
      memberUsersById.value = Object.fromEntries(users.map((user) => [String(user.userId), user]))
    } else {
      memberUsersById.value = {}
    }

    const nextRole =
      roleItems.find((role) => String(role.id) === String(selectedRoleId.value)) ?? roleItems[0]
    if (nextRole) {
      selectRole(nextRole)
    } else {
      selectedRoleId.value = null
      selectedPermissionCodes.value = []
    }
  } catch (error) {
    errorMessage.value = normalizeApiError(error).message
  } finally {
    loading.value = false
  }
}

function selectRole(role: SpaceRole): void {
  selectedRoleId.value = role.id
  selectedPermissionCodes.value = [...role.permissionCodes]
}

function togglePermissionGroup(items: PermissionItem[], checked: boolean): void {
  const codes = new Set(selectedPermissionCodes.value)
  for (const item of items) {
    if (checked) codes.add(item.code)
    else codes.delete(item.code)
  }
  selectedPermissionCodes.value = [...codes]
}

function isPermissionGroupCollapsed(category: string): boolean {
  return collapsedPermissionGroups.value[category] ?? false
}

function togglePermissionGroupCollapse(category: string): void {
  collapsedPermissionGroups.value[category] = !isPermissionGroupCollapsed(category)
}

function selectedCreatePermissionCount(items: PermissionItem[]): number {
  return items.filter((item) => createPermissionCodes.value.includes(item.code)).length
}

function isHighPermission(code: string): boolean {
  return highPermissionCodes.has(code)
}

function roleTone(role: SpaceRole): string {
  if (role.roleKey === 'OWNER') return 'owner'
  if (role.roleKey === 'EDITOR') return 'editor'
  if (role.roleKey === 'VIEWER') return 'viewer'
  return 'custom'
}

function roleIcon(role: SpaceRole) {
  return role.roleKey === 'OWNER' ? Lock : role.roleKey === 'VIEWER' ? User : UserFilled
}

async function savePermissions(): Promise<void> {
  const role = selectedRole.value
  const spaceId = workspaceStore.currentSpaceId
  if (!role || !spaceId || role.protectedRole || !canManageRoles.value) return
  savingPermissions.value = true
  try {
    const updated = await replaceRolePermissions(spaceId, role.id, selectedPermissionCodes.value)
    roles.value = roles.value.map((item) => (item.id === updated.id ? updated : item))
    ElMessage.success('角色权限已保存')
    await workspaceStore.ensurePermissions(spaceId, true)
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
    await loadData()
  } finally {
    savingPermissions.value = false
  }
}

function openCreateRole(): void {
  roleDialogMode.value = 'create'
  roleForm.roleKey = ''
  roleForm.displayName = ''
  roleForm.description = ''
  createPermissionCodes.value = []
  selectedCreatePermissionCategory.value = permissionGroups.value[0]?.category ?? null
  roleDialogVisible.value = true
}

function openEditRole(role: SpaceRole): void {
  roleDialogMode.value = 'edit'
  selectedRoleId.value = role.id
  roleForm.roleKey = role.roleKey
  roleForm.displayName = role.displayName
  roleForm.description = role.description || ''
  roleDialogVisible.value = true
}

async function saveRole(): Promise<void> {
  const spaceId = workspaceStore.currentSpaceId
  if (!spaceId || !roleForm.displayName.trim()) {
    ElMessage.warning('请输入角色名称')
    return
  }
  if (
    roleDialogMode.value === 'create' &&
    (!/^[a-z][a-z0-9-]{1,63}$/.test(roleForm.roleKey) || !createPermissionCodes.value.length)
  ) {
    ElMessage.warning('请输入合法的角色标识，并至少选择一项权限')
    return
  }
  savingRole.value = true
  try {
    if (roleDialogMode.value === 'create') {
      const created = await createRole(spaceId, {
        roleKey: roleForm.roleKey.trim(),
        displayName: roleForm.displayName.trim(),
        description: roleForm.description.trim(),
        permissionCodes: createPermissionCodes.value,
      })
      selectedRoleId.value = created.id
      ElMessage.success('角色已创建')
    } else if (selectedRole.value) {
      await updateRole(spaceId, selectedRole.value.id, {
        displayName: roleForm.displayName.trim(),
        description: roleForm.description.trim(),
      })
      ElMessage.success('角色信息已保存')
    }
    roleDialogVisible.value = false
    await loadData()
    await workspaceStore.ensurePermissions(spaceId, true)
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    savingRole.value = false
  }
}

async function removeSelectedRole(): Promise<void> {
  const role = selectedRole.value
  const spaceId = workspaceStore.currentSpaceId
  if (!role || !spaceId || role.protectedRole) return
  try {
    await ElMessageBox.confirm(`确定删除角色“${role.displayName}”吗？`, '删除角色', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteRole(spaceId, role.id)
    ElMessage.success('角色已删除')
    await loadData()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(normalizeApiError(error).message)
  }
}

function openAddMember(): void {
  memberForm.userId = ''
  memberForm.roleId = roles.value[0]?.id ?? null
  memberDialogVisible.value = true
}

async function saveMember(): Promise<void> {
  const spaceId = workspaceStore.currentSpaceId
  if (!spaceId || !memberForm.userId.trim() || memberForm.roleId === null) {
    ElMessage.warning('请输入用户 ID 并选择角色')
    return
  }
  savingMember.value = true
  try {
    await addMember(spaceId, { userId: memberForm.userId.trim(), roleId: memberForm.roleId })
    memberDialogVisible.value = false
    ElMessage.success('成员已添加')
    await loadData()
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    savingMember.value = false
  }
}

async function changeMemberRoleForMember(member: Member, roleId: EntityId): Promise<void> {
  const spaceId = workspaceStore.currentSpaceId
  if (!spaceId) return
  try {
    await changeMemberRole(spaceId, member.userId, roleId)
    ElMessage.success('成员角色已更新')
    await loadData()
    await workspaceStore.ensurePermissions(spaceId, true)
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
    await loadData()
  }
}

async function removeMemberByUserId(userId: EntityId): Promise<void> {
  const spaceId = workspaceStore.currentSpaceId
  if (!spaceId) return
  const name =
    memberUsersById.value[String(userId)]?.nickname ||
    memberUsersById.value[String(userId)]?.username ||
    `用户 ${userId}`
  try {
    await ElMessageBox.confirm(`确定移除成员“${name}”吗？`, '移除成员', {
      type: 'warning',
      confirmButtonText: '移除',
      cancelButtonText: '取消',
    })
    await removeMember(spaceId, userId)
    ElMessage.success('成员已移除')
    await loadData()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(normalizeApiError(error).message)
  }
}

function showChangeLogPlaceholder(): void {
  detailTab.value = 'changes'
  ElMessage.info('变更记录待开发，敬请期待')
}

watch(
  () => [route.name, workspaceStore.currentSpaceId] as const,
  ([routeName], previous) => {
    detailTab.value = routeName === 'space-access-members' ? 'members' : 'permissions'
    if (previous?.[1] !== workspaceStore.currentSpaceId || loading.value) void loadData()
  },
  { immediate: true },
)

const MemberTable = defineComponent({
  name: 'MemberTable',
  props: {
    members: { type: Array as () => Member[], required: true },
    memberUsers: { type: Object as () => Record<string, MemberUser>, required: true },
    roles: { type: Array as () => SpaceRole[], required: true },
    canManage: { type: Boolean, required: true },
  },
  emits: ['change-role', 'remove'],
  setup(props, { emit }) {
    const displayName = (userId: EntityId) => {
      const user = props.memberUsers[String(userId)]
      return user?.nickname || user?.username || `用户 ${userId}`
    }
    return () =>
      props.members.length
        ? h('div', { class: 'member-table' }, [
            h('div', { class: 'member-table__header' }, [
              h('span', '成员'),
              h('span', '空间角色'),
              h('span', '加入时间'),
              h('span', '操作'),
            ]),
            ...props.members.map((member) =>
              h('div', { class: 'member-table__row', key: String(member.id) }, [
                h('div', { class: 'member-table__identity' }, [
                  h(
                    'span',
                    { class: 'member-table__avatar' },
                    displayName(member.userId).slice(0, 1).toUpperCase(),
                  ),
                  h('div', [
                    h('strong', displayName(member.userId)),
                    h('small', `用户 ID：${member.userId}`),
                  ]),
                ]),
                props.canManage
                  ? h(
                      'select',
                      {
                        class: 'member-table__select',
                        value: String(member.role.roleId),
                        onChange: (event: Event) =>
                          emit('change-role', member, (event.target as HTMLSelectElement).value),
                      },
                      props.roles.map((role) =>
                        h('option', { value: String(role.id) }, role.displayName),
                      ),
                    )
                  : h('span', { class: 'member-table__role' }, member.role.displayName),
                h('time', new Date(member.createdAt).toLocaleDateString('zh-CN')),
                props.canManage
                  ? h(
                      ElButton,
                      { type: 'danger', link: true, onClick: () => emit('remove', member.userId) },
                      { default: () => '移除' },
                    )
                  : h('span', { class: 'member-table__muted' }, '—'),
              ]),
            ),
          ])
        : h('div', { class: 'member-table__empty' }, '当前空间暂无成员')
  },
})
</script>

<style scoped>
.access-page {
  display: grid;
  gap: var(--adw-space-6);
}

.access-page__breadcrumb {
  color: var(--adw-text-secondary);
  font-size: 13px;
}

.access-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: var(--adw-space-5);
  min-height: 680px;
}

.surface-card {
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-lg);
  background: var(--adw-surface);
  box-shadow: var(--adw-shadow-card);
}

.role-list {
  align-self: start;
  overflow: hidden;
}

.role-list__header,
.access-detail__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--adw-space-4);
  padding: var(--adw-space-5);
  border-bottom: 1px solid var(--adw-border-color-light);
}

.role-list__header h2,
.access-detail__header h2 {
  margin: 0;
  color: var(--adw-text-primary);
  font-size: 18px;
}

.role-list__header span,
.access-detail__header p {
  display: block;
  margin: 6px 0 0;
  color: var(--adw-text-secondary);
  font-size: 13px;
}

.role-list__header > .el-icon {
  color: var(--adw-color-primary);
}

.role-list__items {
  display: grid;
  gap: var(--adw-space-3);
  padding: var(--adw-space-4);
}

.role-card {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) 18px;
  align-items: start;
  gap: var(--adw-space-3);
  width: 100%;
  padding: var(--adw-space-4);
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-md);
  color: inherit;
  background: var(--adw-surface);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease,
    background 160ms ease;
}

.role-card:hover,
.role-card--active {
  border-color: var(--adw-color-primary);
  background: #f7f9ff;
  box-shadow: 0 4px 14px rgb(44 91 220 / 10%);
}

.role-card__icon,
.access-detail__role-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
}

.role-card__icon {
  width: 38px;
  height: 38px;
}

.access-detail__role-icon {
  width: 48px;
  height: 48px;
  flex: 0 0 auto;
}

.role-card__icon--owner {
  background: #2563eb;
}
.role-card__icon--editor {
  background: #10b981;
}
.role-card__icon--viewer {
  background: #f59e0b;
}
.role-card__icon--custom {
  background: #7c3aed;
}

.role-card__content {
  display: grid;
  min-width: 0;
  gap: 7px;
}

.role-card__title {
  display: flex;
  min-width: 0;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 7px;
}

.role-card__title strong,
.role-card__description,
.role-card__meta {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-card__title strong {
  color: var(--adw-text-primary);
  font-size: 14px;
}

.role-card code,
.access-detail code {
  color: var(--adw-text-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

.role-card__meta,
.role-card__description {
  color: var(--adw-text-secondary);
  font-size: 12px;
}

.role-card__meta {
  display: flex;
  align-items: center;
  gap: 4px;
}

.role-card__arrow {
  margin-top: 10px;
  color: var(--adw-text-tertiary);
}

.role-list__empty,
.access-detail__empty {
  padding: 64px 24px;
  color: var(--adw-text-secondary);
  text-align: center;
}

.access-detail {
  min-width: 0;
  overflow: hidden;
}

.access-detail--full {
  grid-column: 1 / -1;
}

.access-detail__role-heading {
  display: flex;
  align-items: center;
  gap: var(--adw-space-4);
  min-width: 0;
}

.access-detail__role-heading h2 {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.access-detail__role-heading p {
  max-width: 500px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.access-detail__actions {
  display: flex;
  flex: 0 0 auto;
  gap: var(--adw-space-2);
}

.access-tabs {
  display: flex;
  gap: var(--adw-space-6);
  padding: 0 var(--adw-space-5);
  border-bottom: 1px solid var(--adw-border-color-light);
}

.access-tabs button {
  position: relative;
  padding: 15px 2px 13px;
  border: 0;
  color: var(--adw-text-secondary);
  background: transparent;
  font: inherit;
  cursor: pointer;
}

.access-tabs button::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  background: transparent;
  content: '';
}

.access-tabs button:hover,
.access-tabs__item--active {
  color: var(--adw-color-primary) !important;
}

.access-tabs__item--active::after {
  background: var(--adw-color-primary) !important;
}

.permission-workspace,
.member-workspace {
  padding: var(--adw-space-5);
}

.permission-content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: var(--adw-space-4);
  margin-top: var(--adw-space-4);
}

.permission-groups {
  display: grid;
  gap: var(--adw-space-3);
}

.permission-group {
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-md);
  overflow: hidden;
}

.permission-group__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: #fafbfe;
  cursor: pointer;
  transition: background 160ms ease;
}

.permission-group__header:hover {
  background: #f3f6fc;
}

.permission-group__title {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--adw-text-primary);
  cursor: pointer;
  font-weight: 600;
}

.permission-group__title small {
  color: var(--adw-text-secondary);
  font-weight: 400;
}

.permission-group__items {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 16px;
  padding: 14px;
}

.permission-group__items :deep(.el-checkbox) {
  margin-right: 0;
}

.permission-group__items :deep(.el-checkbox__label) {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--adw-text-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

.permission-summary {
  align-self: start;
  display: grid;
  justify-items: center;
  padding: 22px 18px;
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-md);
  color: var(--adw-text-secondary);
  text-align: center;
}

.permission-summary__icon {
  margin-bottom: 10px;
  color: #10b981;
  font-size: 42px;
}

.permission-summary b {
  margin-top: 5px;
  color: var(--adw-text-primary);
  font-size: 26px;
}

.permission-summary > span {
  font-size: 12px;
}

.permission-summary :deep(.el-divider) {
  width: 100%;
  margin: 18px 0;
}

.permission-summary h3 {
  justify-self: start;
  margin: 0 0 6px;
  color: var(--adw-text-primary);
  font-size: 13px;
}

.permission-summary p {
  margin: 0 0 16px;
  color: var(--adw-text-secondary);
  font-size: 12px;
  line-height: 1.6;
  text-align: left;
}

.permission-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--adw-space-4);
  margin-top: var(--adw-space-5);
  padding: 14px 16px;
  border: 1px solid #cfe0ff;
  border-radius: var(--adw-radius-md);
  color: #335b98;
  background: #f1f6ff;
  font-size: 13px;
}

.placeholder-panel {
  display: grid;
  min-height: 430px;
  place-content: center;
  justify-items: center;
  gap: 12px;
  color: var(--adw-text-secondary);
}

.placeholder-panel .el-icon {
  color: var(--adw-color-primary);
  font-size: 38px;
}

.placeholder-panel strong {
  color: var(--adw-text-primary);
}

:deep(.member-table) {
  overflow: hidden;
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-md);
}

:deep(.member-table__header),
:deep(.member-table__row) {
  display: grid;
  grid-template-columns: minmax(220px, 1.5fr) minmax(130px, 1fr) 140px 80px;
  align-items: center;
  gap: var(--adw-space-4);
  padding: 14px 16px;
}

:deep(.member-table__header) {
  color: var(--adw-text-secondary);
  background: #fafbfe;
  font-size: 12px;
}

:deep(.member-table__row) {
  border-top: 1px solid var(--adw-border-color-light);
  color: var(--adw-text-secondary);
  font-size: 13px;
}

:deep(.member-table__identity) {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

:deep(.member-table__identity div) {
  display: grid;
  gap: 3px;
  min-width: 0;
}

:deep(.member-table__identity strong) {
  overflow: hidden;
  color: var(--adw-text-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.member-table__identity small) {
  color: var(--adw-text-tertiary);
  font-size: 11px;
}

:deep(.member-table__avatar) {
  display: inline-flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  background: var(--adw-color-primary);
  font-size: 12px;
}

:deep(.member-table__select) {
  min-width: 120px;
  padding: 6px 8px;
  border: 1px solid var(--adw-border-color);
  border-radius: 6px;
  color: var(--adw-text-primary);
  background: #fff;
}

:deep(.member-table__role) {
  color: var(--adw-text-primary);
}

:deep(.member-table__muted),
:deep(.member-table__empty) {
  color: var(--adw-text-tertiary);
}

:deep(.member-table__empty) {
  padding: 56px 20px;
  text-align: center;
}

.role-dialog__permission-picker {
  display: flex;
  min-height: 230px;
  overflow: hidden;
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-sm);
}

.role-dialog__module-list {
  display: grid;
  align-content: start;
  width: 132px;
  flex: 0 0 auto;
  padding: 6px;
  border-right: 1px solid var(--adw-border-color-light);
  background: #fafbfe;
}

.role-dialog__module-item {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 9px 8px;
  border: 0;
  border-radius: 5px;
  color: var(--adw-text-secondary);
  background: transparent;
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.role-dialog__module-item:hover,
.role-dialog__module-item--active {
  color: var(--adw-color-primary);
  background: #eaf1ff;
  font-weight: 600;
}

.role-dialog__module-item small {
  flex: 0 0 auto;
  color: var(--adw-text-tertiary);
  font-size: 11px;
  font-weight: 400;
}

.role-dialog__permission-panel {
  min-width: 0;
  flex: 1;
}

.role-dialog__permission-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 11px 14px;
  border-bottom: 1px solid var(--adw-border-color-light);
  background: #fff;
}

.role-dialog__permission-panel-header div {
  display: grid;
  gap: 3px;
}

.role-dialog__permission-panel-header strong {
  color: var(--adw-text-primary);
  font-size: 14px;
}

.role-dialog__permission-panel-header span,
.role-dialog__permission-panel-count {
  color: var(--adw-text-secondary);
  font-size: 12px;
}

.role-dialog__permission-panel-count {
  flex: 0 0 auto;
}

.role-dialog__permission-items {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
  max-height: 180px;
  padding: 12px 14px;
  overflow-y: auto;
}

.role-dialog__permission-items :deep(.el-checkbox) {
  min-width: 0;
  margin-right: 0;
}

.role-dialog__permission-items :deep(.el-checkbox__label) {
  overflow: hidden;
  color: var(--adw-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-dialog__permission-empty {
  display: grid;
  flex: 1;
  place-items: center;
  color: var(--adw-text-tertiary);
}

.role-dialog__module-item:focus-visible {
  outline: 2px solid rgb(44 91 220 / 35%);
  outline-offset: -2px;
}

.form-control {
  width: 100%;
}

.form-help {
  margin-top: 6px;
  color: var(--adw-text-tertiary);
  font-size: 12px;
}

@media (max-width: 1100px) {
  .access-layout {
    grid-template-columns: 260px minmax(0, 1fr);
  }

  .permission-content {
    grid-template-columns: 1fr;
  }

  .permission-summary {
    grid-template-columns: auto auto auto auto;
    justify-items: start;
    align-items: center;
    gap: 8px 12px;
    text-align: left;
  }

  .permission-summary__icon,
  .permission-summary > span,
  .permission-summary h3,
  .permission-summary p,
  .permission-summary :deep(.el-divider) {
    display: none;
  }
}

@media (max-width: 760px) {
  .access-layout {
    display: block;
  }

  .role-list {
    margin-bottom: var(--adw-space-5);
  }

  .access-detail__header,
  .permission-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .permission-group__items {
    grid-template-columns: 1fr;
  }

  :deep(.member-table) {
    overflow-x: auto;
  }

  :deep(.member-table__header),
  :deep(.member-table__row) {
    min-width: 650px;
  }
}
</style>
