import { describe, it, expect, vi } from 'vitest'
import { getSystemInfo } from './api'
import { ApiError } from '../../shared/api/http'

describe('system API boundary', () => {
  it('retains backend budget policy without replacing it with local defaults', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      application: 'Tarime Hardware', stage: 'foundation', zeroBudget: true, externalMessagingEnabled: false,
    }))))
    expect(await getSystemInfo()).toMatchObject({ zeroBudget: true, externalMessagingEnabled: false })
  })
  it('rejects malformed API payloads instead of displaying a connected state', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 'OK' }))))
    await expect(getSystemInfo()).rejects.toThrow('Invalid system response')
  })
  it('preserves HTTP failure status for callers', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 503 })))
    await expect(getSystemInfo()).rejects.toEqual(new ApiError(503))
  })
})
