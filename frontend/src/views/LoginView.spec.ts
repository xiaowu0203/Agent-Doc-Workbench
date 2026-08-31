import { createPinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import LoginView from './LoginView.vue'

import { useAuthStore } from '@/stores/auth'

vi.mock('element-plus', async (importOriginal) => {
  const original = await importOriginal<typeof import('element-plus')>()
  return {
    ...original,
    ElMessage: {
      info: vi.fn(),
    },
  }
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

    expect(login).toHaveBeenCalledWith({ username: 'alice', password: 'secret' })
    expect(router.currentRoute.value.path).toBe('/')
  })
})
