import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { use3DEnabled } from '../../shared/three/preference'
import '../experiences.css'

const FloatingProducts = lazy(() => import('./FloatingProducts').then(module => ({ default: module.FloatingProducts })))

/** Storefront banner. The 3D code is downloaded only when the banner is about to scroll into view. */
export function ExploreBanner() {
  const enabled = use3DEnabled()
  const section = useRef<HTMLElement>(null)
  const [near, setNear] = useState(false)
  useEffect(() => {
    const element = section.current
    if (!element) return
    const observer = new IntersectionObserver(entries => { if (entries.some(entry => entry.isIntersecting)) { setNear(true); observer.disconnect() } }, { rootMargin: '300px' })
    observer.observe(element)
    return () => observer.disconnect()
  }, [])
  return <section ref={section} className="container section"><div className="x3-banner">
    <div className="x3-banner-copy"><p className="eyebrow">3D experience</p><h2>See it before you buy it.</h2>
      <p>Turn products around, try tiles, paint and lights in a room, and walk a showroom of the shop's materials.</p>
      <a className="button primary" href="#/3d">Open the 3D experience →</a></div>
    <div className="x3-banner-art" aria-hidden="true">{enabled && near && <Suspense fallback={null}><FloatingProducts /></Suspense>}</div>
  </div></section>
}
