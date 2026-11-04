import { isClientPreview, previewMessage } from '../../shared/preview'
import { ApiError, getJson } from '../../shared/api/http'

export interface Customer { id: string; name: string; phone: string; email: string | null; contactVerified: boolean }
export interface Registration { name: string; phone: string; email: string; password: string }
export function readCustomer(value: unknown): Customer {
  if (!value || typeof value !== 'object' || !('id' in value) || typeof value.id !== 'string'
    || !('name' in value) || typeof value.name !== 'string' || !('phone' in value) || typeof value.phone !== 'string'
    || !('contactVerified' in value) || typeof value.contactVerified !== 'boolean'
    || !('email' in value) || (value.email !== null && typeof value.email !== 'string')) throw new Error('Invalid account response')
  return { id: value.id, name: value.name, phone: value.phone, email: value.email, contactVerified: value.contactVerified }
}

export async function session(signal?: AbortSignal): Promise<Customer | null> {
  try { return readCustomer(await getJson('/api/auth/me', signal)) }
  catch (error) { if (error instanceof ApiError && [401, 403].includes(error.status)) return null; throw error }
}

// Fetch a fresh session CSRF token for each explicit mutation. Never store credentials/tokens in localStorage.
export async function authPost(path: string, body: unknown): Promise<unknown> {
  if (!['/api/auth/register', '/api/auth/login', '/api/auth/logout'].includes(path)) throw new Error('Unsupported account operation')
  if (isClientPreview) throw new ApiError(405)
  const csrf = await getJson('/api/security/csrf')
  if (!csrf || typeof csrf !== 'object' || !('headerName' in csrf) || csrf.headerName !== 'X-CSRF-TOKEN'
    || !('token' in csrf) || typeof csrf.token !== 'string') throw new Error('Unable to establish a secure session')
  const response = await fetch(path, { method: 'POST', credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', 'X-CSRF-TOKEN': csrf.token }, body: JSON.stringify(body) })
  if (!response.ok) throw new ApiError(response.status)
  return response.status === 204 ? null : response.json()
}

export function authError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 405) return previewMessage
    if (error.status === 401) return 'Phone number or password is incorrect.'
    if (error.status === 400) return 'Check your name, phone number and optional email. Use a password of 12 to 128 characters.'
    if (error.status === 409) return 'Unable to create an account with these details. If you already registered, sign in.'
    if (error.status === 429) return 'Too many attempts. Please wait up to 15 minutes before trying again.'
    if (error.status === 403) return 'Your secure session has expired. Please try again.'
  }
  return 'We could not reach your account. Please check your connection and try again.'
}
