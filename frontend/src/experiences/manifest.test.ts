import { describe, expect, it } from 'vitest'
import { builtInKinds, previewManifestVersion, previewModels, roomFinishes, zones } from './manifest'
import { readManifest } from './useModelManifest'

const entry = { assetId: 'a', version: 2, productId: 'p1', sourceKind: 'BUILT_IN', modelKind: 'paint-tin', modelUrl: null, verified: false, zone: 'finishes',
  roomSurface: null, roomFinish: null, tileMm: null, variantScales: { v1: 0.8 } }

describe('3D model manifest', () => {
  it('reads a published built-in model keyed by product', () => {
    const model = readManifest({ version: 'x', entries: [entry] }).get('p1')
    expect(model).toMatchObject({ kind: 'paint-tin', modelUrl: null, verified: false, version: 2, variantScales: { v1: 0.8 }, zone: 'finishes' })
    expect(model?.room).toBeUndefined()
  })
  it('accepts an uploaded model only from this site\'s own model address', () => {
    const id = '00000000-0000-4000-8000-000000000700'
    const read = (modelUrl: string) => readManifest({ version: 'x', entries: [{ ...entry, sourceKind: 'GLB', modelKind: null, modelUrl }] }).get('p1')
    expect(read(`/api/models/${id}/file`)?.modelUrl).toBe(`/api/models/${id}/file`)
    for (const unsafe of ['https://example.com/a.glb', '//example.com/a.glb', `/api/models/${id}/file?x=1`, 'javascript:alert(1)']) expect(read(unsafe)).toBeUndefined()
  })
  it('skips shapes, zones and finishes this build does not know', () => {
    expect(readManifest({ version: 'x', entries: [{ ...entry, modelKind: 'spaceship' }] }).size).toBe(0)
    expect(readManifest({ version: 'x', entries: [{ ...entry, zone: 'garden' }] }).size).toBe(0)
    expect(readManifest({ version: 'x', entries: [{ ...entry, roomSurface: 'floor', roomFinish: 'pendant' }] }).get('p1')?.room).toBeUndefined()
  })
  it('treats a tile size as confirmed scale and converts it to metres', () => {
    const room = readManifest({ version: 'x', entries: [{ ...entry, roomSurface: 'floor', roomFinish: 'marble', tileMm: 600 }] }).get('p1')?.room
    expect(room).toEqual({ surface: 'floor', finish: 'marble', tileMetres: 0.6, scaleConfirmed: true })
    expect(readManifest({ version: 'x', entries: [{ ...entry, roomSurface: 'wall', roomFinish: 'glazed' }] }).get('p1')?.room?.scaleConfirmed).toBe(false)
  })
  it('keeps the preview sample table consistent', () => {
    expect(previewManifestVersion).toMatch(/^\d{4}\.\d{2}\.\d{2}-\d+$/)
    const known = new Set(zones.map(zone => zone.id))
    for (const [sku, sample] of Object.entries(previewModels)) {
      expect(builtInKinds).toContain(sample.kind); expect(known.has(sample.zone)).toBe(true)
      if (sample.room) expect(roomFinishes[sample.room.surface]).toContain(sample.room.finish)
      for (const variantSku of Object.keys(sample.variantScale ?? {})) expect(variantSku.startsWith(sku + '-')).toBe(true)
    }
  })
})
