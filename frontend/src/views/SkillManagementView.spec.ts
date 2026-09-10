import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import SkillManagementView from './SkillManagementView.vue'

import * as skillApi from '@/features/skill/api/skill-api'
import type { Skill } from '@/features/skill/types'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

vi.mock('@/features/skill/api/skill-api', () => ({
  createSkill: vi.fn(),
  disableSkill: vi.fn(),
  enableSkill: vi.fn(),
  importSkillPackage: vi.fn(),
  searchSkills: vi.fn(),
  updateSkill: vi.fn(),
  uploadSkillVersion: vi.fn(),
  listSkillVersions: vi.fn().mockResolvedValue([]),
  listSkillAgentBindings: vi.fn().mockResolvedValue([]),
  publishSkillVersion: vi.fn(),
  downloadSkillVersion: vi.fn(),
}))

const skill: Skill = {
  id: 11,
  spaceId: 7,
  name: 'document-review',
  displayName: '文档审查',
  description: '检查文档质量、事实一致性与潜在风险',
  status: 'ACTIVE',
  versionCount: 3,
  boundAgentCount: 2,
  latestVersion: {
    id: 21,
    versionNo: 3,
    status: 'PUBLISHED',
    activationDescription: '用于检查正式文档中的事实、结构与风险',
    allowedToolCount: 4,
    createdAt: '2026-09-01T08:00:00Z',
    publishedAt: '2026-09-01T09:00:00Z',
  },
  createdBy: 1,
  createdAt: '2026-08-01T08:00:00Z',
  updatedAt: '2026-09-01T09:00:00Z',
}

async function mountView(permissions: string[]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const workspaceStore = useWorkspaceStore()
  workspaceStore.setCurrentSpace(7)
  workspaceStore.setEffectivePermissions({
    spaceId: 7,
    platformSuperAdmin: false,
    role: null,
    permissions,
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/spaces/:spaceId/skills', component: SkillManagementView }],
  })
  await router.push('/spaces/7/skills')
  await router.isReady()
  const wrapper = mount(SkillManagementView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.mocked(skillApi.searchSkills).mockResolvedValue({
    records: [skill],
    total: 1,
    pageNum: 1,
    pageSize: 12,
  })
})

describe('SkillManagementView', () => {
  it('renders real card summaries returned by the list API', async () => {
    const wrapper = await mountView([SPACE_PERMISSIONS.SKILL_READ, SPACE_PERMISSIONS.SKILL_MANAGE])

    expect(wrapper.text()).toContain('文档审查')
    expect(wrapper.text()).toContain('document-review')
    expect(wrapper.text()).toContain('3 版本')
    expect(wrapper.text()).toContain('2 Agent')
    expect(wrapper.text()).toContain('4 工具')
    expect(wrapper.text()).toContain('v3 已发布')
    expect(wrapper.text()).toContain('上传 Skill ZIP')
  })

  it('hides management actions from read-only members', async () => {
    const wrapper = await mountView([SPACE_PERMISSIONS.SKILL_READ])

    expect(wrapper.text()).not.toContain('上传 Skill ZIP')
    expect(wrapper.text()).not.toContain('新建 Skill')
    expect(wrapper.find('[aria-label="Skill 操作"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('查看详情')
  })
})
