import { useEffect, useRef, useState } from 'react'
import type { ThreeEvent } from '@react-three/fiber'
import { LoadState } from '../../features/catalogue/LoadState'
import { money } from '../../features/catalogue/format'
import { OrbitButtons } from '../../shared/three/OrbitButtons'
import { OrbitRig } from '../../shared/three/OrbitRig'
import type { OrbitApi, OrbitLimits, View } from '../../shared/three/orbit'
import { useReducedMotion } from '../../shared/three/preference'
import { Scene3D } from '../../shared/three/Scene3D'
import { BuyBox } from '../BuyBox'
import type { RoomFinish, RoomSurface } from '../manifest'
import { ProductModel } from '../models'
import { usePainted } from '../textures'
import type { Painted } from '../textures'
import { useModelProducts } from '../useModelProducts'
import type { ModelProduct } from '../useModelProducts'
import '../experiences.css'

type TemplateId = 'living' | 'bathroom'
interface Template { name: string; width: number; depth: number; height: number; home: View }
const templates: Record<TemplateId, Template> = {
  living: { name: 'Living room', width: 5, depth: 4, height: 2.7, home: { target: [0, 1.0, 0], azimuth: 0.38, polar: 1.28, distance: 7.6 } },
  bathroom: { name: 'Bathroom', width: 3, depth: 2.4, height: 2.5, home: { target: [0, 1.0, 0], azimuth: 0.38, polar: 1.28, distance: 5.4 } },
}
const limits: OrbitLimits = { minDistance: 2.6, maxDistance: 10, minPolar: 0.8, maxPolar: 1.5, minAzimuth: -0.95, maxAzimuth: 0.95 }
const surfaces: { id: RoomSurface; name: string; none: string }[] = [
  { id: 'floor', name: 'Floor', none: 'Bare screed' },
  { id: 'wall', name: 'Walls', none: 'Unpainted plaster' },
  { id: 'light', name: 'Lighting', none: 'Daylight only' },
]
type Choice = Record<RoomSurface, string | null>
const empty: Choice = { floor: null, wall: null, light: null }
const storageKey = 'tarime.room.v1'
const half = Math.PI / 2

// The room is remembered for this browser tab only, by product SKU. Prices are never stored.
function restore(): { template: TemplateId; choice: Choice } {
  try {
    const saved: unknown = JSON.parse(sessionStorage.getItem(storageKey) || 'null')
    if (saved && typeof saved === 'object') {
      const value = saved as Record<string, unknown>
      const sku = (entry: unknown) => typeof entry === 'string' && entry.length <= 80 ? entry : null
      return { template: value.template === 'bathroom' ? 'bathroom' : 'living', choice: { floor: sku(value.floor), wall: sku(value.wall), light: sku(value.light) } }
    }
  } catch { /* start with an empty room */ }
  return { template: 'living', choice: empty }
}

function Textured({ kind, repeatX, repeatY, roughness }: { kind: Painted; repeatX: number; repeatY: number; roughness: number }) {
  const map = usePainted(kind, repeatX, repeatY)
  return <meshStandardMaterial map={map} roughness={roughness} />
}

function Furniture({ id, room }: { id: TemplateId; room: Template }) {
  const back = -room.depth / 2
  if (id === 'living') return <group>
    <mesh position={[0, 0.21, back + 0.55]}><boxGeometry args={[2.1, 0.42, 0.9]} /><meshStandardMaterial color="#d6cfc2" roughness={0.9} /></mesh>
    <mesh position={[0, 0.66, back + 0.18]}><boxGeometry args={[2.1, 0.5, 0.22]} /><meshStandardMaterial color="#cfc7b9" roughness={0.9} /></mesh>
    {[-1.16, 1.16].map(x => <mesh key={x} position={[x, 0.33, back + 0.55]}><boxGeometry args={[0.22, 0.66, 0.9]} /><meshStandardMaterial color="#cfc7b9" roughness={0.9} /></mesh>)}
    <mesh position={[0, 0.17, 0.35]}><cylinderGeometry args={[0.48, 0.48, 0.06, 40]} /><meshStandardMaterial color="#8d623a" roughness={0.5} /></mesh>
    <mesh position={[0, 0.07, 0.35]}><cylinderGeometry args={[0.1, 0.16, 0.14, 24]} /><meshStandardMaterial color="#2a2f35" roughness={0.6} /></mesh>
  </group>
  return <group>
    <mesh position={[-0.7, 0.4, back + 0.26]}><boxGeometry args={[0.9, 0.8, 0.46]} /><meshStandardMaterial color="#f4f4f1" roughness={0.5} /></mesh>
    <mesh position={[-0.7, 0.82, back + 0.27]}><boxGeometry args={[0.96, 0.04, 0.5]} /><meshStandardMaterial color="#2a2f35" roughness={0.35} /></mesh>
    <mesh position={[-0.7, 1.45, back + 0.012]}><planeGeometry args={[0.7, 0.8]} /><meshStandardMaterial color="#e3ecef" metalness={0.25} roughness={0.12} /></mesh>
    <mesh position={[room.width / 2 - 0.42, 0.28, 0.1]}><boxGeometry args={[0.76, 0.56, 1.6]} /><meshStandardMaterial color="#f7f7f5" roughness={0.3} /></mesh>
    <mesh position={[room.width / 2 - 0.42, 0.565, 0.1]}><boxGeometry args={[0.6, 0.01, 1.44]} /><meshStandardMaterial color="#cfe3ea" roughness={0.1} /></mesh>
  </group>
}

