import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ModelConfigDrawer from './ModelConfigDrawer.vue'

import * as modelApi from '@/features/model/api/model-api'

vi.mock('@/features/model/api/model-api', () => ({
  createModel: vi.fn(),
  testModelInput: vi.fn(),
  updateModel: vi.fn(),
}))

async function mountCreateDrawer() {
  const wrapper = mount(ModelConfigDrawer, {
    props: { open: true, model: null },
    global: { stubs: { teleport: true } },
  })
  await flushPromises()
  await wrapper.get('input[placeholder="例如：GPT-5.2"]').setValue('GPT-5.2')
  await wrapper.get('input[placeholder="例如：gpt-5.2"]').setValue('gpt-5.2')
  await wrapper.get('input[placeholder="请输入模型 API Key"]').setValue('secret-key')
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(modelApi.createModel).mockResolvedValue({} as never)
})

describe('ModelConfigDrawer', () => {
  it('tests a new configuration before saving it', async () => {
    vi.mocked(modelApi.testModelInput).mockResolvedValue({
      connected: true,
      provider: 'openai',
      errorType: null,
      statusCode: null,
      retryable: false,
      message: '模型连接成功',
    })
    const wrapper = await mountCreateDrawer()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '保存')
      ?.trigger('click')
    await flushPromises()

    expect(modelApi.testModelInput).toHaveBeenCalledOnce()
    expect(modelApi.createModel).toHaveBeenCalledOnce()
    expect(vi.mocked(modelApi.testModelInput).mock.invocationCallOrder[0]).toBeLessThan(
      vi.mocked(modelApi.createModel).mock.invocationCallOrder[0]!,
    )
  })

  it('does not save when the connectivity test fails', async () => {
    vi.mocked(modelApi.testModelInput).mockResolvedValue({
      connected: false,
      provider: 'openai',
      errorType: 'AUTHENTICATION',
      statusCode: 401,
      retryable: false,
      message: 'API Key 无效',
    })
    const wrapper = await mountCreateDrawer()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '保存')
      ?.trigger('click')
    await flushPromises()

    expect(modelApi.testModelInput).toHaveBeenCalledOnce()
    expect(modelApi.createModel).not.toHaveBeenCalled()
  })
})
