import { useEffect, useState, useSyncExternalStore } from 'react'
import type { CSSProperties, PointerEvent } from 'react'
import { assetUrl } from '../../shared/assetUrl'
import { exteriorIdeas, heroClips, interiorIdeas } from './media'
import './design.css'

const motionQuery = '(prefers-reduced-motion: reduce)'
const subscribeMotion = (notify: () => void) => {
  const query = matchMedia(motionQuery)
  query.addEventListener('change', notify)
  return () => query.removeEventListener('change', notify)
}
const readMotion = () => matchMedia(motionQuery).matches

/** Follows the mouse so a panel can lean towards it. Touch and pen input are ignored. */
function lean(event: PointerEvent<HTMLElement>) {
  if (event.pointerType !== 'mouse') return
  const box = event.currentTarget.getBoundingClientRect()
  event.currentTarget.style.setProperty('--dz-px', (((event.clientX - box.left) / box.width) * 2 - 1).toFixed(3))
  event.currentTarget.style.setProperty('--dz-py', (((event.clientY - box.top) / box.height) * 2 - 1).toFixed(3))
}
function settle(event: PointerEvent<HTMLElement>) {
  event.currentTarget.style.removeProperty('--dz-px')
  event.currentTarget.style.removeProperty('--dz-py')
}

/** Shows the copy saved in the project first and falls back to the generated original. */
function Picture({ local, remote, alt, width, height, eager }: { local: string; remote?: string; alt: string; width: number; height: number; eager?: boolean }) {
  const [useRemote, setUseRemote] = useState(false)
  return <img src={useRemote && remote ? remote : assetUrl(local)} alt={alt} width={width} height={height}
    loading={eager ? 'eager' : 'lazy'} onError={() => setUseRemote(true)} />
}

/** A still that drifts slowly, covered by the animation clip once one has been added to the project. */
function Clip({ clip, still }: { clip: (typeof heroClips)[keyof typeof heroClips]; still: boolean }) {
  const [ready, setReady] = useState(false)
  return <div className="dz-clip">
    <Picture local={clip.poster} remote={clip.posterRemote} alt={clip.alt} width={960} height={720} eager />
    {!still && <video className={ready ? 'ready' : ''} src={assetUrl(clip.video)} muted loop playsInline autoPlay preload="metadata"
      aria-hidden="true" tabIndex={-1} onCanPlay={() => setReady(true)} onError={() => setReady(false)} />}
  </div>
}

function InteriorRing({ reduced }: { reduced: boolean }) {
  const count = interiorIdeas.length
  const [turn, setTurn] = useState(0)
  const [held, setHeld] = useState(false)
  // Turns on its own, pauses while the pointer is over it, and stays still for visitors who turn off animations.
  const playing = !reduced
  const active = ((turn % count) + count) % count
  useEffect(() => {
    if (!playing || held) return
    const timer = window.setInterval(() => setTurn(current => current + 1), 3000)
    return () => window.clearInterval(timer)
  }, [playing, held])
  const ringStyle = { '--dz-angle': `${(-turn * 360) / count}deg`, '--dz-step': `${360 / count}deg` } as CSSProperties
  return <div className="dz-ring-wrap" role="region" aria-label="Interior ideas in a rotating 3D gallery"
    onMouseEnter={() => setHeld(true)} onMouseLeave={() => setHeld(false)}>
    <div className="dz-ring-scene">
      <ul className="dz-ring" style={ringStyle}>
        {interiorIdeas.map((idea, position) => <li key={idea.image} className={position === active ? 'active' : ''} style={{ '--dz-i': position } as CSSProperties}>
          <Picture local={idea.image} alt={idea.title} width={idea.width} height={idea.height} />
        </li>)}
      </ul>
    </div>
    <p className="dz-ring-caption" aria-live={playing ? 'off' : 'polite'}><strong>{interiorIdeas[active].title}</strong><span>{interiorIdeas[active].detail}</span></p>
  </div>
}

