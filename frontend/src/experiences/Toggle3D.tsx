import { set3DEnabled, use3DEnabled } from '../shared/three/preference'

/** Lets anyone switch every 3D view and effect off (or back on). The shop works fully either way. */
export function Toggle3D() {
  const enabled = use3DEnabled()
  return <button type="button" className="x3-toggle" aria-pressed={enabled} onClick={() => set3DEnabled(!enabled)}>3D views and effects: {enabled ? 'On' : 'Off'}</button>
}
