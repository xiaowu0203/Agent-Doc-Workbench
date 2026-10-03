import { beforeEach, describe, expect, it, vi } from 'vitest'

import { request } from '@/api/client'
import * as api from './evaluation-api'
import type { DatasetCaseBinding, ExperimentReport, TestCaseEvaluatorBinding } from '../types'

vi.mock('@/api/client', () => ({ request: vi.fn() }))

beforeEach(() => {
  vi.mocked(request).mockReset()
})

describe('evaluation HTTP contracts', () => {
  it('updates draft configuration via version resources without changing frozen source fields', async () => {
    const signal = new AbortController().signal
    const evaluator = { configSchemaVersion: 1, configJson: '{}', resultSchemaVersion: 1 }
    const testCase = {
      expectedSchemaVersion: 1,
      expectedJson: '{}',
      sourceType: 'LIVE',
      sanitizationNote: null,
    }
    await api.updateEvaluatorVersion('21', evaluator, signal)
    await api.updateTestCaseVersion('22', testCase, signal)
    expect(vi.mocked(request).mock.calls.map(([config]) => config)).toEqual([
      { method: 'PUT', url: '/evaluation/evaluator-versions/21', data: evaluator, signal },
      { method: 'PUT', url: '/evaluation/test-case-versions/22', data: testCase, signal },
    ])
  })
  it('uses space-scoped POST searches and explicit default pagination', async () => {
    const signal = new AbortController().signal
    const payload = {
      spaceId: '2104855879319314433',
      status: 'PAUSED' as const,
      datasetVersionId: '2104902086192304129',
      createdFrom: '2026-10-01T00:00:00',
    }
    await api.searchEvaluationRuns(payload, signal)
    await api.searchExperiments({ ...payload, pageNum: 2, pageSize: 100 }, signal)
    expect(vi.mocked(request).mock.calls.map(([config]) => config)).toEqual([
      {
        method: 'POST',
        url: '/evaluation/runs/search',
        data: { ...payload, pageNum: 1, pageSize: 10 },
        signal,
      },
      {
        method: 'POST',
        url: '/evaluation/experiments/search',
        data: { ...payload, pageNum: 2, pageSize: 100 },
        signal,
      },
    ])
  })

  it('preserves false filters and cancellation signals in catalog searches', async () => {
    const signal = new AbortController().signal
    await api.searchTestCases({ spaceId: '9', archived: false, keyword: '' }, signal)
    await api.searchEvaluatorVersions({ spaceId: '9', parentId: '22', status: 'PUBLISHED' }, signal)
    expect(vi.mocked(request).mock.calls.map(([config]) => config)).toEqual([
      {
        method: 'POST',
        url: '/evaluation/test-cases/search',
        data: { spaceId: '9', archived: false, keyword: '', pageNum: 1, pageSize: 10 },
        signal,
      },
      {
        method: 'POST',
        url: '/evaluation/evaluator-versions/search',
        data: { spaceId: '9', parentId: '22', status: 'PUBLISHED', pageNum: 1, pageSize: 10 },
        signal,
      },
    ])
  })

  it('reads binding versions and expected JSON without remapping values', async () => {
    const cases: DatasetCaseBinding[] = [
      {
        id: '1',
        testCaseVersionId: '2',
        testCaseId: '3',
        testCaseName: null,
        versionNo: 1,
        status: 'PUBLISHED',
        sortOrder: 0,
        enabled: false,
      },
    ]
    const evaluators: TestCaseEvaluatorBinding[] = [
      {
        id: '4',
        evaluatorVersionId: '5',
        evaluatorId: '6',
        evaluatorName: null,
        versionNo: 2,
        status: 'PUBLISHED',
        evaluatorKey: 'rule',
        sortOrder: 0,
        expectedJson: '{"threshold":0,"required":false}',
      },
    ]
    vi.mocked(request).mockResolvedValueOnce(cases).mockResolvedValueOnce(evaluators)
    expect(await api.getDatasetCases('8')).toBe(cases)
    expect(await api.getTestCaseEvaluators('9')).toBe(evaluators)
    expect(vi.mocked(request).mock.calls.map(([config]) => [config.method, config.url])).toEqual([
      ['GET', '/evaluation/dataset-versions/8/cases'],
      ['GET', '/evaluation/test-case-versions/9/evaluators'],
    ])
  })

  it('sends full binding replacements, version publication and archival as PUT', async () => {
    const cases = { cases: [{ testCaseVersionId: '2', sortOrder: 0, enabled: false }] }
    const evaluators = {
      evaluators: [{ evaluatorVersionId: '5', sortOrder: 0, expectedJson: '{}' }],
    }
    await api.replaceDatasetCases('8', cases)
    await api.replaceTestCaseEvaluators('9', evaluators)
    await api.publishTestCaseVersion('9')
    await api.archiveEvaluatorVersion('5')
    expect(vi.mocked(request).mock.calls.map(([config]) => config)).toEqual([
      {
        method: 'PUT',
        url: '/evaluation/dataset-versions/8/cases',
        data: cases,
        signal: undefined,
      },
      {
        method: 'PUT',
        url: '/evaluation/test-case-versions/9/evaluators',
        data: evaluators,
        signal: undefined,
      },
      { method: 'PUT', url: '/evaluation/test-case-versions/9/publish', signal: undefined },
      { method: 'PUT', url: '/evaluation/evaluator-versions/5/archive', signal: undefined },
    ])
  })

  it('keeps replay and evaluator retries on their distinct business identities', async () => {
    await api.searchCaseAttempts('10', { pageNum: 2, pageSize: 10 })
    await api.retryCaseReplay('10', { workerCapabilityTtlSeconds: 3600 })
    await api.retryCaseEvaluation('11', {
      evaluatorVersionIds: [],
      workerCapabilityTtlSeconds: 3600,
    })
    await api.resumeEvaluationRun('12', { workerCapabilityTtlSeconds: 3600 })
    await api.cancelEvaluationRun('12')
    expect(vi.mocked(request).mock.calls.map(([config]) => config)).toEqual([
      {
        method: 'POST',
        url: '/evaluation/case-runs/10/attempts/search',
        data: { pageNum: 2, pageSize: 10 },
        signal: undefined,
      },
      {
        method: 'POST',
        url: '/evaluation/case-runs/10/retry-replay',
        data: { workerCapabilityTtlSeconds: 3600 },
        signal: undefined,
      },
      {
        method: 'POST',
        url: '/evaluation/case-attempts/11/retry-evaluation',
        data: { evaluatorVersionIds: [], workerCapabilityTtlSeconds: 3600 },
        signal: undefined,
      },
      {
        method: 'PUT',
        url: '/evaluation/runs/12/resume',
        data: { workerCapabilityTtlSeconds: 3600 },
        signal: undefined,
      },
      { method: 'PUT', url: '/evaluation/runs/12/cancel', signal: undefined },
    ])
  })

  it('keeps experiment idempotency, budget confirmation and report decision identities', async () => {
    const create = {
      spaceId: '9',
      datasetVersionId: '20',
      clientRequestKey: 'create-1',
      candidateVariants: [{ variantKey: 'candidate', agentPrompt: '候选提示词' }],
    }
    await api.createExperiment(create)
    await api.getExperimentPreflight('21')
    await api.startExperiment('21', {
      authorizedTokenBudget: 8192,
      workerCapabilityTtlSeconds: 3600,
    })
    await api.recalculateExperimentReport('21', { clientRequestKey: 'report-1' })
    await api.submitExperimentDecision('21', {
      reportRevision: 2,
      decision: 'INSUFFICIENT_EVIDENCE',
      reason: '有效样本不足',
    })
    expect(vi.mocked(request).mock.calls.map(([config]) => config)).toEqual([
      { method: 'POST', url: '/evaluation/experiments', data: create, signal: undefined },
      { method: 'GET', url: '/evaluation/experiments/21/preflight', signal: undefined },
      {
        method: 'POST',
        url: '/evaluation/experiments/21/start',
        data: { authorizedTokenBudget: 8192, workerCapabilityTtlSeconds: 3600 },
        signal: undefined,
      },
      {
        method: 'POST',
        url: '/evaluation/experiments/21/reports/recalculate',
        data: { clientRequestKey: 'report-1' },
        signal: undefined,
      },
      {
        method: 'POST',
        url: '/evaluation/experiments/21/decision',
        data: { reportRevision: 2, decision: 'INSUFFICIENT_EVIDENCE', reason: '有效样本不足' },
        signal: undefined,
      },
    ])
  })

  it('preserves incompatible historical reports without inventing content', async () => {
    const report: ExperimentReport = {
      experimentId: '21',
      revision: 1,
      schemaVersion: null,
      manifestHash: null,
      calculationInputHash: null,
      selectedRecordIds: null,
      report: null,
      contentHash: 'hash',
      generatedBy: null,
      generatedAt: null,
      compatible: false,
      compatibilityCode: 'SCHEMA_MISSING',
    }
    vi.mocked(request).mockResolvedValue(report)
    expect(await api.getExperimentReport('21', 1)).toBe(report)
    expect(request).toHaveBeenCalledWith({
      method: 'GET',
      url: '/evaluation/experiments/21/reports/1',
      signal: undefined,
    })
  })

  it('propagates server errors and aborted requests instead of returning empty success', async () => {
    const error = new Error('forbidden')
    vi.mocked(request).mockRejectedValue(error)
    const controller = new AbortController()
    controller.abort()
    await expect(api.getEvaluationRun('99', controller.signal)).rejects.toBe(error)
    expect(request).toHaveBeenCalledWith({
      method: 'GET',
      url: '/evaluation/runs/99',
      signal: controller.signal,
    })
  })
})
