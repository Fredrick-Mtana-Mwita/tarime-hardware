import { useEffect, useState } from 'react'
import type { CartItem } from './store'
import { commercePost, orderError } from '../orders/api'
import type { Quote, Branch } from '../orders/api'

export function useCartQuote(items: CartItem[], branch?: Branch) {
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState<{ key: string; data?: Quote; error?: string }>({ key: '' })
  const selections = JSON.stringify(items.map(i => ({ variantId: i.variantId, quantity: i.quantity })))
  const key = selections + '|' + branch + '|' + attempt
  useEffect(() => {
    if (selections === '[]') return
    const controller = new AbortController()
    void commercePost<Quote>('/api/cart/quote', { items: JSON.parse(selections), branch }, {}, controller.signal)
      .then(data => { if (!controller.signal.aborted) setState({ key, data }) })
      .catch((error: unknown) => { if (!controller.signal.aborted) setState({ key, error: orderError(error) }) })
    return () => controller.abort()
  }, [selections, key, branch])
  return { data: state.key === key ? state.data : undefined, error: state.key === key ? state.error : undefined,
    retry: () => setAttempt(value => value + 1) }
}
