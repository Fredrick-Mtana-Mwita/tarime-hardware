import { isClientPreview } from '../preview'
export class ApiError extends Error {
  constructor(public readonly status: number) {
    super(`Request failed (${status})`)
    this.name = 'ApiError'
  }
}

export async function getJson(path: string, signal?: AbortSignal): Promise<unknown> {
  if (!path.startsWith('/api/')) throw new Error('Only same-origin API paths are supported')
  if (isClientPreview) return (await import('../../preview/api')).previewRead(path)
  const response = await fetch(path, { credentials: 'same-origin', signal, headers: { Accept: 'application/json' } })
  if (!response.ok) throw new ApiError(response.status)
  return response.json()
}
