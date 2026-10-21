import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessageBox } from 'element-plus'
import OnlineOriginalReviewView from '@/views/OnlineOriginalReviewView.vue'
import { useWorkspaceStore } from '@/stores/workspace'
import { SPACE_PERMISSIONS as P } from '@/shared/constants/permissions'
import * as api from './api'
import type { OnlineReviewContext, OnlineQualityHistory, OnlineQualityHistoryItem } from './types'
vi.mock('./api')
const wrappers: VueWrapper[] = []
const hash = 'a'.repeat(64)
const original: OnlineReviewContext = {
  assignmentId: '2104902086192304129',
  knowledgeStatus: 'BLINDED',
  blindedEligible: true,
  goal: { taskId: '81', inputSnapshotHash: hash, instruction: '整理冻结业务目标' },
  originalContext: {
    assignmentId: '2104902086192304129',
    taskId: '81',
    executionId: '82',
    documentId: '4',
    baseVersion: 0,
    inputHash: hash,
    inputContent: '完整冻结输入',
    originalEvidenceId: '83',
    originalEvidenceHash: hash,
    originalText: '完整原始文本',
  },
}
const permissions = (values: string[], owner = false) =>
  useWorkspaceStore().setEffectivePermissions({
    spaceId: '7',
    platformSuperAdmin: false,
    role: owner ? { roleId: '1', roleKey: 'OWNER', displayName: '所有者' } : null,
    permissions: values,
  })
const button = (wrapper: VueWrapper, text: string) =>
  wrapper.findAll('button').find((value) => value.text() === text)!