export function DesignPage() {
  const reduced = useSyncExternalStore(subscribeMotion, readMotion)
  const scrollTo = (id: string) => document.getElementById(id)?.scrollIntoView({ behavior: reduced ? 'auto' : 'smooth', block: 'start' })
  return <div className="dz-page">
    <section className="container"><div className="dz-hero">
      <p className="eyebrow">Interior and exterior design</p>
      <h1>See the space before you build it.</h1>
      <p className="dz-lead">Ceilings, feature walls, kitchens, lighting and outdoor finishes. Tarime Hardware helps you plan the look, then supplies the materials to make it real.</p>
      <div className="dz-hero-actions">
        <button type="button" className="button primary" onClick={() => scrollTo('dz-interior')}>Explore interiors →</button>
        <button type="button" className="button dz-ghost" onClick={() => scrollTo('dz-exterior')}>Explore exteriors →</button>
      </div>
      <div className="dz-stage" onPointerMove={lean} onPointerLeave={settle}>
        <button type="button" className="dz-panel dz-panel-left" onClick={() => scrollTo('dz-interior')}>
          <Clip clip={heroClips.interior} still={reduced} />
          <span className="dz-panel-label"><strong>Interior</strong><small>Ceilings · walls · kitchens · lighting</small></span>
        </button>
        <button type="button" className="dz-panel dz-panel-right" onClick={() => scrollTo('dz-exterior')}>
          <Clip clip={heroClips.exterior} still={reduced} />
          <span className="dz-panel-label"><strong>Exterior</strong><small>Roofing · paint · paving · outdoor living</small></span>
        </button>
      </div>
    </div></section>

    <section className="container section" id="dz-interior">
      <div className="section-heading"><div><p className="eyebrow">Interior design</p><h2>Walk around the ideas</h2></div></div>
      <p className="dz-intro">Turn the gallery to look at each idea. Tell us which one is closest to what you have in mind and we will help you choose the boards, panels, lights, tiles and paint for it.</p>
      <InteriorRing reduced={reduced} />
    </section>

    <section className="container section" id="dz-exterior">
      <div className="section-heading"><div><p className="eyebrow">Exterior design</p><h2>First impressions, built to last</h2></div></div>
      <p className="dz-intro">Roof profile, wall finish, paving and lighting decide how a home looks from the gate. Move your mouse over a concept to see it in depth.</p>
      <div className="dz-exterior-grid">
        {exteriorIdeas.map(idea => <figure key={idea.image} className="dz-tilt" onPointerMove={lean} onPointerLeave={settle}>
          <Picture local={idea.image} remote={idea.remote} alt={idea.alt} width={960} height={720} />
          <figcaption><strong>{idea.title}</strong><span>{idea.detail}</span></figcaption>
        </figure>)}
      </div>
    </section>

    <section className="container section">
      <div className="section-heading"><div><p className="eyebrow">Where we can help</p><h2>From the ceiling to the gate</h2></div><a href="#/shop">Browse all materials →</a></div>
      <div className="dz-services">
        <div><h3>Ceilings and lighting</h3><p>Gypsum board ceilings, cove lighting, spotlights and pendants.</p></div>
        <div><h3>Feature walls</h3><p>Fluted panels, wall mouldings, TV walls and decorative paint finishes.</p></div>
        <div><h3>Kitchens and floors</h3><p>Cabinet fittings, worktops, tiles and the plumbing behind them.</p></div>
        <div><h3>Exterior finishes</h3><p>Roofing, exterior paint, cladding, paving and outdoor lighting.</p></div>
      </div>
    </section>

    <section className="container section"><div className="dz-cta">
      <div><p className="eyebrow">Start your project</p><h2>Bring your idea, a photo or a sketch.</h2><p>Visit Tarime Hardware in Tarime and talk it through with us. We will help you turn it into a list of materials you can order.</p></div>
      <div className="dz-cta-actions"><a className="button primary" href="#/shop">Browse materials →</a></div>
    </div>
    <p className="dz-note">Pictures on this page are concept visuals for inspiration. They are not photographs of completed Tarime Hardware projects.</p></section>
  </div>
}
