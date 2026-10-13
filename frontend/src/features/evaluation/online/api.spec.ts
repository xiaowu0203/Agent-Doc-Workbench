import { beforeEach, describe, expect, it, vi } from 'vitest'
import { request } from '@/api/client'
import {
  changeOnlineState,
  getOnlineReviewContext,
  searchOnlineAssignments,
  searchOnlineExperiments,
  searchOnlineReviewAssignments,
  getOnlineReport,
  recalculateOnlineReport,
  decideOnlineExperiment,
  getOnlinePromptComparison,
  queryPublishedOnlineVersions,
  searchOnlineQualityHistory,
} from './api'
vi.mock('@/api/client', () => ({ request: vi.fn() }))
beforeEach(() => vi.resetAllMocks())
describe('线上协议适配', () => {
  it('冻结Prompt、发布批查询和隐藏更正链使用独立接口与取消信号', async () => {
    const signal = new AbortController().signal,
      id = '9007199254740993'
    await getOnlinePromptComparison(id, signal)
    expect(request).toHaveBeenLastCalledWith({
      method: 'GET',
      url: `/evaluation/online-experiments/${id}/prompt-comparison`,
      signal,
    })
    await queryPublishedOnlineVersions('7', [id, '82'], signal)
    expect(request).toHaveBeenLastCalledWith({
      method: 'POST',
      url: '/evaluation/evaluator-versions/published-query',
      data: { spaceId: '7', versionIds: [id, '82'] },
      signal,
    })
    await searchOnlineQualityHistory(id, '81', 'CHANGE_SET', 2, signal)
    expect(request).toHaveBeenLastCalledWith({
      method: 'POST',
      url: `/evaluation/online-experiments/${id}/assignments/81/quality-feedback-history/search`,
      data: { target: 'CHANGE_SET', pageNum: 2, pageSize: 10 },
      signal,
    })
  })
  it('线上报告与离线报告分离，完整核验和概要路径明确', async () => {
    await getOnlineReport('10', 2)
    expect(request).toHaveBeenLastCalledWith({
      method: 'GET',
      url: '/evaluation/online-experiments/10/reports/2/summary',
      signal: undefined,
    })
    await getOnlineReport('10', 2, true)
    expect(request).toHaveBeenLastCalledWith({
      method: 'GET',
      url: '/evaluation/online-experiments/10/reports/2',
      signal: undefined,
    })
    const data = {
      clientRequestKey: 'frozen-1',
      assignmentCutoffSequence: '9007199254740993',
      observationCutoffAt: '2026-10-12T12:00:00.000',
    }
    await recalculateOnlineReport('10', data)
    expect(request).toHaveBeenLastCalledWith({
      method: 'POST',
      url: '/evaluation/online-experiments/10/reports/recalculate',
      data,
      signal: undefined,
    })
    const decision = {
      clientRequestKey: 'decision-1',
      reportRevision: 2,
      reportHash: 'frozen-hash',
      decision: 'INSUFFICIENT_EVIDENCE' as const,
      reason: '文档不足',
    }
    await decideOnlineExperiment('10', decision)
    expect(request).toHaveBeenLastCalledWith({
      method: 'POST',
      url: '/evaluation/online-experiments/10/decision',
      data: decision,
      signal: undefined,
    })
  })
  it('搜索与分配分页保留长文本身份和取消信号', async () => {
    const signal = new AbortController().signal,
      id = '9007199254740993'
    await searchOnlineExperiments({ spaceId: id }, signal)
    expect(request).toHaveBeenLastCalledWith({
      method: 'POST',
      url: '/evaluation/online-experiments/search',
      data: { spaceId: id, pageNum: 1, pageSize: 10 },
      signal,
    })
    await searchOnlineAssignments(id, { taskId: id }, signal)
    expect(request).toHaveBeenLastCalledWith({
      method: 'POST',
      url: `/evaluation/online-experiments/${id}/assignments/search`,
      data: { taskId: id, pageNum: 1, pageSize: 10 },
      signal,
    })
  })
  it('隐藏评审仅携带页码和目标，不传组或模型配置', async () => {
    await searchOnlineReviewAssignments('10', 2)
    expect(request).toHaveBeenLastCalledWith({
      method: 'POST',
      url: '/evaluation/online-experiments/10/review-assignments/search',
      data: { pageNum: 2, pageSize: 10 },
      signal: undefined,
    })
    await getOnlineReviewContext('10', '11', 'CHANGE_SET')
    expect(request).toHaveBeenLastCalledWith({
      method: 'GET',
      url: '/evaluation/online-experiments/10/assignments/11/blind-review-context',
      params: { target: 'CHANGE_SET' },
      signal: undefined,
    })
  })
  it('生命周期保持冻结版本与原幂等 key', async () => {
    const data = {
      clientRequestKey: 'request-1',
      expectedStateVersion: '9007199254740993',
      manifestHash: 'hash',
      reason: '结束',
    }
    await changeOnlineState('10', 'stop', data)
    expect(request).toHaveBeenLastCalledWith({
      method: 'POST',
      url: '/evaluation/online-experiments/10/stop',
      data,
      signal: undefined,
    })
  })
})
