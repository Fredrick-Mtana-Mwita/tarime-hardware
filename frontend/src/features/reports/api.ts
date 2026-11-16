export interface SalesReport {
  from: string; to: string; currency: string
  orders: { submitted: number; requestedValue: number; confirmed: number; rejected: number; cancelled: number }
  sales: { completedOrders: number; gross: number; refunds: number; net: number }
  money: { cash: number; mobileMoney: number; received: number; receipts: number; reversed: number; refunded: number; net: number }
  days: { date: string; requestedValue: number; completedSales: number; received: number; refunded: number }[]
  topProducts: { product: string; variant: string; unit: string; quantity: number; value: number }[]
}
export interface BalancesReport { currency: string; owedToShop: number; owedToCustomers: number; orders: number
  rows: { orderId: string; reference: string; customer: string; phone: string; status: string; total: number; netPaid: number; due: number; refundDue: number; ageDays: number }[] }
export interface StockReport { from: string; to: string; branch: string; currency: string; items: number; lowStock: number; valueAtPrice: number
  rows: { variantId: string; product: string; variant: string; sku: string; unit: string; onHand: number; reserved: number; available: number; threshold: number; lowStock: boolean
    unitPrice: number; valueAtPrice: number; received: number; dispatched: number; released: number }[] }

const shopDay = (date: Date) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Dar_es_Salaam' }).format(date)
const shift = (iso: string, days: number) => { const d = new Date(iso + 'T12:00:00Z'); d.setUTCDate(d.getUTCDate() + days); return d.toISOString().slice(0, 10) }
export type Preset = 'today' | '7d' | '30d' | 'month' | 'last-month'
/** Date ranges in shop time (EAT), both ends included. */
export function presetRange(preset: Preset, now = new Date()): { from: string; to: string } {
  const today = shopDay(now)
  const monthStart = today.slice(0, 8) + '01'
  if (preset === 'today') return { from: today, to: today }
  if (preset === '7d') return { from: shift(today, -6), to: today }
  if (preset === '30d') return { from: shift(today, -29), to: today }
  if (preset === 'month') return { from: monthStart, to: today }
  const lastEnd = shift(monthStart, -1)
  return { from: lastEnd.slice(0, 8) + '01', to: lastEnd }
}
export const todayInShop = () => shopDay(new Date())
