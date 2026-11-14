import type { RefObject } from 'react'
import type { OrbitApi } from './orbit'

/** Visible, keyboard-reachable camera controls. They do everything dragging does. */
export function OrbitButtons({ api, subject }: { api: RefObject<OrbitApi | null>; subject: string }) {
  return <div className="orbit-buttons" role="group" aria-label={`Camera controls for ${subject}`}>
    <button type="button" aria-label="Turn left" onClick={() => api.current?.rotate(-0.45)}>⟲</button>
    <button type="button" aria-label="Turn right" onClick={() => api.current?.rotate(0.45)}>⟳</button>
    <button type="button" aria-label="Look from higher" onClick={() => api.current?.rotate(0, -0.2)}>↑</button>
    <button type="button" aria-label="Look from lower" onClick={() => api.current?.rotate(0, 0.2)}>↓</button>
    <button type="button" aria-label="Zoom in" onClick={() => api.current?.zoom(0.8)}>+</button>
    <button type="button" aria-label="Zoom out" onClick={() => api.current?.zoom(1.25)}>−</button>
    <button type="button" className="orbit-reset" onClick={() => api.current?.reset()}>Reset view</button>
  </div>
}
