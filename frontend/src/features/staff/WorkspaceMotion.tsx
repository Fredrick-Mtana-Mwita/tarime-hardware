import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { MouseEvent } from 'react'
import { flushSync } from 'react-dom'

const reduced = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches
const themeKey = 'tarime.staff.inventory-theme'

export function WorkspaceTheme() {
  const [dark, setDark] = useState(() => {
    try { return localStorage.getItem(themeKey) === 'dark' } catch { return false }
  })
  const changing = useRef(false)
  useLayoutEffect(() => {
    document.querySelector('.staff-shell')?.setAttribute('data-theme', dark ? 'dark' : 'light')
  }, [dark])
  async function change(event: MouseEvent<HTMLButtonElement>) {
    if (changing.current) return
    const next = !dark
    const bounds = event.currentTarget.getBoundingClientRect()
    const x = event.detail ? event.clientX : bounds.x + bounds.width / 2
    const y = event.detail ? event.clientY : bounds.y + bounds.height / 2
    const update = () => {
      flushSync(() => setDark(next))
      try { localStorage.setItem(themeKey, next ? 'dark' : 'light') } catch { /* Theme remains usable without storage. */ }
    }
    if (reduced() || !document.startViewTransition) { update(); return }
    changing.current = true
    document.documentElement.classList.add('staff-theme-reveal')
    const transition = document.startViewTransition(update)
    try {
      await transition.ready
      const radius = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y))
      await document.documentElement.animate({ clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${radius}px at ${x}px ${y}px)`] }, {
        duration: 620, easing: 'cubic-bezier(.65,0,.35,1)', pseudoElement: '::view-transition-new(root)',
      }).finished
    } catch { transition.skipTransition() }
    finally { await transition.finished.catch(() => {}); document.documentElement.classList.remove('staff-theme-reveal'); changing.current = false }
  }
  return <button className="workspace-theme-toggle" type="button" aria-label={dark ? 'Switch to light theme' : 'Switch to dark theme'} onClick={event => void change(event)}><span aria-hidden="true">{dark ? '☀' : '☾'}</span><span>{dark ? 'Light' : 'Dark'}</span></button>
}

export function NavigationGlow({ path }: { path: string }) {
  const marker = useRef<HTMLSpanElement>(null)
  useLayoutEffect(() => {
    const node = marker.current, nav = node?.parentElement
    if (!node || !nav) return
    let animation: Animation | undefined
    const place = () => {
      const link = nav.querySelector<HTMLElement>('a[aria-current="page"]')
      if (!link) { node.style.opacity = '0'; return }
      const rect = link.getBoundingClientRect()
      const previous = getComputedStyle(node).transform
      const transform = `translate(${link.offsetLeft}px,${link.offsetTop}px)`
      const visible = node.style.opacity === '1'
      node.style.width = `${rect.width}px`; node.style.height = `${rect.height}px`
      node.style.transform = transform; node.style.opacity = '1'
      animation?.cancel()
      if (visible && !reduced()) animation = node.animate([{ transform: previous }, { transform }], { duration: 420, easing: 'cubic-bezier(.22,1,.36,1)' })
    }
    place()
    const observer = new ResizeObserver(place); observer.observe(nav)
    return () => { observer.disconnect(); animation?.cancel() }
  }, [path])
  return <span className="staff-nav-indicator" aria-hidden="true" ref={marker} />
}

export function AnimatedNumber({ value }: { value: number }) {
  const ref = useRef<HTMLSpanElement>(null)
  useEffect(() => {
    const node = ref.current
    if (!node || reduced()) return
    let frame = 0
    const start = performance.now()
    const tick = (time: number) => {
      const progress = Math.min(1, (time - start) / 800)
      node.textContent = new Intl.NumberFormat('en-TZ').format(Math.round(value * (1 - Math.pow(1 - progress, 3))))
      if (progress < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [value])
  return <><span aria-hidden="true" ref={ref}>{value}</span><span className="sr-only">{value}</span></>
}
