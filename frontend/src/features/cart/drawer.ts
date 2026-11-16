import { useSyncExternalStore } from 'react'

let open = false
const listeners = new Set<() => void>()
const set = (value: boolean) => { open = value; for (const listener of listeners) listener() }
export const openCartDrawer = () => set(true)
export const closeCartDrawer = () => set(false)
const subscribe = (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener) } }
export function useCartDrawer() { return useSyncExternalStore(subscribe, () => open) }
