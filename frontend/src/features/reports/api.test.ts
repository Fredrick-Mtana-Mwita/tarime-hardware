import { describe, expect, it } from 'vitest'
import { presetRange } from './api'

describe('presetRange', () => {
  // 21:30 UTC on 31 Jan is already 1 Feb in Tanzania (UTC+3).
  const now = new Date('2026-01-31T21:30:00Z')
  it('uses shop time', () => { expect(presetRange('today', now)).toEqual({ from: '2026-02-01', to: '2026-02-01' }) })
  it('counts both ends', () => { expect(presetRange('7d', now)).toEqual({ from: '2026-01-26', to: '2026-02-01' }) })
  it('handles month edges', () => {
    expect(presetRange('month', now)).toEqual({ from: '2026-02-01', to: '2026-02-01' })
    expect(presetRange('last-month', now)).toEqual({ from: '2026-01-01', to: '2026-01-31' })
    expect(presetRange('last-month', new Date('2026-03-10T08:00:00Z'))).toEqual({ from: '2026-02-01', to: '2026-02-28' })
  })
})
