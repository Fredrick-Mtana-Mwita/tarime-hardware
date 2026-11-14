import type { ReactNode } from 'react'
import { ModelStage } from '../ModelStage'
import type { ModelSource } from '../models'
import '../experiences.css'

/** FR-3D-01. Loaded only when a shopper chooses "3D view" on a product page. */
export function ProductViewer({ model, verified, scale, name, fallback }: { model: ModelSource; verified?: boolean; scale?: number; name: string; fallback?: ReactNode }) {
  return <ModelStage model={model} verified={verified} scale={scale} name={name} fallback={fallback} />
}
