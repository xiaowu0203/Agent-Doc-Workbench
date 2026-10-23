import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ElMessage } from 'element-plus'
import CopyButton from './CopyButton.vue'

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})
describe('复制稳定身份', () => {
  it('不转换长整数，复制当前原值', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', { clipboard: { writeText } })
    vi.spyOn(ElMessage, 'success').mockImplementation(() => ({ close: vi.fn() }))
    const wrapper = mount(CopyButton, { props: { value: '9007199254740993', label: '运行 ID' } })
    await wrapper.get('button').trigger('click')
    expect(writeText).toHaveBeenCalledWith('9007199254740993')
    expect(wrapper.get('button').attributes('aria-label')).toBe('复制运行 ID')
    wrapper.unmount()
  })
  it('浏览器拒绝剪贴板时给出手动复制提示', async () => {
    vi.stubGlobal('navigator', {
      clipboard: { writeText: vi.fn().mockRejectedValue(new Error('denied')) },
    })
    const warning = vi.spyOn(ElMessage, 'warning').mockImplementation(() => ({ close: vi.fn() }))
    const wrapper = mount(CopyButton, { props: { value: 'execution.cost', label: '指标 key' } })
    await wrapper.get('button').trigger('click')
    expect(warning).toHaveBeenCalledWith('复制失败，请选中原文手动复制')
    expect(wrapper.get('button').element.disabled).toBe(false)
    wrapper.unmount()
  })
})
