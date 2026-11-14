// What the storefront may show in 3D.
//
// The live shop reads this from GET /api/models/manifest, which lists only models the Owner has
// published for products that are currently on sale. This file holds the shared types, plus the
// sample table used by the backend-less client preview. Prices, units, stock and variant IDs never
// come from here: they are always read from the catalogue API.

export const previewManifestVersion = '2026.11.12-1'

export type ModelKind = 'cement-bag' | 'paint-tin' | 'tile-stack' | 'wall-tile' | 'filament-bulb' | 'cable-roll'
  | 'pendant' | 'hammer' | 'roller' | 'breaker-board' | 'roof-sheet' | 'pipe'
export type RoomSurface = 'floor' | 'wall' | 'light'
export type RoomFinish = 'marble' | 'gloss-white' | 'glazed' | 'pendant' | 'bulb'
export type ZoneId = 'building' | 'finishes' | 'electrical' | 'tools'

/** One published model, as the storefront uses it. `kind` is a built-in shape; `modelUrl` is an uploaded GLB. */
export interface ModelEntry {
  assetId: string
  version: number
  productId: string
  kind: ModelKind | null
  modelUrl: string | null
  /** True only when the Owner approved the model as the exact product. */
  verified: boolean
  /** Relative size of each sellable option, keyed by variant ID. Options not listed use 1. */
  variantScales: Record<string, number>
  /** How the product can be previewed in the room visualizer. `tileMetres` is the real tile edge when known. */
  room?: { surface: RoomSurface; finish: RoomFinish; tileMetres?: number; scaleConfirmed: boolean }
  zone: ZoneId
}
/** Preview-only sample row, keyed by SKU. */
interface PreviewModel { kind: ModelKind; zone: ZoneId; variantScale?: Record<string, number>; room?: { surface: RoomSurface; finish: RoomFinish; tileMetres?: number } }

export const zones: { id: ZoneId; name: string }[] = [
  { id: 'building', name: 'Building and roofing' },
  { id: 'finishes', name: 'Paint and tiles' },
  { id: 'electrical', name: 'Electrical and lighting' },
  { id: 'tools', name: 'Tools and plumbing' },
]

export const previewModels: Record<string, PreviewModel> = {
  'FPA-SAMPLE-001': { kind: 'cement-bag', zone: 'building', variantScale: { 'FPA-SAMPLE-001-01': 1, 'FPA-SAMPLE-001-02': 0.8 } },
  'FPA-SAMPLE-011': { kind: 'roof-sheet', zone: 'building' },
  'FPA-SAMPLE-002': { kind: 'paint-tin', zone: 'finishes', room: { surface: 'wall', finish: 'gloss-white' } },
  'FPA-SAMPLE-008': { kind: 'roller', zone: 'finishes' },
  'FPA-SAMPLE-003': { kind: 'tile-stack', zone: 'finishes', room: { surface: 'floor', finish: 'marble', tileMetres: 0.6 } },
  'FPA-SAMPLE-009': { kind: 'wall-tile', zone: 'finishes', room: { surface: 'wall', finish: 'glazed' } },
  'FPA-SAMPLE-004': { kind: 'filament-bulb', zone: 'electrical', room: { surface: 'light', finish: 'bulb' } },
  'FPA-SAMPLE-005': { kind: 'cable-roll', zone: 'electrical' },
  'FPA-SAMPLE-006': { kind: 'pendant', zone: 'electrical', room: { surface: 'light', finish: 'pendant' } },
  'FPA-SAMPLE-010': { kind: 'breaker-board', zone: 'electrical' },
  'FPA-SAMPLE-007': { kind: 'hammer', zone: 'tools' },
  'FPA-SAMPLE-012': { kind: 'pipe', zone: 'tools' },
}

export const builtInKinds: ModelKind[] = ['cement-bag', 'paint-tin', 'tile-stack', 'wall-tile', 'filament-bulb', 'cable-roll', 'pendant', 'hammer', 'roller', 'breaker-board', 'roof-sheet', 'pipe']
export const roomFinishes: Record<RoomSurface, RoomFinish[]> = { floor: ['marble'], wall: ['gloss-white', 'glazed'], light: ['pendant', 'bulb'] }
export const illustrativeNote = 'Illustrative 3D model. It is not the exact branded product; check the photos and specifications.'
export const verifiedNote = 'The shop has approved this 3D model as the product shown. Colours can still look different on your screen.'
export function modelNote(entry: Pick<ModelEntry, 'verified'>) { return entry.verified ? verifiedNote : illustrativeNote }
