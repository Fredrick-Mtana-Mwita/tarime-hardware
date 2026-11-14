import { useEffect, useRef, useState } from 'react'
import type { ThreeEvent } from '@react-three/fiber'
import { assetUrl } from '../../shared/assetUrl'
import { LoadState } from '../../features/catalogue/LoadState'
import { money } from '../../features/catalogue/format'
import { OrbitButtons } from '../../shared/three/OrbitButtons'
import { OrbitRig } from '../../shared/three/OrbitRig'
import type { OrbitApi, OrbitLimits, View } from '../../shared/three/orbit'
import { useReducedMotion } from '../../shared/three/preference'
import { Scene3D } from '../../shared/three/Scene3D'
import { BuyBox } from '../BuyBox'
import { illustrativeNote, zones } from '../manifest'
import type { ZoneId } from '../manifest'
import { BlobShadow, ProductModel } from '../models'
import { useModelProducts } from '../useModelProducts'
import type { ModelProduct } from '../useModelProducts'
import '../experiences.css'

const zoneX: Record<ZoneId, number> = { building: -4.8, finishes: -1.6, electrical: 1.6, tools: 4.8 }
const viewpoints: { id: 'all' | ZoneId; name: string; view: View }[] = [
  { id: 'all', name: 'Whole showroom', view: { target: [0, 0.9, -1.6], azimuth: 0, polar: 1.2, distance: 11.2 } },
  ...zones.map(zone => ({ id: zone.id, name: zone.name, view: { target: [zoneX[zone.id], 1.05, -1.8] as [number, number, number], azimuth: zoneX[zone.id] * 0.035, polar: 1.27, distance: 6.4 } })),
]
const limits: OrbitLimits = { minDistance: 3.2, maxDistance: 14.5, minPolar: 0.9, maxPolar: 1.5, minAzimuth: -0.75, maxAzimuth: 0.75 }
const half = Math.PI / 2

interface Display extends ModelProduct { x: number; z: number; plinth: number }
function arrange(items: ModelProduct[]): Display[] {
  return zones.flatMap(zone => {
    const group = items.filter(item => item.entry.zone === zone.id)
    const columns = Math.ceil(group.length / 2)
    return group.map((item, index) => {
      const front = index % 2 === 0
      return { ...item, x: zoneX[zone.id] + (Math.floor(index / 2) - (columns - 1) / 2) * 1.2 + (front ? 0 : 0.3), z: front ? -1.2 : -2.4, plinth: front ? 0.55 : 0.95 }
    })
  })
}

function Stand({ display, selected, onSelect }: { display: Display; selected: boolean; onSelect: (id: string) => void }) {
  const select = (event: ThreeEvent<MouseEvent>) => { if (event.delta > 6) return; event.stopPropagation(); onSelect(display.product.id) }
  const hover = (pointer: boolean) => (event: ThreeEvent<PointerEvent>) => { event.stopPropagation(); (event.nativeEvent.target as HTMLElement).style.cursor = pointer ? 'pointer' : '' }
  return <group position={[display.x, 0, display.z]} onClick={select} onPointerOver={hover(true)} onPointerOut={hover(false)}>
    <BlobShadow size={1.6} />
    <mesh position={[0, display.plinth / 2, 0]}><boxGeometry args={[0.86, display.plinth, 0.86]} /><meshStandardMaterial color={selected ? '#f2a900' : '#f7f7f5'} roughness={0.6} /></mesh>
    <group position={[0, display.plinth, 0]} scale={0.72}><ProductModel kind={display.entry.kind} modelUrl={display.entry.modelUrl} /></group>
    <mesh position={[0, display.plinth + 1.28, 0]} rotation={[Math.PI, 0, 0]} scale={selected ? 1.5 : 1}><coneGeometry args={[0.08, 0.17, 4]} /><meshStandardMaterial color="#f2a900" emissive="#f2a900" emissiveIntensity={0.7} /></mesh>
  </group>
}

function Hall({ displays, selected, onSelect }: { displays: Display[]; selected: string; onSelect: (id: string) => void }) {
  return <group>
    <hemisphereLight args={['#ffffff', '#a8a297', 1.5]} />
    <directionalLight position={[2, 7, 6]} intensity={2.1} />
    <directionalLight position={[-6, 4, 2]} intensity={0.6} />
    <mesh rotation={[-half, 0, 0]} position={[0, 0, 2]}><planeGeometry args={[30, 16]} /><meshStandardMaterial color="#d5cfc4" roughness={0.55} /></mesh>
    <mesh position={[0, 2.5, -3.4]}><planeGeometry args={[30, 5]} /><meshStandardMaterial color="#22262b" roughness={0.9} /></mesh>
    <mesh position={[0, 2.9, -3.38]}><boxGeometry args={[30, 0.12, 0.04]} /><meshStandardMaterial color="#f2a900" roughness={0.5} /></mesh>
    {zones.map(zone => <mesh key={zone.id} rotation={[-half, 0, 0]} position={[zoneX[zone.id], 0.004, -1.8]}><planeGeometry args={[2.9, 2.9]} /><meshStandardMaterial color="#c8c1b4" roughness={0.6} /></mesh>)}
    {displays.map(display => <Stand key={display.product.id} display={display} selected={display.product.id === selected} onSelect={onSelect} />)}
  </group>
}

