import { useEffect, useRef } from 'react'

/** Decorative tilt is pointer-only; forms and text controls never follow the cursor. */
export function useWorkspaceDepth() {
  const root = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const shell = root.current
    if (!shell) return
    const preference = matchMedia('(prefers-reduced-motion: reduce)')
    const pointer = matchMedia('(hover: hover) and (pointer: fine)')
    let active: HTMLElement | null = null, frame = 0
    const reset = () => { cancelAnimationFrame(frame); active?.style.removeProperty('--tilt-x'); active?.style.removeProperty('--tilt-y'); active = null }
    const move = (event: PointerEvent) => {
      if (preference.matches || !pointer.matches || event.pointerType === 'touch') return
      const card = (event.target as HTMLElement).closest<HTMLElement>('.workspace-stats article,.inventory-card,.overview-branches>div>a')
      if (active !== card) reset()
      if (!card || !shell.contains(card)) return
      active = card
      const rect = card.getBoundingClientRect()
      const x = Math.max(-1, Math.min(1, (event.clientX - rect.left) / rect.width * 2 - 1))
      const y = Math.max(-1, Math.min(1, (event.clientY - rect.top) / rect.height * 2 - 1))
      cancelAnimationFrame(frame)
      frame = requestAnimationFrame(() => { card.style.setProperty('--tilt-x', `${-y * 4}deg`); card.style.setProperty('--tilt-y', `${x * 5}deg`) })
    }
    shell.addEventListener('pointermove', move); shell.addEventListener('pointerleave', reset)
    preference.addEventListener('change', reset); pointer.addEventListener('change', reset)
    return () => { reset(); shell.removeEventListener('pointermove', move); shell.removeEventListener('pointerleave', reset); preference.removeEventListener('change', reset); pointer.removeEventListener('change', reset) }
  }, [])
  return root
}