function Room({ id, floor, wall, light, tileMetres, onPick }: { id: TemplateId; floor?: RoomFinish; wall?: RoomFinish; light?: RoomFinish; tileMetres: number; onPick: (surface: RoomSurface) => void }) {
  const room = templates[id], { width, depth, height } = room
  const pick = (surface: RoomSurface) => (event: ThreeEvent<MouseEvent>) => { if (event.delta > 6) return; event.stopPropagation(); onPick(surface) }
  const plain = wall === 'gloss-white' ? { color: '#fbfbf8', roughness: 0.42 } : { color: '#d9ccb9', roughness: 0.95 }
  const lamp = light ? 1 : 0
  return <group>
    <hemisphereLight args={['#ffffff', '#b8b0a2', light ? 0.9 : 1.5]} />
    <directionalLight position={[3, 5, 6]} intensity={light ? 1 : 1.9} />
    <pointLight position={[0, height - 0.95, 0.2]} intensity={lamp * 16} color="#ffd9a0" distance={14} decay={2} />
    <mesh rotation={[-half, 0, 0]} onClick={pick('floor')}><planeGeometry args={[width, depth]} />
      {floor === 'marble' ? <Textured kind="marble" repeatX={width / (tileMetres * 2)} repeatY={depth / (tileMetres * 2)} roughness={0.16} /> : <meshStandardMaterial color="#a9a59c" roughness={0.95} />}
    </mesh>
    <mesh position={[0, height / 2, -depth / 2]} onClick={pick('wall')}><planeGeometry args={[width, height]} />
      {wall === 'glazed' ? <Textured kind="glazed" repeatX={width / 0.4} repeatY={height / 0.4} roughness={0.34} /> : <meshStandardMaterial {...plain} />}
    </mesh>
    <mesh position={[-width / 2, height / 2, 0]} rotation={[0, half, 0]} onClick={pick('wall')}><planeGeometry args={[depth, height]} /><meshStandardMaterial {...plain} /></mesh>
    <mesh position={[width / 2, height / 2, 0]} rotation={[0, -half, 0]} onClick={pick('wall')}><planeGeometry args={[depth, height]} /><meshStandardMaterial {...plain} /></mesh>
    <mesh position={[0, height, 0]} rotation={[half, 0, 0]} onClick={pick('light')}><planeGeometry args={[width, depth]} /><meshStandardMaterial color="#f3f1ec" roughness={0.9} /></mesh>
    {light === 'pendant' && <group position={[0, height - 1.4 * 0.85, 0.2]} scale={0.85} onClick={pick('light')}><ProductModel kind="pendant" /></group>}
    {light === 'bulb' && <group position={[0, height, 0.2]} onClick={pick('light')}>
      <mesh position={[0, -0.25, 0]}><cylinderGeometry args={[0.006, 0.006, 0.5, 8]} /><meshStandardMaterial color="#16191c" /></mesh>
      <group position={[0, -0.46, 0]} rotation={[Math.PI, 0, 0]} scale={0.4}><ProductModel kind="filament-bulb" /></group>
    </group>}
    <Furniture id={id} room={room} />
  </group>
}

