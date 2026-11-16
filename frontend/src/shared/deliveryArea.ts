import { useSyncExternalStore } from 'react'

/** The customer's delivery area, remembered in this browser only. Used by the header and prefilled at checkout. */
const KEY = 'finishers.deliveryArea'
function stored() { try { return (localStorage.getItem(KEY) || '').slice(0, 60) } catch { return '' } }
let area = stored()
const listeners = new Set<() => void>()
export function setDeliveryArea(next: string) {
  area = next.trim().slice(0, 60)
  try { if (area) localStorage.setItem(KEY, area); else localStorage.removeItem(KEY) } catch { /* Kept for this visit. */ }
  for (const listener of listeners) listener()
}
export const deliveryArea = () => area
export function useDeliveryArea() {
  return useSyncExternalStore(listener => { listeners.add(listener); return () => { listeners.delete(listener) } }, () => area)
}
