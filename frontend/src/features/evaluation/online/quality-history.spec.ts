import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ElPagination } from 'element-plus'
import OnlineQualityHistoryPanel from './OnlineQualityHistoryPanel.vue'
import * as api from './api'
import type { OnlineQualityHistory, OnlineQualityHistoryItem, OnlineReviewContext } from './types'
vi.mock('./api')
const wrappers: VueWrapper[] = [],
  hash = 'a'.repeat(64)
const context: OnlineReviewContext = {
  assignmentId: '51',
  knowledgeStatus: 'BLINDED',
  blindedEligible: true,
  goal: { taskId: '81', inputSnapshotHash: hash, instruction: '目标' },
  originalContext: {
    assignmentId: '51',
    taskId: '81',
    executionId: '82',
    documentId: '4',
    baseVersion: 0,
    inputHash: hash,
    inputContent: '冻结输入',
    originalEvidenceId: '83',
    originalEvidenceHash: hash,
    originalText: '原始正文',
  },
}
const rows: OnlineQualityHistoryItem[] = Array.from({ length: 11 }, (_, index) => ({
  id: String(100 - index),
  sourceHash: hash,
  supersedesFeedbackId: index === 0 ? null : String(101 - index),
  rubricScore: index === 10 ? 0 : 4,
  label: 'NEEDS_CHANGES',
  blindness: 'NON_BLINDED',
  declaredBlindness: 'UNKNOWN',
  ownFeedback: index === 10,
  comment: index === 10 ? '自己的更正' : null,
  reasonCodes: [],
  seriousSafetyEvent: false,
  safetyReason: null,
  createdAt: null,
}))
function history(pageNum = 1): OnlineQualityHistory {
  return {
    schemaVersion: 2,
    spaceId: '7',
    experimentId: '10',
    assignmentId: '51',
    taskId: '81',
    executionId: '82',
    originalEvidenceType: 'ORIGINAL_TEXT',
    originalEvidenceId: '83',
    originalEvidenceHash: hash,
    currentTailFeedbackId: '90',
    currentTailBlindness: 'NON_BLINDED',
    lastOwnFeedback: rows[10]!,
    history: {
      records: rows.slice((pageNum - 1) * 10, pageNum * 10),
      total: 11,
      pageNum,
      pageSize: 10,
    },
  }
}
function create() {
  const wrapper = mount(OnlineQualityHistoryPanel, {
    props: { context, target: 'TEXT', spaceId: '7', experimentId: '10', busy: false },
  })
  wrappers.push(wrapper)
  return wrapper
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.searchOnlineQualityHistory).mockImplementation(
    async (_id, _assignment, _target, pageNum) => history(pageNum),
  )
})
afterEach(() => wrappers.splice(0).forEach((item) => item.unmount()))
describe('隐藏反馈更正链', () => {
  it('自动核对后分页显示更正顺序，以真实链尾追加，0分有效', async () => {
    const wrapper = create()
    expect(api.searchOnlineQualityHistory).toHaveBeenCalledTimes(1)
    await flushPromises()
    expect(wrapper.findAll('ol > li')).toHaveLength(10)
    expect(wrapper.text()).toContain('当次链尾 #90')
    expect(wrapper.text()).toContain('自己的更正')
    wrapper.findComponent(ElPagination).vm.$emit('current-change', 2)
    await flushPromises()
    expect(api.searchOnlineQualityHistory).toHaveBeenLastCalledWith(
      '10',
      '51',
      'TEXT',
      2,
      expect.any(AbortSignal),
    )
    expect(wrapper.findAll('ol > li')).toHaveLength(1)
    expect(wrapper.find('ol > li').text()).toContain('0 / 4')
    await wrapper
      .findAll('button')
      .find((item) => item.text() === '以当前链尾追加更正')!
      .trigger('click')
    expect(wrapper.emitted('correct')).toEqual([['90']])
  })
  it.each(['private', 'source', 'branch', 'tail'])(
    '不可核验的 %s 响应保持未知，未展示历史',
    async (kind) => {
      const value = history()
      if (kind === 'private')
        value.history.records = [
          { ...rows[0]!, comment: 'Candidate泄露内容' },
          ...rows.slice(1, 10),
        ]
      if (kind === 'source') value.originalEvidenceHash = 'b'.repeat(64)
      if (kind === 'branch')
        value.history.records = [
          rows[0]!,
          { ...rows[1]!, supersedesFeedbackId: '99' },
          ...rows.slice(2, 10),
        ]
      if (kind === 'tail') {
        value.history = { records: rows.slice(0, 10), total: 10, pageNum: 1, pageSize: 10 }
        value.currentTailFeedbackId = '90'
      }
      vi.mocked(api.searchOnlineQualityHistory).mockResolvedValue(value)
      const wrapper = create()
      await wrapper.get('button').trigger('click')
      await flushPromises()
      expect(wrapper.text()).toContain('反馈链未知')
      expect(wrapper.text()).not.toContain('Candidate泄露内容')
      expect(wrapper.find('li').exists()).toBe(false)
      expect(wrapper.emitted('verified')).toBeUndefined()
    },
  )
  it('身份变化取消核对，迟到的历史不能污染新目标', async () => {
    let resolve!: (value: OnlineQualityHistory) => void
    vi.mocked(api.searchOnlineQualityHistory).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    const wrapper = create()
    const signal = vi.mocked(api.searchOnlineQualityHistory).mock.calls[0]![4]!
    const previousResolve = resolve
    await wrapper.setProps({ experimentId: '11' })
    previousResolve(history())
    await flushPromises()
    expect(signal.aborted).toBe(true)
    expect(wrapper.emitted('verified')).toBeUndefined()
    expect(wrapper.find('li').exists()).toBe(false)
  })
})
