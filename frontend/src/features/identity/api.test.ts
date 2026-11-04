import { afterEach, describe, expect, it, vi } from 'vitest'
import { authPost, readCustomer, session } from './api'

afterEach(() => vi.unstubAllGlobals())
describe('customer session boundary', () => {
  it('fetches CSRF before mutation and uses a cookie session without persisting credentials', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'fresh-csrf' })))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetcher)
    await expect(authPost('/api/auth/logout', {})).resolves.toBeNull()
    expect(fetcher.mock.calls[0][0]).toBe('/api/security/csrf')
    expect(fetcher.mock.calls[1][1]).toMatchObject({ method: 'POST', credentials: 'same-origin', headers: { 'X-CSRF-TOKEN': 'fresh-csrf' } })
  })
  it('never sends a mutation when CSRF establishment fails', async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ token: 'invalid' })))
    vi.stubGlobal('fetch', fetcher)
    await expect(authPost('/api/auth/login', {})).rejects.toThrow('secure session')
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
  it('treats anonymous sessions differently from server failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 401 })))
    await expect(session()).resolves.toBeNull()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 503 })))
    await expect(session()).rejects.toThrow('503')
  })
  it('keeps only the safe profile contract, even if unexpected sensitive fields are returned', () => {
    const profile = readCustomer({ id: 'customer-id', name: 'Juma', phone: '+255712345678', email: null, contactVerified: false, passwordHash: 'sensitive', role: 'OWNER' })
    expect(profile).not.toHaveProperty('passwordHash')
    expect(profile).not.toHaveProperty('role')
    expect(() => readCustomer({ id: 'broken' })).toThrow('Invalid account response')
  })
})
