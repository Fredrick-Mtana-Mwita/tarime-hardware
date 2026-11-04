import { useSyncExternalStore } from 'react'

export interface CartItem { productId: string; variantId: string; name: string; label: string; quantity: string }
const key = 'finishers.cart.v1'
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
export function quantity(value: string): string | null {
  if (!/^\d{1,7}(\.\d{1,3})?$/.test(value)) return null
  const number = Number(value)
  return number > 0 && number <= 1000000 ? String(number) : null
}
export function parseCart(raw: string | null): CartItem[] {
  try {
    const parsed: unknown = JSON.parse(raw || '[]')
    if (!Array.isArray(parsed) || parsed.length > 50) return []
    const seen = new Set<string>()
    return parsed.filter((i): i is CartItem => {
      if (!i || typeof i !== 'object' || typeof i.productId !== 'string' || !uuid.test(i.productId)
        || typeof i.variantId !== 'string' || !uuid.test(i.variantId) || seen.has(i.variantId)
        || typeof i.quantity !== 'string' || !quantity(i.quantity)
        || typeof i.name !== 'string' || i.name.length > 200 || typeof i.label !== 'string' || i.label.length > 120) return false
      seen.add(i.variantId); return true
    })
  } catch { return [] }
}
let storageAvailable = true
function load() { try { return parseCart(localStorage.getItem(key)) } catch { storageAvailable = false; return [] } }
let state = { items: load(), storageAvailable }
const listeners = new Set<() => void>()
function notify() { for (const listener of listeners) listener() }
window.addEventListener('storage', event => {
  if (event.key === key || event.key === null) { state = { items: load(), storageAvailable }; notify() }
})
function save(items: CartItem[]) {
  try { if (items.length) localStorage.setItem(key, JSON.stringify(items)); else localStorage.removeItem(key) }
  catch { storageAvailable = false }
  state = { items, storageAvailable }; notify()
}
export function addToCart(item: CartItem) {
  const q = quantity(item.quantity)
  if (!q) throw new Error('Enter a positive quantity with up to 3 decimal places.')
  const existing = state.items.find(i => i.variantId === item.variantId)
  if (!existing && state.items.length >= 50) throw new Error('Your cart can contain up to 50 product options.')
  const sum = quantity(String(((existing ? Number(existing.quantity) * 1000 : 0) + Number(q) * 1000) / 1000))
  if (!sum) throw new Error('The quantity is too large.')
  save(existing ? state.items.map(i => i.variantId === item.variantId ? { ...i, quantity: sum } : i) : [...state.items, { ...item, quantity: q }])
}
export function updateQuantity(id: string, value: string) {
  const q = quantity(value)
  if (q) save(state.items.map(i => i.variantId === id ? { ...i, quantity: q } : i))
}
export function removeItem(id: string) { save(state.items.filter(i => i.variantId !== id)) }
export function clearPurchased(purchased: CartItem[]) {
  save(state.items.flatMap(i => {
    const bought = purchased.find(p => p.variantId === i.variantId)
    if (!bought) return [i]
    const remaining = Math.round(Number(i.quantity) * 1000) - Math.round(Number(bought.quantity) * 1000)
    return remaining > 0 ? [{ ...i, quantity: String(remaining / 1000) }] : []
  }))
}
const subscribe = (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener) } }
export function useCart() { return useSyncExternalStore(subscribe, () => state) }
