import { createContext } from 'react'
import type { Customer, Registration } from './api'

export interface AccountContext {
  user: Customer | null; loading: boolean; unavailable: boolean
  signIn: (phone: string, password: string) => Promise<void>
  register: (details: Registration) => Promise<void>
  signOut: () => Promise<void>
  refresh: () => void
}
export const AuthContext = createContext<AccountContext | null>(null)