/** FR-3D-04. Guided viewpoints with bounded camera movement, selectable displays and an ordinary product list. */
export function ShowroomPage() {
  const { items, error, retry } = useModelProducts()
  const [viewpoint, setViewpoint] = useState(0)
  const [selected, setSelected] = useState('')
  const api = useRef<OrbitApi | null>(null)
  const reduced = useReducedMotion()
  const displays = items ? arrange(items) : []
  useEffect(() => { api.current?.goTo(viewpoints[viewpoint].view) }, [viewpoint])
  function show(item: ModelProduct) {
    setSelected(item.product.id)
    setViewpoint(viewpoints.findIndex(point => point.id === item.entry.zone))
    document.getElementById('showroom-stage')?.scrollIntoView({ behavior: reduced ? 'auto' : 'smooth', block: 'start' })
  }
  const step = (change: number) => setViewpoint((viewpoint + change + viewpoints.length) % viewpoints.length)
  return <div className="container x3-page">
    <nav className="breadcrumbs" aria-label="Breadcrumb"><a href="#/">Home</a><span>/</span><a href="#/3d">3D experience</a><span>/</span><span>Showroom</span></nav>
    <p className="eyebrow">Virtual showroom</p><h1>Walk the showroom</h1>
    <p className="intro">Move between the four areas, then press a display to see its price and add it to your cart. Every product is also in the list under the picture.</p>
    <div className="x3-split" id="showroom-stage">
      <section aria-label="Showroom" className="x3-stage">
        <div className="x3-canvas x3-canvas-hall">
          <Scene3D label={`Showroom, ${viewpoints[viewpoint].name} view, with ${displays.length} product displays. The same products are listed below.`}
            fallback={<p className="x3-fallback-text">The showroom picture is not available. Use the product list below; it has every product on display.</p>}>
            <Hall displays={displays} selected={selected} onSelect={setSelected} />
            <OrbitRig apiRef={api} home={viewpoints[0].view} limits={limits} instant={reduced} composedFor={1.6} />
          </Scene3D>
        </div>
        <div className="x3-viewpoints" role="group" aria-label="Showroom viewpoints">
          <button type="button" aria-label="Previous viewpoint" onClick={() => step(-1)}>←</button>
          {viewpoints.map((point, index) => <button type="button" key={point.id} aria-pressed={index === viewpoint} onClick={() => setViewpoint(index)}>{point.name}</button>)}
          <button type="button" aria-label="Next viewpoint" onClick={() => step(1)}>→</button>
        </div>
        <OrbitButtons api={api} subject="the showroom" />
        <p className="x3-note">{illustrativeNote}</p>
      </section>
      <section className="x3-panel" aria-label="Selected display" aria-live="polite">
        {selected ? <BuyBox key={selected} productId={selected} /> : <><h2>Select a display</h2><p className="intro">Press a product on its stand, or choose “Show in showroom” in the list below.</p></>}
      </section>
    </div>
    <section className="section" aria-labelledby="showroom-list">
      <h2 id="showroom-list">Everything in the showroom</h2>
      {!items ? <LoadState error={error} retry={retry} /> : items.length ? zones.map(zone => {
        const group = items.filter(item => item.entry.zone === zone.id)
        return group.length ? <div key={zone.id} className="x3-zone"><h3>{zone.name}</h3><ul className="x3-cards">{group.map(item => <li key={item.product.id}>
          <a href={`#/product/${item.product.id}`}><img src={assetUrl(item.product.imagePath || '/images/store-shelves.jpg')} alt="" loading="lazy" />
            <span><strong>{item.product.name}</strong><small>{money(item.product.price, item.product.currency)} per {item.product.unit} · {item.product.inStock ? 'In stock' : 'Out of stock'}</small></span></a>
          <button type="button" onClick={() => show(item)}>Show in showroom</button>
        </li>)}</ul></div> : null
      }) : <p>No products are on display yet. <a className="text-link" href="#/shop">Browse the catalogue</a>.</p>}
    </section>
  </div>
}
