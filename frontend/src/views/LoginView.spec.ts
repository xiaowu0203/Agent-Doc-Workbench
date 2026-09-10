import { createPinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import LoginView from './LoginView.vue'

import * as authApi from '@/features/auth/api/auth-api'
import { useAuthStore } from '@/stores/auth'

vi.mock('element-plus', async (importOriginal) => {
  const original = await importOriginal<typeof import('element-plus')>()
  return {
    ...original,
    ElMessage: {
      info: vi.fn(),
      success: vi.fn(),
    },
  }
})

vi.mock('@/features/auth/api/auth-api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/features/auth/api/auth-api')>()
  return { ...original, register: vi.fn() }
})

async function mountLoginView() {
  const pinia = createPinia()
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', component: LoginView },
      { path: '/', component: { template: '<div>home</div>' } },
    ],
  })
  await router.push('/login')
  await router.isReady()

  const wrapper = mount(LoginView, {
    global: {
      plugins: [pinia, router],
    },
  })

  return { pinia, router, wrapper }
}

describe('LoginView', () => {
  it('renders the account login form and reserved login providers', async () => {
    const { wrapper } = await mountLoginView()

    expect(wrapper.text()).toContain('登录工作台')
    expect(wrapper.text()).toContain('邮箱或用户名')
    expect(wrapper.text()).toContain('GitHub 登录')
    expect(wrapper.text()).toContain('企业 OAuth2')
  })

  it('shows a coming soon message for reserved capabilities', async () => {
    const { wrapper } = await mountLoginView()
    const { ElMessage } = await import('element-plus')

    await wrapper.get('.login-providers .el-button').trigger('click')

    expect(ElMessage.info).toHaveBeenCalledWith('即将支持')
  })

  it('submits credentials and enters the workbench', async () => {
    const { pinia, router, wrapper } = await mountLoginView()
    const login = vi.spyOn(useAuthStore(pinia), 'login').mockResolvedValue()

    await wrapper.get('input[placeholder="请输入邮箱或用户名"]').setValue('alice')
    await wrapper.get('input[placeholder="请输入密码"]').setValue('secret')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(login).toHaveBeenCalledWith({ username: 'alice', password: 'secret', remember: false })
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('registers an account and returns to login with the username filled', async () => {
    const { wrapper } = await mountLoginView()
    vi.mocked(authApi.register).mockResolvedValue({
      id: 2,
      username: 'alice',
      nickname: 'Alice',
      email: 'alice@example.com',
      avatarUrl: null,
    })

    await wrapper.get('.login-register button').trigger('click')
    await wrapper.get('input[placeholder="3-32 位字母、数字或下划线"]').setValue('alice')
    await wrapper.get('input[placeholder="请输入昵称"]').setValue('Alice')
    await wrapper.get('input[placeholder="请输入邮箱"]').setValue('alice@example.com')
    await wrapper.get('input[placeholder="请输入密码（6-64 位）"]').setValue('secret1')
    await wrapper.get('input[placeholder="请再次输入密码"]').setValue('secret1')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(authApi.register).toHaveBeenCalledWith({
      username: 'alice',
      password: 'secret1',
      nickname: 'Alice',
      email: 'alice@example.com',
    })
    expect(wrapper.text()).toContain('登录工作台')
    expect(wrapper.get('input[placeholder="请输入邮箱或用户名"]').element).toHaveProperty(
      'value',
      'alice',
    )
  })

  it('sends the remember me choice with login', async () => {
    const { pinia, wrapper } = await mountLoginView()
    const authStore = useAuthStore(pinia)
    vi.spyOn(authStore, 'login').mockResolvedValue()

    await wrapper.get('input[type="checkbox"]').setValue(true)
    await wrapper.get('input[placeholder="请输入邮箱或用户名"]').setValue('alice')
    await wrapper.get('input[placeholder="请输入密码"]').setValue('secret')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(authStore.login).toHaveBeenCalledWith({
      username: 'alice',
      password: 'secret',
      remember: true,
    })
  })
})
