import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import AccessControlView from './AccessControlView.vue'

import * as accessApi from '@/features/access-control/api/access-control-api'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

vi.mock('@/features/access-control/api/access-control-api', () => ({
  addMember: vi.fn(),
  changeMemberRole: vi.fn(),
  createRole: vi.fn(),
  deleteRole: vi.fn(),
  listMemberUsers: vi.fn(),
  listMembers: vi.fn(),
  listPermissions: vi.fn(),
  listRoles: vi.fn(),
  removeMember: vi.fn(),
  replaceRolePermissions: vi.fn(),
  updateRole: vi.fn(),
}))

const role = {
  id: 11,
  spaceId: 7,
  roleKey: 'OWNER',
  displayName: '所有者',
  systemRole: true,
  description: '拥有空间全部权限',
  protectedRole: true,
  memberCount: 2,
  permissionCodes: [SPACE_PERMISSIONS.SPACE_READ],
  createdAt: '2026-08-31T00:00:00Z',
}

const viewerRole = {
  id: 12,
  spaceId: 7,
  roleKey: 'VIEWER',
  displayName: '观察者',
  systemRole: true,
  description: '只读查看空间资源',
  protectedRole: false,
  memberCount: 0,
  permissionCodes: [SPACE_PERMISSIONS.SPACE_READ],
  createdAt: '2026-08-31T00:00:00Z',
}

const customRole = {
  id: 13,
  spaceId: 7,
  roleKey: 'content-reviewer',
  displayName: '内容审核员',
  systemRole: false,
  description: '负责内容审核',
  protectedRole: false,
  memberCount: 0,
  permissionCodes: [SPACE_PERMISSIONS.SPACE_READ],
  createdAt: '2026-08-31T00:00:00Z',
}

const permissions = [
  {
    code: SPACE_PERMISSIONS.SPACE_READ,
    name: '查看空间',
    category: 'SPACE',
    description: '查看空间',
    sortOrder: 1,
  },
  {
    code: SPACE_PERMISSIONS.SPACE_MANAGE,
    name: '管理空间',
    category: 'SPACE',
    description: '管理空间',
    sortOrder: 2,
  },
]

async function mountAccess(
  routeName: 'space-access-roles' | 'space-access-members',
  permissionsForUser = Object.values(SPACE_PERMISSIONS),
) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const workspaceStore = useWorkspaceStore()
  workspaceStore.spaces = [
    {
      id: 7,
      name: '产品研发空间',
      description: '文档协作空间',
      ownerId: 1,
      tokenBudget: null,
      status: 'ACTIVE',
      role: null,
      platformSuperAdmin: false,
      createdAt: '2026-08-31T00:00:00Z',
    },
  ]
  workspaceStore.setCurrentSpace(7)
  workspaceStore.setEffectivePermissions({
    spaceId: 7,
    platformSuperAdmin: false,
    role: null,
    permissions: permissionsForUser,
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/spaces/:spaceId/access/roles',
        name: 'space-access-roles',
        component: AccessControlView,
      },
      {
        path: '/spaces/:spaceId/access/members',
        name: 'space-access-members',
        component: AccessControlView,
      },
    ],
  })
  await router.push({ name: routeName, params: { spaceId: 7 } })
  await router.isReady()
  const wrapper = mount(AccessControlView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.mocked(accessApi.listRoles).mockResolvedValue([role, viewerRole, customRole])
  vi.mocked(accessApi.listPermissions).mockResolvedValue(permissions)
  vi.mocked(accessApi.listMembers).mockResolvedValue([
    {
      id: 101,
      userId: 1,
      role: { roleId: 11, roleKey: 'OWNER', displayName: '所有者' },
      createdAt: '2026-08-31T00:00:00Z',
    },
  ])
  vi.mocked(accessApi.listMemberUsers).mockResolvedValue([
    { userId: 1, username: 'admin', nickname: '管理员' },
  ])
  vi.mocked(accessApi.replaceRolePermissions).mockResolvedValue(role)
})

describe('AccessControlView', () => {
  it('renders real role counts and grouped permissions', async () => {
    const wrapper = await mountAccess('space-access-roles')

    expect(wrapper.text()).toContain('所有者')
    expect(wrapper.text()).toContain('系统默认')
    expect(wrapper.text()).toContain('自定义')
    expect(wrapper.text()).toContain('2 位成员')
    expect(wrapper.text()).toContain('空间 (2 项)')
    expect(wrapper.text()).toContain('space:read')
  })

  it('allows permission groups to collapse independently', async () => {
    const wrapper = await mountAccess('space-access-roles')
    const group = wrapper.get('.permission-group')
    const title = group.get('.permission-group__title')

    expect(group.get('.permission-group__items').isVisible()).toBe(true)
    await title.trigger('click')
    expect(group.get('.permission-group__items').isVisible()).toBe(false)
  })

  it('keeps the change log tab as a development placeholder', async () => {
    const wrapper = await mountAccess('space-access-roles')

    const changeTab = wrapper.findAll('nav button').find((button) => button.text() === '变更记录')
    expect(changeTab).toBeDefined()
    await changeTab!.trigger('click')
    expect(wrapper.text()).toContain('变更记录待开发，敬请期待')
  })

  it('renders member names from the space-scoped user summary endpoint', async () => {
    const wrapper = await mountAccess('space-access-members')

    expect(wrapper.text()).toContain('管理员')
    expect(wrapper.text()).toContain('用户 ID：1')
  })

  it('filters role-bound members by the selected role', async () => {
    const wrapper = await mountAccess('space-access-roles')

    await wrapper.findAll('.role-card')[1].trigger('click')
    const memberTab = wrapper.findAll('nav button').find((button) => button.text() === '成员绑定')
    await memberTab!.trigger('click')

    expect(wrapper.text()).toContain('当前空间暂无成员')
    expect(wrapper.text()).not.toContain('管理员')
  })

  it('does not expose role editing controls without role management permission', async () => {
    const wrapper = await mountAccess('space-access-roles', [SPACE_PERMISSIONS.ROLE_READ])

    expect(wrapper.text()).not.toContain('新建角色')
    const saveButton = wrapper.findAll('button').find((button) => button.text() === '保存权限')
    expect(saveButton).toBeDefined()
    expect(saveButton!.attributes('disabled')).toBeDefined()
  })
})
