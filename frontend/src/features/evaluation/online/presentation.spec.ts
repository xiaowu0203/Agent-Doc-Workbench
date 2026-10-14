import { describe, expect, it } from 'vitest'
import {
  onlineActionAvailable,
  onlineFact,
  onlineStateVersion,
  onlineStatus,
  preflightCurrent,
  validOnlineId,
} from './presentation'
import { experiment, proof } from './fixtures.spec-support'

describe('线上事实与门禁', () => {
  it('保留零、false 和长整数；缺失显示未知', () => {
    expect([0, false, '9007199254740993', null, undefined, ''].map(onlineFact)).toEqual([
      '0',
      'false',
      '9007199254740993',
      '未知',
      '未知',
      '未知',
    ])
    expect(onlineStateVersion('9007199254740993')).toBe('9007199254740993')
    expect(onlineStateVersion(0)).toBe('0')
    expect(() => onlineStateVersion(Number('9007199254740993'))).toThrow()
  })
  it.each(['01', '-1', '1e3', '9223372036854775808', '', '0'])('拒绝非规范或越界身份 %s', (value) =>
    expect(validOnlineId(value)).toBe(false),
  )
  it('前向未知状态和旧 schema 只读', () => {
    expect(onlineStatus('FUTURE')).toContain('FUTURE')
    for (const status of ['FUTURE', 'CREATED'])
      expect(
        onlineActionAvailable({ ...experiment, status, manifestSchemaVersion: 1 }, 'stop', proof),
      ).toBe(false)
  })
  it('部署未就绪、过期、身份变化和预检错误均阻止启动', () => {
    const now = Date.parse(proof.checkedAt)
    expect(preflightCurrent(experiment, proof, now)).toBe(true)
    for (const invalid of [
      null,
      { ...proof, startable: false },
      { ...proof, experimentId: '9' },
      { ...proof, manifestHash: 'changed' },
      { ...proof, expiresAt: proof.checkedAt },
      {
        ...proof,
        issues: [{ code: 'BLOCKED', severity: 'ERROR', documentId: null, ruleKey: null }],
      },
    ])
      expect(onlineActionAvailable(experiment, 'start', invalid, now)).toBe(false)
  })
  it('PAUSED 可以结束，STOPPING 只允许紧急取消，不伪装已释放', () => {
    expect(onlineActionAvailable({ ...experiment, status: 'PAUSED' }, 'stop', null)).toBe(true)
    expect(onlineActionAvailable({ ...experiment, status: 'STOPPING' }, 'stop', null)).toBe(false)
    expect(
      onlineActionAvailable({ ...experiment, status: 'STOPPING' }, 'emergency-stop', null),
    ).toBe(true)
  })
})
