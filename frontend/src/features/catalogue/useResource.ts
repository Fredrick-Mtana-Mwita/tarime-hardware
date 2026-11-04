import { useEffect, useState } from 'react'
import { getJson, ApiError } from '../../shared/api/http'

export function useResource<T>(url: string) {
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState<{ key: string; data?: T; error?: string }>({ key: '' })
  const key = `${url}|${attempt}`
  useEffect(() => {
    const controller = new AbortController()
    void getJson(url, controller.signal).then(value => {
      if (!controller.signal.aborted) setState({ key, data: value as T })
    }).catch((error: unknown) => {
      if (!controller.signal.aborted) setState({ key, error: error instanceof ApiError && error.status === 404
        ? 'This product is not available.' : error instanceof ApiError && error.status === 400
          ? 'Please check the filters. Prices must be positive and the minimum cannot exceed the maximum.'
          : 'We could not load the catalogue. Please try again.' })
    })
    return () => controller.abort()
  }, [url, key])
  return { data: state.key === key ? state.data : undefined, error: state.key === key ? state.error : undefined,
    retry: () => setAttempt(value => value + 1) }
}