/** FR-3D-02. Room templates whose floor, walls and lighting preview real catalogue products. */
export function RoomPage() {
  const { items, error, retry } = useModelProducts()
  const [saved] = useState(restore)
  const [template, setTemplate] = useState<TemplateId>(saved.template)
  const [choice, setChoice] = useState<Choice>(saved.choice)
  const [active, setActive] = useState<RoomSurface | null>(null)
  const api = useRef<OrbitApi | null>(null)
  const reduced = useReducedMotion()
  useEffect(() => {
    try { sessionStorage.setItem(storageKey, JSON.stringify({ template, ...choice })) } catch { /* continuity is optional */ }
  }, [template, choice])

  const options = (surface: RoomSurface) => (items || []).filter(item => item.entry.room?.surface === surface)
  // A saved SKU that is no longer published simply falls back to "none".
  const chosen = (surface: RoomSurface): ModelProduct | undefined => options(surface).find(item => item.product.sku === choice[surface])
  const finish = (surface: RoomSurface) => chosen(surface)?.entry.room?.finish
  const selected = surfaces.flatMap(surface => { const item = chosen(surface.id); return item ? [{ surface, item }] : [] })
  function pick(surface: RoomSurface) {
    setActive(surface)
    document.querySelector<HTMLInputElement>(`#room-${surface} input:checked`)?.focus()
  }
  const room = templates[template]
  return <div className="container x3-page">
    <nav className="breadcrumbs" aria-label="Breadcrumb"><a href="#/">Home</a><span>/</span><a href="#/3d">3D experience</a><span>/</span><span>Room visualizer</span></nav>
    <p className="eyebrow">Room visualizer</p><h1>Try finishes in a room</h1>
    <p className="intro">Choose a room, then pick a floor, a wall finish and a light from the shop's catalogue. You can also press a surface in the picture to jump to its options.</p>
    <div className="x3-split">
      <section aria-label="Room preview" className="x3-stage">
        <div className="x3-canvas x3-canvas-room">
          <Scene3D label={`${room.name} preview. Floor: ${chosen('floor')?.product.name || 'bare screed'}. Walls: ${chosen('wall')?.product.name || 'unpainted plaster'}. Lighting: ${chosen('light')?.product.name || 'daylight only'}.`}
            fallback={<p className="x3-fallback-text">The room picture is not available, but you can still choose products in the lists and add them to your cart.</p>}>
            <Room id={template} floor={finish('floor')} wall={finish('wall')} light={finish('light')} tileMetres={chosen('floor')?.entry.room?.tileMetres ?? 0.6} onPick={pick} />
            <OrbitRig apiRef={api} home={room.home} limits={limits} instant={reduced} composedFor={1.33} />
          </Scene3D>
        </div>
        <OrbitButtons api={api} subject="the room" />
        <p className="x3-note">Colours, lighting and room dimensions are illustrative. Check real samples in the shop before you buy.</p>
      </section>
      <section className="x3-panel" aria-label="Room options">
        <fieldset className="x3-options x3-inline"><legend>Room</legend>
          {(Object.keys(templates) as TemplateId[]).map(id => <label key={id} className={id === template ? 'selected' : ''}>
            <input type="radio" name="template" checked={id === template} onChange={() => setTemplate(id)} /><span><strong>{templates[id].name}</strong><small>{templates[id].width} × {templates[id].depth} m</small></span>
          </label>)}
        </fieldset>
        {!items ? <LoadState error={error} retry={retry} /> : surfaces.map(surface => <fieldset key={surface.id} id={`room-${surface.id}`} className={`x3-options ${active === surface.id ? 'active' : ''}`}>
          <legend>{surface.name}</legend>
          <label className={!chosen(surface.id) ? 'selected' : ''}>
            <input type="radio" name={surface.id} checked={!chosen(surface.id)} onChange={() => setChoice({ ...choice, [surface.id]: null })} /><span><strong>{surface.none}</strong><small>No product</small></span>
          </label>
          {options(surface.id).map(({ product, entry }) => <label key={product.id} className={choice[surface.id] === product.sku ? 'selected' : ''}>
            <input type="radio" name={surface.id} checked={choice[surface.id] === product.sku} onChange={() => setChoice({ ...choice, [surface.id]: product.sku })} />
            <span><strong>{product.name}</strong><small>{entry.room?.scaleConfirmed ? `Drawn at ${Math.round((entry.room.tileMetres ?? 0) * 100)} cm per tile` : 'Size and colour approximate'}</small></span>
            <span className="x3-option-price">{money(product.price, product.currency)}<small>per {product.unit}</small></span>
          </label>)}
        </fieldset>)}
        <button type="button" className="button secondary" onClick={() => { setChoice(empty); setActive(null); api.current?.reset() }}>Reset room</button>
      </section>
    </div>
    <section className="section" aria-labelledby="room-products">
      <h2 id="room-products">Products in this room</h2>
      {selected.length ? <ul className="x3-buy-list">{selected.map(({ surface, item }) => <li key={surface.id}><p className="eyebrow">{surface.name}</p><BuyBox key={item.product.id} productId={item.product.id} compact /></li>)}</ul>
        : <p className="intro">Nothing selected yet. Choose a floor, wall finish or light above and it will be listed here with its price.</p>}
      <p className="x3-note">This picture is not a measured estimate. Ask the shop to work out quantities from your room's real measurements.</p>
    </section>
  </div>
}
