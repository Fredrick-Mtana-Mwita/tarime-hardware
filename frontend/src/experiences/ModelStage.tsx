import { useRef } from 'react'
import type { ReactNode } from 'react'
import { OrbitButtons } from '../shared/three/OrbitButtons'
import { OrbitRig } from '../shared/three/OrbitRig'
import type { OrbitApi, OrbitLimits, View } from '../shared/three/orbit'
import { useReducedMotion } from '../shared/three/preference'
import { Scene3D } from '../shared/three/Scene3D'
import { modelNote } from './manifest'
import { BlobShadow, ProductModel, StudioLight } from './models'
import type { ModelSource } from './models'

const home: View = { target: [0, 0.45, 0], azimuth: 0.6, polar: 1.15, distance: 2.7 }
const limits: OrbitLimits = { minDistance: 1.4, maxDistance: 5, minPolar: 0.35, maxPolar: 1.5 }

/** One product model on a turntable-style stage: drag or use the buttons to turn, zoom and reset. */
export function ModelStage({ model, verified = false, scale = 1, name, fallback }: { model: ModelSource; verified?: boolean; scale?: number; name: string; fallback?: ReactNode }) {
  const api = useRef<OrbitApi | null>(null)
  const reduced = useReducedMotion()
  return <div className="x3-stage">
    <div className="x3-canvas x3-canvas-light">
      <Scene3D label={`${verified ? '3D' : 'Illustrative 3D'} model of ${name}. Use the camera buttons below to turn, zoom and reset.`} fallback={fallback}>
        <StudioLight />
        <BlobShadow />
        <ProductModel kind={model.kind} modelUrl={model.modelUrl} scale={scale} />
        <OrbitRig apiRef={api} home={home} limits={limits} instant={reduced} />
      </Scene3D>
    </div>
    <OrbitButtons api={api} subject={name} />
    <p className="x3-note">{modelNote({ verified })}</p>
  </div>
}
