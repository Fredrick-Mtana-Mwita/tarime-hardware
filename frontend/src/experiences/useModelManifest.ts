import { useResource } from '../features/catalogue/useResource'
import { builtInKinds, roomFinishes, zones } from './manifest'
import type { ModelEntry, ModelKind, RoomFinish, RoomSurface, ZoneId } from './manifest'

interface ServerEntry {
  assetId: string; version: number; productId: string; sourceKind: string; modelKind: string | null; modelUrl: string | null; verified: boolean
  zone: string; roomSurface: string | null; roomFinish: string | null; tileMm: number | null; variantScales: Record<string, number> | null
}
interface ServerManifest { version: string; entries: ServerEntry[] }

/** Keeps only entries this build knows how to draw, so a newer server can never break an older page. */
export function readManifest(manifest: ServerManifest): Map<string, ModelEntry> {
  const result = new Map<string, ModelEntry>()
  for (const entry of manifest.entries) {
    const kind = builtInKinds.includes(entry.modelKind as ModelKind) ? entry.modelKind as ModelKind : null
    const modelUrl = typeof entry.modelUrl === 'string' && /^\/api\/models\/[0-9a-f-]{36}\/file$/.test(entry.modelUrl) ? entry.modelUrl : null
    if (!kind && !modelUrl) continue
    if (!zones.some(zone => zone.id === entry.zone)) continue
    const surface = entry.roomSurface as RoomSurface | null, finish = entry.roomFinish as RoomFinish | null
    const room = surface && finish && roomFinishes[surface]?.includes(finish)
      ? { surface, finish, tileMetres: entry.tileMm ? entry.tileMm / 1000 : undefined, scaleConfirmed: Boolean(entry.tileMm) } : undefined
    result.set(entry.productId, { assetId: entry.assetId, version: entry.version, productId: entry.productId, kind, modelUrl, verified: entry.verified === true,
      variantScales: entry.variantScales ?? {}, room, zone: entry.zone as ZoneId })
  }
  return result
}

/** The published 3D models, keyed by product ID. While loading, or if the request fails, there are simply no 3D models. */
export function useModelManifest() {
  const resource = useResource<ServerManifest>('/api/models/manifest')
  return { entries: resource.data ? readManifest(resource.data) : undefined, version: resource.data?.version, error: resource.error, retry: resource.retry }
}
