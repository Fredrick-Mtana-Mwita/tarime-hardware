import { useFrame } from '@react-three/fiber'
import { useRef } from 'react'
import type { Group } from 'three'
import { Scene3D } from '../../shared/three/Scene3D'
import type { ModelKind } from '../manifest'
import { ProductModel, StudioLight } from '../models'

const floaters: { kind: ModelKind; at: [number, number, number]; scale: number; speed: number }[] = [
  { kind: 'paint-tin', at: [-1.7, 0.75, 0], scale: 0.95, speed: 0.35 },
  { kind: 'tile-stack', at: [0, 1.25, -0.6], scale: 1, speed: -0.28 },
  { kind: 'filament-bulb', at: [1.75, 0.95, 0.2], scale: 1.05, speed: 0.4 },
  { kind: 'cement-bag', at: [-0.8, -0.35, 0.6], scale: 0.9, speed: -0.3 },
  { kind: 'hammer', at: [1.0, -0.4, 0.5], scale: 0.95, speed: 0.33 },
]

function Floater({ kind, at, scale, speed, phase }: { kind: ModelKind; at: [number, number, number]; scale: number; speed: number; phase: number }) {
  const group = useRef<Group>(null)
  useFrame(({ clock }) => {
    const body = group.current
    if (!body) return
    const time = clock.elapsedTime
    body.rotation.y = phase + time * speed
    body.position.y = at[1] + Math.sin(time * 0.8 + phase) * 0.1
  })
  return <group ref={group} position={at} rotation={[0.3, phase, 0.12]} scale={scale}><group position={[0, -0.35, 0]}><ProductModel kind={kind} /></group></group>
}

/**
 * FR-3D-05. Decorative only: slowly turning products beside a heading. It sits next to the text, never
 * behind it, stops when off screen, holds still under reduced motion and disappears when 3D is off.
 */
export function FloatingProducts() {
  return <Scene3D label="" decorative animate>
    <StudioLight />
    {floaters.map((floater, index) => <Floater key={floater.kind} {...floater} phase={index * 1.3} />)}
  </Scene3D>
}
