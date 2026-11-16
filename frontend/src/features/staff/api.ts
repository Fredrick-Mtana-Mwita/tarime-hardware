import { isClientPreview, previewMessage } from '../../shared/preview'
import { ApiError, getJson } from '../../shared/api/http'
export type StaffRole = 'OWNER' | 'SALES' | 'INVENTORY' | 'DELIVERY'
export interface Staff { id: string; username: string; name: string; role: StaffRole; passwordChangeRequired: boolean }
export interface StaffAccount { profile: Staff; active: boolean; revision: number }
export class StaffError extends ApiError { constructor(status: number, public detail: string) { super(status) } }
export async function staffSession(signal?: AbortSignal): Promise<Staff | null> {
  try { return await getJson('/api/staff/me', signal) as Staff }
  catch (error) { if (error instanceof ApiError && [401, 403].includes(error.status)) return null; throw error }
}
export async function staffWrite<T>(path: string, body: unknown, method: 'POST' | 'PUT' = 'POST'): Promise<T> {
  if (!/^\/api\/staff\/(auth\/(login|logout|password)|inventory\/receipts|fulfilment\/[0-9a-f-]+|accounts(\/[0-9a-f-]+)?|catalogue\/(products(\/[0-9a-f-]{36}(\/(publish|unpublish|archive|restore|photos|variants(\/[0-9a-f-]{36}(\/price)?)?))?)?|price-changes\/[0-9a-f-]{36}\/(approve|reject)|samples\/archive)|orders\/[0-9a-f-]+\/(confirm|reject|cancel)|finance\/(orders\/[0-9a-f-]{36}\/(payments|adjustments)|adjustments\/[0-9a-f-]{36}\/(approve|reject))|notifications\/[0-9a-f-]+\/read)$/.test(path)) throw new Error('Unsupported staff operation')
  if (isClientPreview) throw new StaffError(405, previewMessage)
  const csrf = await getJson('/api/security/csrf') as { headerName: string; token: string }
  if (csrf.headerName !== 'X-CSRF-TOKEN' || typeof csrf.token !== 'string') throw new Error('Unable to establish a secure session')
  const response = await fetch(path, { method, credentials: 'same-origin', headers: { Accept: 'application/json', 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf.token }, body: JSON.stringify(body) })
  if (!response.ok) {
    let detail = ''
    try { const problem = await response.json() as { title?: string; detail?: string }; const text = problem.detail || problem.title; if (typeof text === 'string' && text.length < 600) detail = text } catch { /* Use the status fallback. */ }
    throw new StaffError(response.status, detail)
  }
  return (response.status === 204 ? undefined : await response.json()) as T
}
async function staffProblem(response: Response) {
  let detail = ''
  try { const problem = await response.json() as { title?: string; detail?: string }; const text = problem.detail || problem.title; if (typeof text === 'string' && text.length < 600) detail = text } catch { /* Use the status fallback. */ }
  return new StaffError(response.status, detail)
}
/** Sends one product photo as the raw request body. Only the photo upload address is accepted. */
export async function staffUpload<T>(path: string, file: Blob): Promise<T> {
  if (!/^\/api\/staff\/catalogue\/products\/[0-9a-f-]{36}\/photos\?revision=\d+&photo=[0-9a-f-]{36}&alt=[A-Za-z0-9%._~!'()*-]{0,800}$/.test(path)) throw new Error('Unsupported staff operation')
  if (isClientPreview) throw new StaffError(405, previewMessage)
  if (file.size > 10 * 1024 * 1024) throw new StaffError(413, 'The photo is larger than 10 MB.')
  const csrf = await getJson('/api/security/csrf') as { headerName: string; token: string }
  if (csrf.headerName !== 'X-CSRF-TOKEN' || typeof csrf.token !== 'string') throw new Error('Unable to establish a secure session')
  const response = await fetch(path, { method: 'POST', credentials: 'same-origin', headers: { Accept: 'application/json', 'Content-Type': ['image/jpeg', 'image/png'].includes(file.type) ? file.type : 'application/octet-stream', 'X-CSRF-TOKEN': csrf.token }, body: file })
  if (!response.ok) throw await staffProblem(response)
  return await response.json() as T
}
export function staffError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Staff sign-in details or verification code are incorrect, or your access changed. Please sign in again.'
    if (error.status === 403) return 'This action is not available for your session. Refresh your sign-in status and retry.'
    if (error.status === 413) return error instanceof StaffError && error.detail ? error.detail : 'The file is too large.'
    if (error.status === 429) return 'Too many attempts. Wait up to 15 minutes before trying again.'
    if (error instanceof StaffError && error.detail) return error.detail
    if (error.status === 404) return 'This record is not available.'
  }
  return 'We could not reach the staff service. Check your connection and try again.'
}
