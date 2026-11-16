import { useEffect, useState } from 'react'

/**
 * Selcom-style "scroll back" header: hides when the visitor scrolls down past the header,
 * comes back as soon as they scroll up. Stays visible while `keepVisible` is true
 * (a menu or panel is open) or while something inside the header has focus.
 */
export function useScrollBackHeader(keepVisible: boolean) {
  const [hidden, setHidden] = useState(false)
  useEffect(() => {
    let last = window.scrollY, ticking = false
    const update = () => {
      ticking = false
      const y = window.scrollY, header = document.querySelector('.site-header')
      const focused = !!header && header.contains(document.activeElement)
      if (y < 160 || focused) setHidden(false)
      else if (y > last + 6) setHidden(true)
      else if (y < last - 6) setHidden(false)
      if (Math.abs(y - last) > 6 || y < 160) last = y
    }
    const onScroll = () => { if (!ticking) { ticking = true; requestAnimationFrame(update) } }
    const onFocus = () => update()
    window.addEventListener('scroll', onScroll, { passive: true })
    document.addEventListener('focusin', onFocus)
    return () => { window.removeEventListener('scroll', onScroll); document.removeEventListener('focusin', onFocus) }
  }, [])
  const isHidden = hidden && !keepVisible
  useEffect(() => { document.documentElement.classList.toggle('header-hidden', isHidden) }, [isHidden])
  return isHidden
}
