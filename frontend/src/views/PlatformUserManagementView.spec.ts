import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import PlatformUserManagementView from './PlatformUserManagementView.vue'

import * as platformApi from '@/features/platform-management/api/platform-management-api'

vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return {
    ...actual,
    useRoute: () => ({ query: {} }),
    useRouter: () => ({ replace: vi.fn() }),
  }
})

vi.mock('@/features/platform-management/api/platform-management-api', () => ({
  createPlatformUser: vi.fn(),
  getPlatformUserStats: vi.fn(),
  listDepartments: vi.fn(),
  queryPlatformUserMemberships: vi.fn(),
  replacePlatformUserRoles: vi.fn(),
  resetPlatformUserPassword: vi.fn(),
  searchPlatformUsers: vi.fn(),
  updatePlatformUser: vi.fn(),
  updatePlatformUserStatus: vi.fn(),
}))

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  vi.mocked(platformApi.listDepartments).mockResolvedValue([
    {
      id: 5,
      parentId: 0,
      name: '产品研发部',
      code: 'PRODUCT_RD',
      leaderUserId: null,
      leaderName: null,
      memberCount: 1,
      childCount: 0,
      sortOrder: 0,
      status: 1,
      createdAt: '2026-09-01T09:00:00',
      updatedAt: '2026-09-01T09:00:00',
    },
  ])
  vi.mocked(platformApi.getPlatformUserStats).mockResolvedValue({
    total: 1,
    enabled: 1,
    disabled: 0,
    unassignedDepartment: 0,
  })
  vi.mocked(platformApi.searchPlatformUsers).mockResolvedValue({
    records: [
      {
        id: 10,
        username: 'zhangsan',
        nickname: '张三',
        email: 'zhangsan@example.com',
        avatarUrl: null,
        department: { id: 5, name: '产品研发部', code: 'PRODUCT_RD' },
        jobTitle: '后端工程师',
        status: 1,
        platformRoles: [],
        lastLoginAt: null,
        createdAt: '2026-09-01T09:00:00',
        updatedAt: '2026-09-01T09:00:00',
      },
    ],
    total: 1,
    pageNum: 1,
    pageSize: 10,
  })
  vi.mocked(platformApi.queryPlatformUserMemberships).mockResolvedValue([])
})

describe('PlatformUserManagementView', () => {
  it('renders real platform user and organization summaries', async () => {
    const wrapper = mount(PlatformUserManagementView)
    await flushPromises()

    expect(wrapper.text()).toContain('用户账号属于平台')
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('产品研发部')
    expect(wrapper.text()).toContain('尚未加入空间')
    expect(platformApi.searchPlatformUsers).toHaveBeenCalled()
    expect(platformApi.queryPlatformUserMemberships).toHaveBeenCalledWith([10], expect.anything())
  })
})
