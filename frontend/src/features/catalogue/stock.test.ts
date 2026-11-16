import { describe, expect, it } from 'vitest'
import { lowStockText } from './stock'
import type { Variant } from './types'

const variant = (available: number, inStock = true): Variant => ({ id: 'v', sku: 's', label: 'l', unit: 'bag', price: 1, currency: 'TZS', pricingMode: 'FIXED', available, inStock, fractional: false })

describe('lowStockText', () => {
  it('shows only genuinely low stock', () => {
    expect(lowStockText(variant(7))).toBe('Only 7 left')
    expect(lowStockText(variant(10))).toBe('Only 10 left')
    expect(lowStockText(variant(11))).toBeNull()
    expect(lowStockText(variant(0, false))).toBeNull()
    expect(lowStockText(undefined)).toBeNull()
  })
  it('speaks Kiswahili', () => { expect(lowStockText(variant(3), 'sw')).toBe('Zimebaki 3 tu') })
  it('trims fractional stock', () => { expect(lowStockText({ ...variant(2.5), unit: 'm', fractional: true })).toBe('Only 2.5 m left') })
})
