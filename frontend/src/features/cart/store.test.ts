import { describe, expect, it, vi } from 'vitest'

// The cart adapter has browser subscriptions; the parsing/quantity rules need no browser runtime.
vi.stubGlobal('window', { addEventListener: vi.fn() })
vi.stubGlobal('localStorage', { getItem: () => null })
const { parseCart, quantity } = await import('./store')
describe('cart input boundaries', () => {
  it('accepts fractional quantities without silent truncation', () => {
    expect(quantity('1.125')).toBe('1.125')
    expect(quantity('001.000')).toBe('1')
    for (const value of ['0', '-1', 'NaN', '1.0001', '1e2', '1000001', '']) expect(quantity(value)).toBeNull()
  })
  it('rejects corrupted storage and deduplicates product options', () => {
    const item = { productId: '00000000-0000-4000-8000-000000000100', variantId: '00000000-0000-4000-8000-000000000101', name: 'Cement', label: 'Bag', quantity: '2' }
    expect(parseCart(JSON.stringify([item, item]))).toEqual([item])
    expect(parseCart('not json')).toEqual([])
    expect(parseCart('{}')).toEqual([])
    expect(parseCart(JSON.stringify([{ ...item, quantity: '-2' }]))).toEqual([])
    expect(parseCart(JSON.stringify([{ ...item, variantId: '../account' }]))).toEqual([])
  })
})
