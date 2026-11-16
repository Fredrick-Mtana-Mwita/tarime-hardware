import { useLayoutEffect, useRef, useState } from 'react'

/** Selcom-style reveal: fade in and rise 28px over 0.6s the first time the element scrolls into view. */
export const revealProps = {
  initial: { opacity: 0, y: 28 },
  whileInView: { opacity: 1, y: 0 },
  viewport: { once: true, amount: 0.15 },
} as const

export const revealTransition = (delay = 0) => ({ duration: 0.6, ease: 'easeOut' as const, delay })

/** Delay by grid column so each row of cards or tiles reads left to right, whatever the grid width. */
export function useColumnDelay<T extends HTMLElement>() {
  const ref = useRef<T>(null)
  const [delay, setDelay] = useState(0)
  useLayoutEffect(() => {
    const el = ref.current, grid = el?.parentElement
    if (!el || !grid) return
    const box = grid.getBoundingClientRect(), left = el.getBoundingClientRect().left - box.left
    if (box.width) setDelay(Math.min(3, Math.round(left / box.width * 4)) * 0.08)
  }, [])
  return [ref, delay] as const
}

