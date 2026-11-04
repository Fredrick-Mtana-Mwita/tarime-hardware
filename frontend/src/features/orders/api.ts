import { isClientPreview, previewMessage } from '../../shared/preview'
import { ApiError, getJson } from '../../shared/api/http'

export class CommerceError extends ApiError {
  constructor(status: number, public readonly detail: string) { super(status) }
}
export async function commercePost<T>(path: string, body: unknown, extra: Record<string, string> = {}, signal?: AbortSignal): Promise<T> {
  if (!(path === '/api/cart/quote' || path === '/api/orders' || /^\/api\/notifications\/[0-9a-f-]+\/read$/.test(path))) throw new Error('Unsupported operation')
  if (isClientPreview) {
    if (path === '/api/cart/quote') return (await import('../../preview/api')).previewQuote(body) as T
    throw new CommerceError(400, previewMessage)
  }
  const csrf = await getJson('/api/security/csrf', signal) as { headerName: string; token: string }
  if (csrf.headerName !== 'X-CSRF-TOKEN' || typeof csrf.token !== 'string') throw new Error('Unable to establish a secure session')
  const response = await fetch(path, { method: 'POST', credentials: 'same-origin', signal,
    headers: { ...extra, 'Content-Type': 'application/json', Accept: 'application/json', 'X-CSRF-TOKEN': csrf.token }, body: JSON.stringify(body) })
  if (!response.ok) {
    let title = ''
    try { const problem = await response.json() as { title?: unknown }; if (typeof problem.title === 'string' && problem.title.length <= 600) title = problem.title } catch { /* Use the status fallback for non-JSON failures. */ }
    throw new CommerceError(response.status, title)
  }
  return (response.status === 204 ? undefined : await response.json()) as T
}
export function orderError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Please sign in again before continuing. Your cart is saved.'
    if (error.status === 403) return 'Your secure session has expired. Please retry.'
    if (error.status === 404) return 'This order is not available.'
    if (error instanceof CommerceError && [400, 409, 429].includes(error.status) && error.detail) return error.detail
    if (error.status === 429) return 'Too many requests. Please try again later.'
  }
  return 'We could not reach the shop. Check your connection and retry. An interrupted submission may already have succeeded; retrying the same details will retrieve it.'
}
export interface QuoteLine {
  variantId: string; productId: string; productName: string; variantLabel: string; sku: string; unit: string
  fractional: boolean; quantity: number; unitPrice: number; currency: string; lineTotal: number; available: number
}
export interface Quote { items: QuoteLine[]; currency: string; subtotal: number }
export type Branch = 'TARIME' | 'MWANZA'
export const branchName = (branch: Branch | null) => branch === 'TARIME' ? 'Tarime (main branch)' : branch === 'MWANZA' ? 'Mwanza' : 'Branch not selected'
export interface OrderSummary {
  branch: Branch | null
  id: string; reference: string; status: string; fulfilment: 'DELIVERY' | 'PICKUP'; currency: string
  subtotal: number; deliveryFee: number | null; total: number | null; createdAt: string
}
export interface Order { summary: OrderSummary; name: string; phone: string; address: string; instructions: string
  items: Omit<QuoteLine, 'fractional' | 'currency' | 'available'>[] }
export interface Notice { id: string; orderId: string; title: string; message: string; createdAt: string; readAt: string | null }
export interface OrderPage { items: OrderSummary[]; total: number; page: number; size: number }
