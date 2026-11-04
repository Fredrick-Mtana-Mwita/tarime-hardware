import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { authPost, readCustomer, session } from './api'
import type { Customer, Registration } from './api'
import { AuthContext } from './auth-context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Customer | null>(null)
  const [loading, setLoading] = useState(true)
  const [unavailable, setUnavailable] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const sessionRevision = useRef(0)
  useEffect(() => {
    // Refresh cookie-backed identity after signing in or out in another tab.
    const focus = () => setAttempt(value => value + 1)
    window.addEventListener('focus', focus)
    return () => window.removeEventListener('focus', focus)
  }, [])
  useEffect(() => {
    const controller = new AbortController()
    const revision = sessionRevision.current
    void session(controller.signal).then(customer => {
      if (!controller.signal.aborted && revision === sessionRevision.current) { setUser(customer); setLoading(false); setUnavailable(false) }
    }).catch(() => { if (!controller.signal.aborted && revision === sessionRevision.current) { setLoading(false); setUnavailable(true) } })
    return () => controller.abort()
  }, [attempt])
  async function signIn(phone: string, password: string) {
    const customer = readCustomer(await authPost('/api/auth/login', { phone, password }))
    sessionRevision.current += 1
    setUser(customer); setLoading(false); setUnavailable(false)
  }
  async function register(details: Registration) {
    const customer = readCustomer(await authPost('/api/auth/register', details))
    sessionRevision.current += 1
    setUser(customer); setLoading(false); setUnavailable(false)
  }
  async function signOut() {
    await authPost('/api/auth/logout', {}); sessionRevision.current += 1; setUser(null); setLoading(false); setUnavailable(false)
  }
  function refresh() { setLoading(true); setAttempt(value => value + 1) }
  return <AuthContext.Provider value={{ user, loading, unavailable, signIn, register, signOut, refresh }}>{children}</AuthContext.Provider>
}
