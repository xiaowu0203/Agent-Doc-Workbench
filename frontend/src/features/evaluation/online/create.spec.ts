import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import OnlineExperimentCreate from './OnlineExperimentCreate.vue'
import DurationInput from '../components/DurationInput.vue'
import { ElSelect } from 'element-plus'
import PublishedVersionPicker from '../components/PublishedVersionPicker.vue'
import * as api from './api'
import * as catalogApi from '../api/evaluation-api'
import * as agentApi from '@/features/agent/api/agent-api'
import { experiment } from './fixtures.spec-support'
import type { EvaluatorVersion } from '../types'
import type { AgentDetail } from '@/features/agent/types'
import { listDocumentTree } from '@/features/document/api/document-api'
vi.mock('./api')
vi.mock('../api/evaluation-api')
vi.mock('@/features/agent/api/agent-api')
vi.mock('@/features/document/api/document-api')
const wrappers: VueWrapper[] = []
const version = {
  id: '2104902086192304130',
  spaceId: '7',
  evaluatorId: '20',
  versionNo: 1,
  status: 'PUBLISHED',
  evaluatorKey: 'online-original-text-assertion',
  configSchemaVersion: 1,
  resultSchemaVersion: 1,
  configJson: '{}',
  contentHash: 'v1',
} as EvaluatorVersion
const button = (wrapper: VueWrapper, text: string) =>
  wrapper.findAll('button').find((value) => value.text() === text)!
