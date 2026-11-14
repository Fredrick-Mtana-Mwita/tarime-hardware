import { Canvas } from '@react-three/fiber'
import { Component, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { set3DEnabled, use3DEnabled, useReducedMotion, webglAvailable } from './preference'
import './three.css'

class Boundary extends Component<{ onError: () => void; children: ReactNode }, { failed: boolean }> {
  state = { failed: false }
  static getDerivedStateFromError() { return { failed: true } }
  componentDidCatch() { this.props.onError() }
  render() { return this.state.failed ? null : this.props.children }
}

/**
 * The only place a WebGL canvas is created. It draws on demand, pauses moving scenes while they are
 * off screen or the tab is hidden, and swaps to ordinary HTML when 3D is switched off, unsupported or lost.
 */
export function Scene3D({ label, animate = false, decorative = false, fallback, children }: {
  label: string; animate?: boolean; decorative?: boolean; fallback?: ReactNode; children: ReactNode
}) {
  const enabled = use3DEnabled()
  const reduced = useReducedMotion()
  const host = useRef<HTMLDivElement>(null)
  const [visible, setVisible] = useState(true)
  const [failure, setFailure] = useState<'' | 'lost' | 'error'>('')
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const element = host.current
    if (!element) return
    let onScreen = true
    const update = () => setVisible(onScreen && document.visibilityState === 'visible')
    const observer = new IntersectionObserver(entries => { onScreen = entries.some(entry => entry.isIntersecting); update() })
    observer.observe(element)
    document.addEventListener('visibilitychange', update)
    return () => { observer.disconnect(); document.removeEventListener('visibilitychange', update) }
  }, [])
  const supported = webglAvailable()
  const showCanvas = enabled && supported && !failure
  return <div ref={host} className="scene3d" data-state={showCanvas ? 'live' : 'fallback'}>
    {showCanvas ? <div className="scene3d-canvas" role="img" aria-label={label} aria-hidden={decorative || undefined}>
      <Boundary key={attempt} onError={() => setFailure('error')}>
        <Canvas frameloop={animate && visible && !reduced ? 'always' : 'demand'} dpr={[1, 1.75]} camera={{ fov: 40, near: 0.1, far: 80, position: [0, 1.4, 5] }}
          gl={{ antialias: true, powerPreference: 'default' }}
          onCreated={({ gl }) => gl.domElement.addEventListener('webglcontextlost', event => { event.preventDefault(); setFailure('lost') })}>
          {children}
        </Canvas>
      </Boundary>
    </div> : <div className="scene3d-fallback">
      {fallback}
      {!decorative && <p className="scene3d-status" role="status">
        {!enabled ? 'The 3D view is switched off.' : !supported ? 'This browser or device cannot show the 3D view. Everything is still available below.' : 'The 3D view stopped. Your selections are kept.'}
        {!enabled && <button type="button" onClick={() => set3DEnabled(true)}>Turn on 3D</button>}
        {enabled && supported && <button type="button" onClick={() => { setFailure(''); setAttempt(attempt + 1) }}>Reload 3D view</button>}
      </p>}
    </div>}
  </div>
}
