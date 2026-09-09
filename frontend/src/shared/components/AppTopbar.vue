<template>
  <header class="app-topbar">
    <el-button
      class="app-topbar__toggle"
      text
      aria-label="折叠侧栏"
      @click="$emit('toggle-sidebar')"
    >
      <el-icon :size="22"><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
    </el-button>

    <button
      type="button"
      class="app-topbar__search"
      aria-label="打开全局搜索"
      @click="searchVisible = true"
    >
      <el-icon><Search /></el-icon>
      <span>搜索文档、任务或 Agent</span>
      <kbd>⌘ K</kbd>
    </button>

    <div class="app-topbar__spacer" />
    <el-button type="primary" @click="openSpaceCreate">
      <el-icon><Plus /></el-icon>
      创建空间
    </el-button>
    <el-button
      v-if="canDeleteSpace"
      type="danger"
      plain
      :loading="spaceDeleting"
      @click="deleteCurrentSpace"
    >
      删除空间
    </el-button>
    <el-divider direction="vertical" />
    <el-dropdown class="app-topbar__user-menu" trigger="click" @command="handleUserCommand">
      <button
        type="button"
        class="app-topbar__user"
        :title="authStore.user?.username"
        aria-label="打开用户菜单"
      >
        <span class="app-topbar__avatar">{{ initials }}</span>
        <span class="app-topbar__username">{{
          authStore.user?.nickname || authStore.user?.username || '当前用户'
        }}</span>
        <el-icon><ArrowDown /></el-icon>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="change-password">修改密码</el-dropdown-item>
          <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <el-dialog
      v-model="spaceDialogVisible"
      title="创建空间"
      width="460px"
      :close-on-click-modal="false"
      @closed="resetSpaceForm"
    >
      <el-form
        ref="spaceFormRef"
        :model="spaceForm"
        :rules="spaceRules"
        label-position="top"
        @submit.prevent="submitSpaceCreate"
      >
        <el-form-item label="空间名称" prop="name">
          <el-input
            v-model="spaceForm.name"
            maxlength="100"
            show-word-limit
            placeholder="请输入空间名称"
          />
        </el-form-item>
        <el-form-item label="空间描述" prop="description">
          <el-input
            v-model="spaceForm.description"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="可选"
          />
        </el-form-item>
        <el-alert v-if="spaceError" :title="spaceError" type="error" :closable="false" />
      </el-form>
      <template #footer>
        <el-button @click="spaceDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="spaceSubmitting" @click="submitSpaceCreate">
          创建并进入
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="passwordDialogVisible"
      title="修改密码"
      width="420px"
      :close-on-click-modal="false"
      @closed="resetPasswordForm"
    >
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-position="top"
        @submit.prevent="submitPasswordChange"
      >
        <el-form-item label="当前密码" prop="currentPassword">
          <el-input
            v-model="passwordForm.currentPassword"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="请输入当前密码"
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="请输入新密码（6-64 位）"
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="请再次输入新密码"
          />
        </el-form-item>
        <el-alert v-if="passwordError" :title="passwordError" type="error" :closable="false" />
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSubmitting" @click="submitPasswordChange">
          确认修改
        </el-button>
      </template>
    </el-dialog>

    <GlobalSearchDialog v-model="searchVisible" @create-space="openSpaceCreate" />
  </header>
</template>

<script setup lang="ts">
import { ArrowDown, Expand, Fold, Plus, Search } from '@element-plus/icons-vue'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElDivider,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormItemRule,
  type FormRules,
} from 'element-plus'
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { normalizeApiError } from '@/api/errors'
import GlobalSearchDialog from '@/features/search/components/GlobalSearchDialog.vue'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useAuthStore } from '@/stores/auth'
import { useWorkspaceStore } from '@/stores/workspace'

defineProps<{ collapsed: boolean }>()
defineEmits<{ 'toggle-sidebar': [] }>()

const authStore = useAuthStore()
const workspaceStore = useWorkspaceStore()
const router = useRouter()
const passwordDialogVisible = ref(false)
const searchVisible = ref(false)
const spaceDialogVisible = ref(false)
const spaceSubmitting = ref(false)
const spaceDeleting = ref(false)
const spaceError = ref('')
const passwordSubmitting = ref(false)
const passwordError = ref('')
const passwordFormRef = ref<FormInstance>()
const spaceFormRef = ref<FormInstance>()
const spaceForm = reactive({ name: '', description: '' })
const passwordForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})
const validateConfirmPassword: FormItemRule['validator'] = (_rule, value, callback) => {
  if (!value) callback(new Error('请确认新密码'))
  else if (value !== passwordForm.newPassword) callback(new Error('两次输入的密码不一致'))
  else callback()
}
const passwordRules: FormRules<typeof passwordForm> = {
  currentPassword: [
    { required: true, min: 6, max: 64, message: '当前密码长度为 6-64 位', trigger: 'blur' },
  ],
  newPassword: [
    { required: true, min: 6, max: 64, message: '新密码长度为 6-64 位', trigger: 'blur' },
  ],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }],
}
const spaceRules: FormRules<typeof spaceForm> = {
  name: [{ required: true, whitespace: true, message: '请输入空间名称', trigger: 'blur' }],
}
const initials = computed(() => {
  const name = authStore.user?.nickname || authStore.user?.username || 'AD'
  return name.slice(0, 2).toUpperCase()
})
const canDeleteSpace = computed(
  () =>
    workspaceStore.currentSpaceId !== null &&
    workspaceStore.hasPermission(SPACE_PERMISSIONS.SPACE_DELETE),
)

