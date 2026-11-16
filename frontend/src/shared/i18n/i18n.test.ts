import { describe, expect, it } from 'vitest'
import { sw } from './sw'
import { translate } from './index'

const sources = import.meta.glob(['../../**/*.{ts,tsx}', '!../../**/*.test.{ts,tsx}'], { query: '?raw', import: 'default', eager: true }) as Record<string, string>
const placeholders = (text: string) => [...text.matchAll(/\{(\w+)\}/g)].map(m => m[1]).sort().join(',')

describe('Kiswahili dictionary', () => {
  it('has a translation for every t("…") text used in the storefront', () => {
    const missing: string[] = []; let used = 0
    for (const [file, code] of Object.entries(sources)) {
      for (const match of code.matchAll(/(?<![\w.])t\(\s*(['"])((?:\\.|(?!\1).)+)\1/g)) {
        const key = match[2].replace(/\\(["'\\])/g, '$1')
        used++
        if (!(key in sw)) missing.push(`${file}: ${key}`)
      }
    }
    expect(missing).toEqual([])
    expect(used).toBeGreaterThan(250)
  })
  it('keeps the same {placeholders} and no empty entries', () => {
    for (const [en, text] of Object.entries(sw)) {
      expect(text.trim(), en).not.toBe('')
      expect(placeholders(text), en).toBe(placeholders(en))
    }
  })
  it('fills placeholders and falls back to English', () => {
    expect(translate('sw', 'Welcome, {name}', { name: 'Juma' })).toBe('Karibu, Juma')
    expect(translate('sw', 'Text the dictionary does not have')).toBe('Text the dictionary does not have')
    expect(translate('en', 'Add to cart')).toBe('Add to cart')
  })
})
