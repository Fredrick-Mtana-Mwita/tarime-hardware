export type Method = 'CASH' | 'MOBILE_MONEY'
export type Provider = 'MPESA' | 'MIXX' | 'AIRTEL_MONEY' | 'HALOPESA'
export type BalanceStatus = 'UNPAID' | 'PART_PAID' | 'PAID' | 'REFUND_DUE' | 'PARTLY_REFUNDED' | 'REFUNDED'
export interface Balance { currency: string; total: number; paid: number; reversed: number; refunded: number; netPaid: number; due: number; refundDue: number; status: BalanceStatus }
export interface Receipt { id: string; receiptNumber: string; method: Method; provider: Provider | null; reference: string | null; amount: number; currency: string
  note: string | null; recordedBy: string | null; recordedAt: string; reversed: boolean }
export interface Adjustment { id: string; orderId: string; orderReference: string; kind: 'REVERSAL' | 'REFUND'; paymentId: string | null; receiptNumber: string | null
  amount: number; reason: string; refundMethod: Method | null; refundProvider: Provider | null; refundReference: string | null; status: 'PENDING' | 'APPROVED' | 'REJECTED'
  requestedBy: string; requestedAt: string; decidedBy: string | null; decidedAt: string | null; decisionNote: string | null }
export interface Ledger { orderId: string; reference: string; orderStatus: string; fulfilment: string; customerName: string; customerPhone: string; balance: Balance; payments: Receipt[]; adjustments: Adjustment[] }
export interface LedgerRow { orderId: string; reference: string; orderStatus: string; customerName: string; createdAt: string; balance: Balance }
export interface LedgerPage { items: LedgerRow[]; total: number; page: number; size: number }
export interface DaySummary { date: string; currency: string; cash: number; mobileMoney: number; received: number; payments: number; reversed: number; refunded: number; net: number; completedSales: number; completedOrders: number }
export interface CustomerPayments { balance: Balance; receipts: Receipt[] }

export const providers: Record<Provider, string> = { MPESA: 'M-Pesa', MIXX: 'Mixx by Yas', AIRTEL_MONEY: 'Airtel Money', HALOPESA: 'HaloPesa' }
export const statusLabels: Record<BalanceStatus, string> = { UNPAID: 'Unpaid', PART_PAID: 'Part paid', PAID: 'Paid', REFUND_DUE: 'Refund due', PARTLY_REFUNDED: 'Partly refunded', REFUNDED: 'Refunded' }
export const howPaid = (method: Method | null, provider: Provider | null) => method === 'CASH' ? 'Cash' : provider ? providers[provider] : 'Mobile money'
/** Orders that may still take a payment. The server applies the same rule. */
export const payable = ['CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP', 'READY_FOR_DELIVERY', 'OUT_FOR_DELIVERY', 'DELIVERED', 'COLLECTED']
export const dateTime = (value: string) => new Intl.DateTimeFormat('en-TZ', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Africa/Dar_es_Salaam' }).format(new Date(value))