async function create() {
  const wrapper = mount(OnlineExperimentCreate, {
    props: { open: true, spaceId: '7', allowed: true },
    global: {
      stubs: {
        ElDialog: { template: '<div><slot /><slot name="footer" /></div>' },
        PublishedVersionPicker: true,
      },
    },
  })
  wrappers.push(wrapper)
  await flushPromises()
  const values: Record<string, string> = {
    实验名称: '交付质量',
    实验Agent: '3',
    文档范围ID: '2104902086192304129',
    候选系统提示词: '候选提示词',
    总授权Token: '9007199254740993',
    每任务Token上限: '4096',
    差异业务理由: '减少修改',
  }
  for (const [key, value] of Object.entries(values))
    await wrapper.get(`[aria-label="${key}"]`).setValue(value)
  await flushPromises()
  await button(wrapper, '继续：规则与预算').trigger('click')
  wrapper.findComponent(PublishedVersionPicker).vm.$emit('select', version, '原始文本规则')
  await flushPromises()
  await wrapper.get('[aria-label="断言1内容"]').setValue('预期产物')
  return wrapper
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(agentApi.listAgents).mockResolvedValue([
    { id: '3', spaceId: '7', name: '文档 Agent', status: 'ENABLED' },
  ])
  vi.mocked(agentApi.getAgent).mockResolvedValue({
    id: '3',
    spaceId: '7',
    status: 'ENABLED',
    systemPrompt: '完整当前基线参考',
    configVersion: 1,
  } as AgentDetail)
  vi.mocked(api.queryPublishedOnlineVersions).mockResolvedValue([version])
  vi.mocked(api.createOnlineExperiment).mockResolvedValue(experiment)
})
afterEach(() => wrappers.splice(0).forEach((value) => value.unmount()))
describe('冻结草案创建', () => {
  it('分步返回保留输入，核对步骤才展示最终创建并清楚列出冻结内容', async () => {
    const wrapper = await create()
    expect(wrapper.get('[aria-current="step"]').text()).toContain('规则与预算')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('[aria-current="step"]').text()).toContain('核对冻结内容')
    expect(wrapper.get('[aria-label="创建草案确认"]').text()).toContain('候选提示词')
    await button(wrapper, '返回上一步').trigger('click')
    expect(wrapper.get('[aria-label="候选系统提示词"]').element).toHaveProperty(
      'value',
      '候选提示词',
    )
    expect(wrapper.find('[aria-label="创建草案确认"]').exists()).toBe(false)
    expect(api.createOnlineExperiment).not.toHaveBeenCalled()
  })
  it('按名称选择文档并精确换算两位小数概率，不改动 ID 或冻结协议', async () => {
    const wrapper = await create()
    vi.mocked(listDocumentTree).mockResolvedValue([
      {
        id: '9007199254740993',
        parentId: null,
        title: '验收草稿',
        docType: 'DRAFT',
        nodeType: 'DOCUMENT',
        children: [],
      },
    ])
    await button(wrapper, '从空间文档中选择').trigger('click')
    await flushPromises()
    await wrapper.get('[aria-label="实验文档选择"] input[type="checkbox"]').setValue(true)
    expect(
      (wrapper.get('[aria-label="文档范围ID"]').element as HTMLTextAreaElement).value,
    ).toContain('9007199254740993')
    await wrapper.get('[aria-label="候选文档概率百分比"]').setValue('50.01')
    await wrapper.get('form').trigger('submit')
    await button(wrapper, '确认创建草案').trigger('click')
    await flushPromises()
    expect(api.createOnlineExperiment).toHaveBeenCalledWith(
      expect.objectContaining({
        candidateWeightBps: 5001,
        documentIds: ['2104902086192304129', '9007199254740993'],
      }),
      expect.any(AbortSignal),
    )
  })
  it('多角色规则和文档覆盖一次冻结，秒级窗口与零观察窗口保持原值', async () => {
    const wrapper = await create()
    await wrapper.get('[aria-label="文档范围ID"]').setValue('41\n42')
    const assignmentUnit = wrapper.findAllComponents(DurationInput)[0]!.getComponent(ElSelect)
    assignmentUnit.vm.$emit('update:modelValue', 1)
    assignmentUnit.vm.$emit('change', 1)
    await wrapper.vm.$nextTick()
    await wrapper.get('[aria-label="分配窗口数值"]').setValue('1')
    const observationUnit = wrapper.findAllComponents(DurationInput)[1]!.getComponent(ElSelect)
    observationUnit.vm.$emit('update:modelValue', 1)
    observationUnit.vm.$emit('change', 1)
    await wrapper.vm.$nextTick()
    await wrapper.get('[aria-label="观察窗口数值"]').setValue('0')
    await wrapper.get('[aria-label="主规则覆盖文档"]').setValue('42')
    const override = {
      assertions: [{ field: 'originalText', operator: 'EXACT', value: '文档42专用' }],
    }
    await wrapper.get('[aria-label="主规则文档期望"]').setValue(JSON.stringify(override))
    const terminal = { ...version, id: '81', evaluatorKey: 'task-terminal-status' }
    const ledger = { ...version, id: '82', evaluatorKey: 'audit-ledger-integrity' }
    for (const [index, item, role] of [
      [1, terminal, 'AUXILIARY'],
      [2, ledger, 'GUARDRAIL'],
    ] as const) {
      await button(wrapper, '增加冻结规则').trigger('click')
      await button(wrapper, `选择规则 ${index + 1} 的已发布版本`).trigger('click')
      wrapper.findComponent(PublishedVersionPicker).vm.$emit('select', item, item.evaluatorKey)
      await flushPromises()
      await wrapper.get(`[aria-label="附加规则${index}角色"]`).setValue(role)
    }
    vi.mocked(api.queryPublishedOnlineVersions).mockResolvedValue([ledger, version, terminal])
    await wrapper.get('form').trigger('submit')
    await button(wrapper, '确认创建草案').trigger('click')
    await flushPromises()
    const payload = vi.mocked(api.createOnlineExperiment).mock.calls[0]![0]
    expect(payload).toMatchObject({ assignmentWindowSeconds: 1, completionObservationSeconds: 0 })
    expect(payload.rules.map((rule) => rule.role)).toEqual(['PRIMARY', 'AUXILIARY', 'GUARDRAIL'])
    expect(payload.rules[0]!.expectedBindings[1]).toEqual({
      documentId: '42',
      expectedJson: override,
    })
    expect(payload.rules[1]!.expectedBindings).toEqual([
      { documentId: '41', expectedJson: {} },
      { documentId: '42', expectedJson: {} },
    ])
    expect(api.queryPublishedOnlineVersions).toHaveBeenCalledTimes(1)
    expect(api.queryPublishedOnlineVersions).toHaveBeenCalledWith(
      '7',
      [version.id, '81', '82'],
      expect.any(AbortSignal),
    )
  })
  it('预算和 IDs 保持文本、NUMBER 阈值为十进制字符串，提交前重新核验发布版本', async () => {
    const wrapper = await create()
    await wrapper.get('form').trigger('submit')
    await button(wrapper, '确认创建草案').trigger('click')
    await flushPromises()
    const payload = vi.mocked(api.createOnlineExperiment).mock.calls[0]![0]
    expect(payload.authorizedTokenBudget).toBe('9007199254740993')
    expect(payload.documentIds).toEqual(['2104902086192304129'])
    expect(payload.rules[0]).toMatchObject({
      threshold: '1',
      valueType: 'NUMBER',
      evidenceTarget: 'ORIGINAL_TEXT',
      evaluatorVersionId: version.id,
    })
    expect(payload.rules[0]!.expectedBindings[0]!.expectedJson).toEqual({
      assertions: [{ field: 'originalText', operator: 'CONTAINS', value: '预期产物' }],
    })
    expect(api.queryPublishedOnlineVersions).toHaveBeenCalledWith(
      '7',
      [version.id],
      expect.any(AbortSignal),
    )
    expect(catalogApi.getEvaluatorVersion).not.toHaveBeenCalled()
    expect(wrapper.get('[aria-label="当前基线提示词参考"]').element).toHaveProperty(
      'value',
      '完整当前基线参考',
    )
    expect(wrapper.emitted('created')).toHaveLength(1)
  })
  it.each(['01', '9223372036854775808', '1\n1'])('非法/重复文档不能确认 %s', async (ids) => {
    const wrapper = await create()
    await wrapper.get('[aria-label="文档范围ID"]').setValue(ids)
    await wrapper.get('form').trigger('submit')
    expect(wrapper.find('[aria-label="创建草案确认"]').exists()).toBe(false)
    expect(api.createOnlineExperiment).not.toHaveBeenCalled()
  })
  it('修改草案使确认失效，撤销权限清除候选 Prompt', async () => {
    const wrapper = await create()
    await wrapper.get('form').trigger('submit')
    await wrapper.get('[aria-label="候选系统提示词"]').setValue('新候选')
    expect(wrapper.find('[aria-label="创建草案确认"]').exists()).toBe(false)
    await wrapper.setProps({ allowed: false })
    expect(
      (wrapper.get('[aria-label="候选系统提示词"]').element as HTMLTextAreaElement).value,
    ).toBe('')
    expect(api.createOnlineExperiment).not.toHaveBeenCalled()
  })
  it.each(['version', 'parent'])('提交前拒绝变化的 %s', async (changed) => {
    const wrapper = await create()
    await wrapper.get('form').trigger('submit')
    if (changed === 'version')
      vi.mocked(api.queryPublishedOnlineVersions).mockResolvedValue([
        {
          ...version,
          contentHash: 'changed',
        },
      ])
    else vi.mocked(api.queryPublishedOnlineVersions).mockRejectedValue(new Error('父资源已归档'))
    await button(wrapper, '确认创建草案').trigger('click')
    await flushPromises()
    expect(api.createOnlineExperiment).not.toHaveBeenCalled()
  })
  it('响应丢失重试保留请求 key；编辑后重新核对使用新 key', async () => {
    vi.mocked(api.createOnlineExperiment).mockRejectedValue(new Error('响应丢失'))
    const wrapper = await create()
    await wrapper.get('form').trigger('submit')
    await button(wrapper, '确认创建草案').trigger('click')
    await flushPromises()
    await button(wrapper, '确认创建草案').trigger('click')
    await flushPromises()
    expect(vi.mocked(api.createOnlineExperiment).mock.calls[1]![0].clientRequestKey).toBe(
      vi.mocked(api.createOnlineExperiment).mock.calls[0]![0].clientRequestKey,
    )
    await wrapper.get('[aria-label="候选系统提示词"]').setValue('新候选')
    await wrapper.get('form').trigger('submit')
    await button(wrapper, '确认创建草案').trigger('click')
    await flushPromises()
    expect(vi.mocked(api.createOnlineExperiment).mock.calls[2]![0].clientRequestKey).not.toBe(
      vi.mocked(api.createOnlineExperiment).mock.calls[0]![0].clientRequestKey,
    )
  })
})