function openSpaceCreate(): void {
  spaceDialogVisible.value = true
  spaceError.value = ''
}

async function submitSpaceCreate(): Promise<void> {
  if (spaceSubmitting.value || !(await spaceFormRef.value?.validate().catch(() => false))) return

  spaceSubmitting.value = true
  spaceError.value = ''
  try {
    const space = await workspaceStore.createSpace({
      name: spaceForm.name.trim(),
      description: spaceForm.description.trim() || undefined,
    })
    spaceDialogVisible.value = false
    ElMessage.success('空间创建成功')
    await router.push(`/spaces/${space.id}/overview`)
  } catch (error) {
    spaceError.value = normalizeApiError(error).message
  } finally {
    spaceSubmitting.value = false
  }
}

function resetSpaceForm(): void {
  spaceFormRef.value?.resetFields()
  spaceForm.name = ''
  spaceForm.description = ''
  spaceError.value = ''
}

async function deleteCurrentSpace(): Promise<void> {
  const space = workspaceStore.currentSpace
  if (!space || spaceDeleting.value) return
  try {
    await ElMessageBox.confirm(
      `删除后将移除空间及其成员关系，确定删除“${space.name}”吗？`,
      '删除空间',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      },
    )
    spaceDeleting.value = true
    await workspaceStore.deleteSpace(space.id)
    ElMessage.success('空间已删除')
    await router.replace('/')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(normalizeApiError(error).message)
    }
  } finally {
    spaceDeleting.value = false
  }
}

function handleUserCommand(command: string | number | object): void {
  if (command === 'change-password') {
    passwordDialogVisible.value = true
    passwordError.value = ''
    return
  }
  if (command === 'logout') void logout()
}

async function logout(): Promise<void> {
  try {
    await authStore.logout()
  } catch {
    authStore.clearSession()
  } finally {
    workspaceStore.clearWorkspace()
    await router.replace('/login')
  }
}

async function submitPasswordChange(): Promise<void> {
  if (passwordSubmitting.value || !(await passwordFormRef.value?.validate().catch(() => false))) {
    return
  }

  passwordSubmitting.value = true
  passwordError.value = ''
  try {
    await authStore.changePassword({
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword,
    })
    passwordDialogVisible.value = false
    workspaceStore.clearWorkspace()
    authStore.clearSession()
    ElMessage.success('密码修改成功，请重新登录')
    await router.replace('/login')
  } catch (error) {
    passwordError.value = normalizeApiError(error).message
  } finally {
    passwordSubmitting.value = false
  }
}

function resetPasswordForm(): void {
  passwordFormRef.value?.resetFields()
  passwordError.value = ''
}
</script>

<style scoped>
.app-topbar {
  display: flex;
  height: var(--adw-topbar-height);
  align-items: center;
  gap: var(--adw-space-4);
  padding: 0 var(--adw-space-6);
  border-bottom: 1px solid var(--adw-border-color);
  background: var(--adw-surface);
}

.app-topbar__toggle {
  color: var(--adw-text-primary);
}

.app-topbar__search {
  display: flex;
  width: min(100%, 430px);
  height: 38px;
  align-items: center;
  gap: var(--adw-space-2);
  padding: 0 var(--adw-space-3);
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
  color: var(--adw-text-tertiary);
  background: var(--adw-surface-muted);
  font-size: var(--adw-font-size-body);
  cursor: pointer;
  text-align: left;
}

.app-topbar__search:hover {
  border-color: var(--adw-color-primary);
}

.app-topbar__search:focus-visible {
  outline: 2px solid var(--adw-color-primary);
  outline-offset: 2px;
}

.app-topbar__search kbd {
  margin-left: auto;
  padding: 1px 6px;
  border: 1px solid var(--adw-border-color);
  border-radius: 4px;
  color: var(--adw-text-tertiary);
  background: var(--adw-surface);
  font-size: 11px;
}

.app-topbar__spacer {
  flex: 1;
}

.app-topbar :deep(.el-divider--vertical) {
  height: 24px;
  margin: 0;
}

.app-topbar__user {
  display: flex;
  padding: 0;
  border: 0;
  align-items: center;
  gap: var(--adw-space-2);
  color: var(--adw-text-primary);
  background: transparent;
  cursor: pointer;
  white-space: nowrap;
}

.app-topbar__user:focus-visible {
  outline: 2px solid var(--adw-color-primary);
  outline-offset: 4px;
  border-radius: var(--adw-radius-sm);
}

.app-topbar__user-menu {
  display: flex;
  align-items: center;
}

.app-topbar__avatar {
  display: inline-flex;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #ffffff;
  background: var(--adw-color-primary);
  font-size: 12px;
}

@media (max-width: 720px) {
  .app-topbar {
    padding-inline: var(--adw-space-4);
  }

  .app-topbar__search {
    display: none;
  }

  .app-topbar__username {
    display: none;
  }
}
</style>