async function create() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/spaces/:spaceId/evaluation/online-experiments/:experimentId/review',
        component: OnlineOriginalReviewView,
        props: { experimentId: '10' },
      },
    ],
  })
  await router.push('/spaces/7/evaluation/online-experiments/10/review')
  const wrapper = mount(
    { template: '<router-view />' },
    {
      global: { plugins: [router] },
    },
  )
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper
}
async function read(wrapper: VueWrapper, target = 'TEXT', chooseRating = true) {
  await wrapper.get('[aria-label="评审分配ID"]').setValue(original.assignmentId)
  await wrapper.get('[aria-label="原始评审目标"]').setValue(target)
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  if (chooseRating && wrapper.find('[aria-label="原始质量等级"]').exists()) {
    await wrapper.get('[aria-label="原始质量等级"]').setValue('0')
    await wrapper.get('[aria-label="质量标签"]').setValue('NEEDS_CHANGES')
  }
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue(
    'confirm' as Awaited<ReturnType<typeof ElMessageBox.confirm>>,
  )
  setActivePinia(createPinia())
  useWorkspaceStore().setCurrentSpace('7')
  permissions([P.SPACE_READ, P.EVALUATION_READ, P.EVALUATION_RUN, P.TASK_READ, P.DOCUMENT_READ])
  vi.mocked(api.searchOnlineReviewAssignments).mockResolvedValue({
    records: [{ assignmentId: original.assignmentId, documentId: '4' }],
    total: 1,
    pageNum: 1,
    pageSize: 10,
  })
  vi.mocked(api.getOnlineReviewContext).mockResolvedValue(original)
})
afterEach(() => wrappers.splice(0).forEach((value) => value.unmount()))
describe('隐藏分组原始评价', () => {
  it('候选仅显示本人历史记录状态，缺字段保持未知且不提前读取原始产物', async () => {
    vi.mocked(api.searchOnlineReviewAssignments).mockResolvedValue({
      records: [
        { assignmentId: '11', documentId: '4', documentTitle: '文档一', ownReviewRecorded: true },
        { assignmentId: '12', documentId: '5', ownReviewRecorded: false },
        { assignmentId: '13', documentId: '6' },
      ],
      total: 3,
      pageNum: 1,
      pageSize: 10,
    })
    const wrapper = await create()
    expect(wrapper.text()).toContain('本人有历史评审记录')
    expect(wrapper.text()).toContain('本人尚无评审记录')
    expect(wrapper.text()).toContain('本人记录状态未知')
    expect(api.getOnlineReviewContext).not.toHaveBeenCalled()
    expect(api.createOnlineQuality).not.toHaveBeenCalled()
  })
  it('安全预览只渲染 Markdown，不加载远程图片或执行模型 HTML', async () => {
    vi.mocked(api.getOnlineReviewContext).mockResolvedValue({
      ...original,
      originalContext: {
        ...original.originalContext,
        originalText:
          '# 标题\n<script>alert(1)</script>\n![图](https://invalid.example/image.png)\n[链接](javascript:alert(1))',
      },
    })
    const wrapper = await create()
    await read(wrapper, 'TEXT', false)
    await button(wrapper, '预览 Markdown').trigger('click')
    expect(wrapper.get('.original-markdown h1').text()).toBe('标题')
    expect(
      wrapper
        .find('.original-markdown script, .original-markdown img, .original-markdown a')
        .exists(),
    ).toBe(false)
    expect(wrapper.get('.original-markdown').text()).toContain('<script>')
  })
  it('取消离开时保留评分、目标和输入，不发出新的证据请求', async () => {
    const wrapper = await create()
    await read(wrapper)
    const before = vi.mocked(api.getOnlineReviewContext).mock.calls.length
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')
    await wrapper.get('[aria-label="原始评审目标"]').setValue('CHANGE_SET')
    await flushPromises()
    expect(wrapper.get('[aria-label="原始评审目标"]').element).toHaveProperty('value', 'TEXT')
    expect(wrapper.get('[aria-label="原始质量等级"]').element).toHaveProperty('value', '0')
    expect(api.getOnlineReviewContext).toHaveBeenCalledTimes(before)
    expect(api.createOnlineQuality).not.toHaveBeenCalled()
  })
  it('首评不预选零分或质量标签，未选择时不能核对提交', async () => {
    const wrapper = await create()
    await read(wrapper, 'TEXT', false)
    expect((wrapper.get('[aria-label="原始质量等级"]').element as HTMLSelectElement).value).toBe('')
    expect((wrapper.get('[aria-label="质量标签"]').element as HTMLSelectElement).value).toBe('')
    await wrapper.findAll('form')[1]!.trigger('submit')
    expect(wrapper.find('[aria-label="原始评价确认"]').exists()).toBe(false)
    expect(api.createOnlineQuality).not.toHaveBeenCalled()
  })
  it('响应丢失先核对本人反馈；相同评分不重复提交，明确修改后才追加当前链尾', async () => {
    vi.mocked(api.createOnlineQuality).mockRejectedValue(new Error('响应丢失'))
    const wrapper = await create()
    await read(wrapper)
    await wrapper.get('[aria-label="评价原因"]').setValue('首次评价理由')
    await wrapper.findAll('form')[1]!.trigger('submit')
    await button(wrapper, '确认评价').trigger('click')
    await flushPromises()
    await wrapper.findAll('form')[1]!.trigger('submit')
    expect(wrapper.find('[aria-label="原始评价确认"]').exists()).toBe(false)
    const attempted = vi.mocked(api.createOnlineQuality).mock.calls[0]![0]
    const own: OnlineQualityHistoryItem = {
      id: '9007199254740993',
      sourceHash: hash,
      supersedesFeedbackId: null,
      rubricScore: 0,
      label: attempted.label,
      blindness: 'NON_BLINDED',
      declaredBlindness: 'BLINDED',
      ownFeedback: true,
      comment: attempted.comment,
      reasonCodes: [],
      seriousSafetyEvent: false,
      safetyReason: null,
      createdAt: null,
    }
    const history: OnlineQualityHistory = {
      schemaVersion: 2,
      spaceId: '7',
      experimentId: '10',
      assignmentId: original.assignmentId,
      taskId: '81',
      executionId: '82',
      originalEvidenceType: 'ORIGINAL_TEXT',
      originalEvidenceId: '83',
      originalEvidenceHash: hash,
      currentTailFeedbackId: own.id,
      currentTailBlindness: 'NON_BLINDED',
      lastOwnFeedback: own,
      history: { records: [own], total: 1, pageNum: 1, pageSize: 10 },
    }
    vi.mocked(api.searchOnlineQualityHistory)
      .mockRejectedValueOnce(new Error('核对失败'))
      .mockResolvedValue(history)
    await button(wrapper, '核对反馈链').trigger('click')
    await flushPromises()
    await wrapper.findAll('form')[1]!.trigger('submit')
    expect(wrapper.text()).toContain('上次响应未知')
    await button(wrapper, '核对反馈链').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('已核对上次评分保存成功')
    await wrapper.findAll('form')[1]!.trigger('submit')
    expect(wrapper.text()).toContain('当前评分与原因已经保存')
    expect(api.createOnlineQuality).toHaveBeenCalledTimes(1)
    await button(wrapper, '以当前链尾追加更正').trigger('click')
    await wrapper.get('[aria-label="评价原因"]').setValue('复核后更正')
    await wrapper.get('[aria-label="原始质量等级"]').setValue('1')
    vi.mocked(api.createOnlineQuality).mockImplementation(async (payload) => ({
      id: '9007199254740994',
      spaceId: '7',
      taskId: '81',
      executionId: '82',
      onlineQuality: {
        ...payload.onlineQuality,
        declaredBlindness: payload.onlineQuality.blindness,
      },
    }))
    await wrapper.findAll('form')[1]!.trigger('submit')
    await button(wrapper, '确认评价').trigger('click')
    await flushPromises()
    expect(api.createOnlineQuality).toHaveBeenCalledTimes(2)
    expect(vi.mocked(api.createOnlineQuality).mock.calls[1]![0]).toMatchObject({
      onlineQuality: { rubricScore: 1, supersedesFeedbackId: own.id, blindness: 'NON_BLINDED' },
    })
    expect(api.getOnlineExperiment).not.toHaveBeenCalled()
    expect(api.searchOnlineAssignments).not.toHaveBeenCalled()
  })
  it('直接深链仅加载隐藏接口，不补普通实验元数据', async () => {
    const wrapper = await create()
    await read(wrapper)
    expect(api.searchOnlineReviewAssignments).toHaveBeenCalledWith('10', 1, expect.any(AbortSignal))
    expect(api.getOnlineReviewContext).toHaveBeenCalledWith(
      '10',
      original.assignmentId,
      'TEXT',
      expect.any(AbortSignal),
    )
    expect(api.getOnlineExperiment).not.toHaveBeenCalled()
    expect(api.searchOnlineAssignments).not.toHaveBeenCalled()
    expect(api.getOnlinePreflight).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('完整冻结输入')
    expect(wrapper.text()).toContain('整理冻结业务目标')
    expect(wrapper.text()).toContain('完整原始文本')
  })
  it('完整变更集合保留结构无效项，整体评分不挑选成功产物', async () => {
    vi.mocked(api.getOnlineReviewContext).mockResolvedValue({
      ...original,
      originalContext: {
        ...original.originalContext,
        originalSetId: original.assignmentId,
        originalSetHash: hash,
        originals: [
          {
            evidenceId: '90',
            evidenceType: 'CHANGE_REQUEST',
            evidenceHash: hash,
            baseVersion: 0,
            outputVersion: null,
            structureValid: false,
            originalChanges: '<script>原始无效提案</script>',
            originalContent: null,
          },
          {
            evidenceId: '91',
            evidenceType: 'DRAFT_DOCUMENT',
            evidenceHash: hash,
            baseVersion: 0,
            outputVersion: 1,
            structureValid: true,
            originalChanges: null,
            originalContent: '完整草稿正文',
          },
        ],
      },
    })
    const wrapper = await create()
    await read(wrapper, 'CHANGE_SET')
    expect(wrapper.findAll('article')).toHaveLength(2)
    expect(wrapper.text()).toContain('原始无效提案')
    expect(wrapper.find('script').exists()).toBe(false)
    await wrapper.findAll('form')[1]!.trigger('submit')
    vi.mocked(api.createOnlineQuality).mockRejectedValue(new Error('断网'))
    await button(wrapper, '确认评价').trigger('click')
    await flushPromises()
    expect(vi.mocked(api.createOnlineQuality).mock.calls[0]![0].onlineQuality).toMatchObject({
      originalEvidenceType: 'ORIGINAL_CHANGE_SET',
      originalEvidenceId: original.assignmentId,
      originalEvidenceHash: hash,
    })
  })
  it('0 分有效，保存实际知情状态和新反馈 ID，可明确追加更正', async () => {
    vi.mocked(api.createOnlineQuality).mockImplementation(async (payload) => ({
      id: '9007199254740993',
      spaceId: '7',
      taskId: '81',
      executionId: '82',
      onlineQuality: {
        ...payload.onlineQuality,
        blindness: 'NON_BLINDED',
        declaredBlindness: payload.onlineQuality.blindness,
      },
    }))
    const wrapper = await create()
    await read(wrapper)
    await wrapper.findAll('form')[1]!.trigger('submit')
    await button(wrapper, '确认评价').trigger('click')
    await flushPromises()
    expect(vi.mocked(api.createOnlineQuality).mock.calls[0]![0]).toMatchObject({
      score: 0,
      onlineQuality: { rubricScore: 0, blindness: 'BLINDED', supersedesFeedbackId: null },
    })
    expect(wrapper.text()).toContain('有效知情状态 NON_BLINDED')
    await wrapper.get('[aria-label="评价原因"]').setValue('更正理由')
    await wrapper.get('[aria-label="原始质量等级"]').setValue('1')
    await wrapper.findAll('form')[1]!.trigger('submit')
    await button(wrapper, '确认评价').trigger('click')
    await flushPromises()
    expect(vi.mocked(api.createOnlineQuality).mock.calls[1]![0]).toMatchObject({
      score: 0.25,
      onlineQuality: { supersedesFeedbackId: '9007199254740993' },
    })
  })
  it.each(['UNKNOWN', 'NON_BLINDED'] as const)('%s 不自动声明盲态', async (status) => {
    vi.mocked(api.getOnlineReviewContext).mockResolvedValue({
      ...original,
      knowledgeStatus: status,
      blindedEligible: false,
    })
    const wrapper = await create()
    await read(wrapper)
    expect(wrapper.find('option[value="BLINDED"]').exists()).toBe(false)
    expect((wrapper.get('[aria-label="声明知情状态"]').element as HTMLSelectElement).value).toBe(
      status,
    )
  })
  it('缺证据不填零分，错身份不展示正文', async () => {
    vi.mocked(api.getOnlineReviewContext).mockResolvedValue({
      ...original,
      originalContext: { ...original.originalContext, originalEvidenceHash: undefined },
    })
    const wrapper = await create()
    await read(wrapper)
    expect(wrapper.text()).toContain('不能评分')
    expect(wrapper.find('[aria-label="原始质量等级"]').exists()).toBe(false)
    vi.mocked(api.getOnlineReviewContext).mockResolvedValue({ ...original, assignmentId: '9' })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).not.toContain('完整原始文本')
    expect(api.createOnlineQuality).not.toHaveBeenCalled()
  })
  it('权限撤销时立即清除原文，候选/读/写请求中止', async () => {
    const wrapper = await create()
    await read(wrapper)
    const signal = vi.mocked(api.getOnlineReviewContext).mock.calls[0]![3]!
    permissions([])
    await flushPromises()
    expect(signal.aborted).toBe(true)
    expect(wrapper.text()).not.toContain('完整原始文本')
    expect(wrapper.text()).toContain('需要当前空间')
    expect(api.createOnlineQuality).not.toHaveBeenCalled()
  })
  it('修改原目标或评分使确认失效；非 OWNER 无安全确认入口', async () => {
    const wrapper = await create()
    await read(wrapper)
    await wrapper.findAll('form')[1]!.trigger('submit')
    await wrapper.get('[aria-label="评价原因"]').setValue('修改意见')
    expect(wrapper.find('[aria-label="原始评价确认"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('确认严重安全事件')
    await wrapper.get('[aria-label="原始评审目标"]').setValue('CHANGE_SET')
    expect(wrapper.text()).not.toContain('完整原始文本')
  })
})
