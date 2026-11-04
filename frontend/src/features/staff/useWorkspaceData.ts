import { useEffect, useState } from 'react'
import { getJson } from '../../shared/api/http'
import { staffError } from './api'

export function useWorkspaceData<T>(url: string) {
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState<{ key: string; data?: T; error?: string }>({ key: '' })
  const key = url + '|' + attempt
  useEffect(() => {
    const controller = new AbortController()
    void getJson(url, controller.signal).then(data => { if (!controller.signal.aborted) setState({ key, data: data as T }) })
      .catch((error: unknown) => { if (!controller.signal.aborted) setState({ key, error: staffError(error) }) })
    return () => controller.abort()
  }, [url, key])
  return { data: state.key === key ? state.data : undefined, error: state.key === key ? state.error : undefined, retry: () => setAttempt(n => n + 1) }
}
