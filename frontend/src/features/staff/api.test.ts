import { afterEach, expect, it, vi } from 'vitest'
import { staffSession, staffWrite, StaffError } from './api'
afterEach(() => vi.unstubAllGlobals())
it('treats a customer session as signed out of staff tools', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 403 })))
  expect(await staffSession()).toBeNull()
})
it('requires a fresh valid CSRF token and rejects unsupported operations before transport', async () => {
  const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ headerName: 'unexpected', token: 'token' })))
  vi.stubGlobal('fetch', fetcher)
  await expect(staffWrite('/api/staff/auth/login', {})).rejects.toThrow('secure session')
  expect(fetcher).toHaveBeenCalledTimes(1)
  await expect(staffWrite('/api/staff/accounts/../../products', {})).rejects.toThrow('Unsupported')
  expect(fetcher).toHaveBeenCalledTimes(1)
})
it('sends staff mutations only to same-origin endpoints with CSRF and reports server conflicts', async () => {
  const fetcher = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'session-token' })))
    .mockResolvedValueOnce(new Response(JSON.stringify({ title: 'This staff account changed.' }), { status: 409 }))
  vi.stubGlobal('fetch', fetcher)
  await expect(staffWrite('/api/staff/accounts', {})).rejects.toBeInstanceOf(StaffError)
  expect(fetcher.mock.calls[1][1]).toMatchObject({ credentials: 'same-origin', method: 'POST', headers: { 'X-CSRF-TOKEN': 'session-token' } })
})
