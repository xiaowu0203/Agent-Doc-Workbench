import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it } from 'vitest'

import { evaluationRoute } from './routes'
import EvaluationWorkbenchLayout from './components/EvaluationWorkbenchLayout.vue'
import EvaluationEntryView from '@/views/EvaluationEntryView.vue'
import { installRouterGuards } from '@/router/guards'
import AppSidebar from '@/shared/components/AppSidebar.vue'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import pinia from '@/stores'
import { useAuthStore } from '@/stores/auth'
import { useWorkspaceStore } from '@/stores/workspace'

const spaceId = '2104855879319314433'
const paths = ['evaluators', 'test-cases', 'datasets', 'runs', 'experiments'].flatMap(
  (resource) => [
    `/spaces/${spaceId}/evaluation/${resource}`,
    `/spaces/${spaceId}/evaluation/${resource}/2104902086192304129`,
  ],
)

function testRouter(guarded = true) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/',
        component: { template: '<router-view />' },
        meta: { requiresAuth: true },
        children: [evaluationRoute],
      },
      { path: '/login', component: { template: '<div />' } },
      { path: '/forbidden', component: { template: '<div />' } },
      { path: '/spaces/:spaceId/tasks/:taskId', component: { template: '<div />' } },
    ],
  })
  if (guarded) installRouterGuards(router)
  return router
}

beforeEach(() => {
  useAuthStore(pinia).$reset()
  useWorkspaceStore(pinia).$reset()
  useAuthStore(pinia).setSession({
    accessToken: 'test-token',
    user: { id: 1, username: 'member', nickname: null, email: null, avatarUrl: null },
    platformRoles: [],
  })
  const workspace = useWorkspaceStore(pinia)
  workspace.spaces = [
    {
      id: spaceId,
      name: '测试空间',
      description: null,
      ownerId: 1,
      tokenBudget: null,
      status: 'ACTIVE',
      role: { roleId: 1, roleKey: 'OWNER', displayName: '所有者' },
      platformSuperAdmin: false,
      createdAt: '',
    },
  ]
  workspace.setCurrentSpace(spaceId)
})

function setPermissions(permissions: string[]) {
  useWorkspaceStore(pinia).setEffectivePermissions({
    spaceId,
    platformSuperAdmin: false,
    role: null,
    permissions,
  })
}

