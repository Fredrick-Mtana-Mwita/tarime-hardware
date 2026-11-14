import { useFrame, useThree } from '@react-three/fiber'
import { useEffect, useRef } from 'react'
import type { RefObject } from 'react'
import type { OrbitApi, OrbitLimits, View } from './orbit'

const clamp = (value: number, low: number, high: number) => Math.min(high, Math.max(low, value))
const copy = (view: View): View => ({ ...view, target: [...view.target] })

/**
 * A small bounded orbit camera. Dragging turns it; the HTML buttons beside the canvas call the same
 * API, so nothing depends on dragging. It only asks for new frames while the camera is still moving.
 */
export function OrbitRig({ apiRef, home, limits, instant = false, composedFor = 0 }: { apiRef: RefObject<OrbitApi | null>; home: View; limits: OrbitLimits; instant?: boolean; composedFor?: number }) {
  const camera = useThree(state => state.camera)
  const gl = useThree(state => state.gl)
  const invalidate = useThree(state => state.invalidate)
  const goal = useRef<View>(copy(home))
  const now = useRef<View>(copy(home))

  useEffect(() => {
    const bound = (view: View) => {
      view.distance = clamp(view.distance, limits.minDistance, limits.maxDistance)
      view.polar = clamp(view.polar, limits.minPolar ?? 0.35, limits.maxPolar ?? 1.5)
      if (limits.minAzimuth !== undefined && limits.maxAzimuth !== undefined) view.azimuth = clamp(view.azimuth, limits.minAzimuth, limits.maxAzimuth)
    }
    const controls: OrbitApi = {
      rotate(azimuth, polar = 0) { goal.current.azimuth += azimuth; goal.current.polar += polar; bound(goal.current); invalidate() },
      zoom(factor) { goal.current.distance *= factor; bound(goal.current); invalidate() },
      goTo(view) { goal.current = copy(view); bound(goal.current); invalidate() },
      reset() { goal.current = copy(home); bound(goal.current); invalidate() },
    }
    apiRef.current = controls
    controls.reset()
    const element = gl.domElement
    let drag: { id: number; x: number; y: number } | null = null
    const down = (event: PointerEvent) => {
      if (event.button !== 0) return
      drag = { id: event.pointerId, x: event.clientX, y: event.clientY }
      element.setPointerCapture(event.pointerId)
    }
    const move = (event: PointerEvent) => {
      if (!drag || drag.id !== event.pointerId) return
      controls.rotate((drag.x - event.clientX) * 0.008, (drag.y - event.clientY) * 0.006)
      drag.x = event.clientX; drag.y = event.clientY
    }
    const up = (event: PointerEvent) => { if (drag?.id === event.pointerId) drag = null }
    element.addEventListener('pointerdown', down)
    element.addEventListener('pointermove', move)
    element.addEventListener('pointerup', up)
    element.addEventListener('pointercancel', up)
    return () => {
      apiRef.current = null
      element.removeEventListener('pointerdown', down)
      element.removeEventListener('pointermove', move)
      element.removeEventListener('pointerup', up)
      element.removeEventListener('pointercancel', up)
    }
  }, [apiRef, gl, home, invalidate, limits])

  useFrame((state, delta) => {
    // Views are composed for a wide canvas. On a narrower one (a phone) step back so the sides still fit.
    const stepBack = composedFor ? Math.max(1, composedFor / (state.size.width / state.size.height)) : 1
    const from = now.current, to = goal.current
    const ease = instant ? 1 : 1 - Math.exp(-Math.min(delta, 0.1) * 7)
    let moving = false
    const approach = (current: number, wanted: number) => {
      const next = current + (wanted - current) * ease
      if (Math.abs(wanted - next) > 0.0008) { moving = true; return next }
      return wanted
    }
    from.azimuth = approach(from.azimuth, to.azimuth)
    from.polar = approach(from.polar, to.polar)
    from.distance = approach(from.distance, to.distance)
    from.target = [approach(from.target[0], to.target[0]), approach(from.target[1], to.target[1]), approach(from.target[2], to.target[2])]
    const reach = from.distance * stepBack, flat = Math.sin(from.polar) * reach
    camera.position.set(from.target[0] + flat * Math.sin(from.azimuth), from.target[1] + Math.cos(from.polar) * reach, from.target[2] + flat * Math.cos(from.azimuth))
    camera.lookAt(from.target[0], from.target[1], from.target[2])
    if (moving) invalidate()
  })
  return null
}
