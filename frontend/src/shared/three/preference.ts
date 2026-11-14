import { useSyncExternalStore } from 'react'

// One switch for every 3D view and decorative effect. Kept per browser, never sent to the server.
const key = 'tarime.3d.v1'
const listeners = new Set<() => void>()
function read() { try { return localStorage.getItem(key) !== 'off' } catch { return true } }
let enabled = read()
const subscribe = (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener) } }
window.addEventListener('storage', event => {
  if (event.key === key || event.key === null) { enabled = read(); for (const listener of listeners) listener() }
})
export function set3DEnabled(value: boolean) {
  enabled = value
  try { localStorage.setItem(key, value ? 'on' : 'off') } catch { /* the choice still applies to this tab */ }
  for (const listener of listeners) listener()
}
export function use3DEnabled() { return useSyncExternalStore(subscribe, () => enabled) }

const motionQuery = '(prefers-reduced-motion: reduce)'
const subscribeMotion = (notify: () => void) => {
  const query = matchMedia(motionQuery)
  query.addEventListener('change', notify)
  return () => query.removeEventListener('change', notify)
}
export function useReducedMotion() { return useSyncExternalStore(subscribeMotion, () => matchMedia(motionQuery).matches) }

let support: boolean | undefined
/** Checks once whether this browser can create a WebGL drawing surface at all. */
export function webglAvailable() {
  if (support === undefined) {
    try {
      const canvas = document.createElement('canvas')
      const context = canvas.getContext('webgl2') || canvas.getContext('webgl')
      support = Boolean(context)
      context?.getExtension('WEBGL_lose_context')?.loseContext()
    } catch { support = false }
  }
  return support
}