describe('evaluation navigation and permission boundary', () => {
  it('preserves a selected source task and only links back with task read permission', async () => {
    setPermissions([SPACE_PERMISSIONS.EVALUATION_READ])
    const router = testRouter(false)
    await router.push(`/spaces/${spaceId}/evaluation/test-cases?sourceTaskId=2104902086192304129`)
    const wrapper = mount(EvaluationEntryView, {
      props: { section: 'test-cases' },
      global: { plugins: [pinia, router] },
    })
    expect(wrapper.text()).toContain('已选择来源任务 #2104902086192304129')
    expect(wrapper.findAll('a')).toHaveLength(0)
    setPermissions([SPACE_PERMISSIONS.EVALUATION_READ, SPACE_PERMISSIONS.TASK_READ])
    await wrapper.vm.$nextTick()
    expect(wrapper.find('a').attributes('href')).toBe(
      `/spaces/${spaceId}/tasks/2104902086192304129?tab=evidence`,
    )
    await router.push(`/spaces/${spaceId}/evaluation/test-cases?sourceTaskId=invalid`)
    expect(wrapper.text()).not.toContain('已选择来源任务')
    wrapper.unmount()
  })
  it.each(paths)('rejects a deep link without read permission: %s', async (path) => {
    setPermissions([SPACE_PERMISSIONS.EVALUATION_MANAGE, SPACE_PERMISSIONS.EVALUATION_RUN])
    const router = testRouter()
    await router.push(path)
    expect(router.currentRoute.value.path).toBe('/forbidden')
  })

  it.each(paths)('allows read-only access with inherited route metadata: %s', async (path) => {
    setPermissions([SPACE_PERMISSIONS.EVALUATION_READ])
    const router = testRouter()
    await router.push(path)
    expect(router.currentRoute.value.path).toBe(path)
    expect(router.currentRoute.value.meta.permission).toBe(SPACE_PERMISSIONS.EVALUATION_READ)
  })

  it('redirects the entry to test cases and preserves string identities', async () => {
    setPermissions([SPACE_PERMISSIONS.EVALUATION_READ])
    const router = testRouter()
    await router.push(`/spaces/${spaceId}/evaluation`)
    expect(router.currentRoute.value.path).toBe(`/spaces/${spaceId}/evaluation/test-cases`)
    expect(router.currentRoute.value.params.spaceId).toBe(spaceId)
  })

  it('redirects unauthenticated deep links to login with their original destination', async () => {
    useAuthStore(pinia).clearSession()
    const router = testRouter()
    await router.push(paths[0]!)
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe(paths[0])
  })

  it('does not inherit read permission from another space', async () => {
    setPermissions([SPACE_PERMISSIONS.EVALUATION_READ])
    const workspace = useWorkspaceStore(pinia)
    workspace.spaces.push({ ...workspace.spaces[0]!, id: '2' })
    workspace.setEffectivePermissions({
      spaceId: '2',
      platformSuperAdmin: false,
      role: null,
      permissions: [],
    })
    const router = testRouter()
    await router.push('/spaces/2/evaluation/runs')
    expect(router.currentRoute.value.path).toBe('/forbidden')
    expect(workspace.currentSpaceId).toBe(spaceId)
  })

  it.each([
    { permissions: [] },
    { permissions: [SPACE_PERMISSIONS.EVALUATION_MANAGE] },
    { permissions: [SPACE_PERMISSIONS.EVALUATION_RUN] },
  ])('hides the single sidebar entry without read: $permissions', async ({ permissions }) => {
    setPermissions(permissions)
    const router = testRouter(false)
    await router.push(paths[0]!)
    const wrapper = mount(AppSidebar, {
      props: { collapsed: false },
      global: { plugins: [pinia, router], stubs: { ElSelect: true, ElOption: true } },
    })
    expect(wrapper.text()).not.toContain('评估与实验')
    wrapper.unmount()
  })

  it('shows one active sidebar entry for a resource detail', async () => {
    setPermissions([SPACE_PERMISSIONS.EVALUATION_READ])
    const router = testRouter(false)
    await router.push(paths[1]!)
    const wrapper = mount(AppSidebar, {
      props: { collapsed: false },
      global: { plugins: [pinia, router], stubs: { ElSelect: true, ElOption: true } },
    })
    const links = wrapper.findAll('a').filter((link) => link.text() === '评估与实验')
    expect(links).toHaveLength(1)
    expect(links[0]!.attributes('href')).toBe(`/spaces/${spaceId}/evaluation`)
    expect(links[0]!.classes()).toContain('app-sidebar__link--active')
    wrapper.unmount()
  })

  it('keeps two-level navigation active on details and follows the route space', async () => {
    const router = testRouter(false)
    await router.push(paths[1]!)
    const wrapper = mount(EvaluationWorkbenchLayout, {
      global: { plugins: [router], stubs: { RouterView: true } },
    })
    expect(wrapper.get('[aria-label="评估工作台导航"] [aria-current="page"]').text()).toBe(
      '评估目录',
    )
    expect(wrapper.get('[aria-label="评估目录资源"] [aria-current="page"]').text()).toBe('评估器')
    await router.push('/spaces/2/evaluation/experiments/3')
    expect(wrapper.find('[aria-label="评估目录资源"]').exists()).toBe(false)
    expect(wrapper.get('[aria-label="评估工作台导航"] [aria-current="page"]').text()).toBe(
      '离线实验',
    )
    expect(
      wrapper
        .findAll('a')
        .every((link) => link.attributes('href')?.startsWith('/spaces/2/evaluation/')),
    ).toBe(true)
    wrapper.unmount()
  })
})
