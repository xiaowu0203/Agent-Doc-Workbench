import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import PlatformDepartmentManagementView from './PlatformDepartmentManagementView.vue'

import * as platformApi from '@/features/platform-management/api/platform-management-api'

vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return { ...actual, useRouter: () => ({ push: vi.fn() }) }
})

vi.mock('@/features/platform-management/api/platform-management-api', () => ({
  createDepartment: vi.fn(),
  deleteDepartment: vi.fn(),
  getPlatformUserStats: vi.fn(),
  listDepartments: vi.fn(),
  queryPlatformUserMemberships: vi.fn(),
  searchPlatformUsers: vi.fn(),
  updateDepartment: vi.fn(),
}))

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(platformApi.listDepartments).mockResolvedValue([
    {
      id: 5,
      parentId: 0,
      name: '产品研发部',
      code: 'PRODUCT_RD',
      leaderUserId: 10,
      leaderName: '张三',
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
    records: [],
    total: 0,
    pageNum: 1,
    pageSize: 100,
  })
  vi.mocked(platformApi.queryPlatformUserMemberships).mockResolvedValue([])
})

describe('PlatformDepartmentManagementView', () => {
  it('renders department scope and selected department details', async () => {
    const wrapper = mount(PlatformDepartmentManagementView)
    await flushPromises()

    expect(wrapper.text()).toContain('部门仅用于组织归属')
    expect(wrapper.text()).toContain('产品研发部')
    expect(wrapper.text()).toContain('PRODUCT_RD')
    expect(wrapper.text()).toContain('张三')
  })
})
