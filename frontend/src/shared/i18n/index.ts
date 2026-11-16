import { useSyncExternalStore } from 'react'
import { sw } from './sw'

export type Lang = 'en' | 'sw'
const KEY = 'finishers.lang'
function stored(): Lang | null { try { const v = localStorage.getItem(KEY); return v === 'en' || v === 'sw' ? v : null } catch { return null } }

// English unless the visitor has switched to Kiswahili with the SW/EN switch before.
let lang: Lang = stored() ?? 'en'
const listeners = new Set<() => void>()
const subscribe = (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener) } }
if (typeof document !== 'undefined') document.documentElement.lang = lang

/** Remembered for this browser. Nothing is sent to the server. */
export function setLanguage(next: Lang) {
  lang = next
  try { localStorage.setItem(KEY, next) } catch { /* The choice still applies for this visit. */ }
  document.documentElement.lang = next
  for (const listener of listeners) listener()
}

export type Vars = Record<string, string | number>
/** English text is the key. Missing Kiswahili falls back to English; a unit test keeps the dictionary complete. */
export function translate(to: Lang, text: string, vars?: Vars) {
  const base = to === 'sw' ? sw[text] ?? text : text
  return vars ? base.replace(/\{(\w+)\}/g, (match, name: string) => name in vars ? String(vars[name]) : match) : base
}
export function useLanguage() { return useSyncExternalStore(subscribe, () => lang) }
export function useT() {
  const current = useLanguage()
  return (text: string, vars?: Vars) => translate(current, text, vars)
}
/** Locale for dates and numbers. */
export const locale = (to: Lang) => to === 'sw' ? 'sw-TZ' : 'en-TZ'
/** Current language outside React (date helpers). Components re-render through useT/useLanguage. */
export const currentLanguage = () => lang
/** Database text with an optional Kiswahili version: use it when Kiswahili is chosen. */
export const pick = (to: Lang, en: string, sw?: string | null) => to === 'sw' && sw ? sw : en
