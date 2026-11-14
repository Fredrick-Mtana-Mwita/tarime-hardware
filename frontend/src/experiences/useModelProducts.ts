import type { Product, ProductPage } from '../features/catalogue/types'
import { useResource } from '../features/catalogue/useResource'
import type { ModelEntry } from './manifest'
import { useModelManifest } from './useModelManifest'

export interface ModelProduct { product: Product; entry: ModelEntry }

/** Catalogue products that also have a published 3D model. A product the shop unpublishes disappears from every scene. */
export function useModelProducts() {
  const resource = useResource<ProductPage>('/api/products?size=48&sort=name')
  const manifest = useModelManifest()
  const items: ModelProduct[] | undefined = resource.data && manifest.entries ? resource.data.items.flatMap(product => {
    const entry = manifest.entries?.get(product.id)
    return entry ? [{ product, entry }] : []
  }) : undefined
  return { items, version: manifest.version, error: resource.error || manifest.error, retry: () => { resource.retry(); manifest.retry() } }
}
