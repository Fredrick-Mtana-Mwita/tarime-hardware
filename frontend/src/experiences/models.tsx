import { useEffect, useMemo, useState } from 'react'
import type { ComponentType } from 'react'
import { useThree } from '@react-three/fiber'
import { Box3, BoxGeometry, DoubleSide, PlaneGeometry, Vector3 } from 'three'
import type { Material, Mesh, Object3D, Texture } from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import type { ModelKind } from './manifest'
import { usePainted } from './textures'

// Illustrative product shapes built from simple solids. Each stands on y = 0 and fits in about one metre.
const steel = { color: '#c3c7cc', metalness: 0.45, roughness: 0.38 }
const brass = { color: '#d2a84e', metalness: 0.4, roughness: 0.36 }
const half = Math.PI / 2

function CementBag() {
  const map = usePainted('bag')
  const geometry = useMemo(() => {
    const sack = new BoxGeometry(1.1, 0.3, 0.66, 14, 4, 10), position = sack.attributes.position
    for (let index = 0; index < position.count; index++) {
      const x = position.getX(index) / 0.55, z = position.getZ(index) / 0.33
      position.setY(index, position.getY(index) * (0.3 + 0.7 * Math.sqrt(Math.max(0, (1 - x ** 4) * (1 - z ** 4)))))
    }
    sack.computeVertexNormals()
    return sack
  }, [])
  return <mesh geometry={geometry} position={[0, 0.16, 0]} rotation={[0, 0.35, 0]}><meshStandardMaterial map={map} roughness={0.95} /></mesh>
}

function PaintTin() {
  return <group>
    <mesh position={[0, 0.31, 0]}><cylinderGeometry args={[0.34, 0.34, 0.62, 40]} /><meshStandardMaterial {...steel} /></mesh>
    <mesh position={[0, 0.33, 0]}><cylinderGeometry args={[0.344, 0.344, 0.4, 40, 1, true]} /><meshStandardMaterial color="#fbfbf8" roughness={0.45} /></mesh>
    <mesh position={[0, 0.19, 0]}><cylinderGeometry args={[0.347, 0.347, 0.07, 40, 1, true]} /><meshStandardMaterial color="#f2a900" roughness={0.5} /></mesh>
    <mesh position={[0, 0.63, 0]}><cylinderGeometry args={[0.3, 0.3, 0.02, 40]} /><meshStandardMaterial color="#aab0b6" metalness={0.45} roughness={0.34} /></mesh>
    <mesh position={[0, 0.62, 0]} rotation={[half, 0, 0]}><torusGeometry args={[0.335, 0.018, 10, 48]} /><meshStandardMaterial {...steel} /></mesh>
    <mesh position={[0, 0.5, 0]}><torusGeometry args={[0.37, 0.012, 8, 40, Math.PI]} /><meshStandardMaterial {...steel} /></mesh>
  </group>
}

function TileStack() {
  const map = usePainted('marble', 0.5, 0.5)
  return <group>{[0, 1, 2].map(layer => <mesh key={layer} position={[layer * 0.03 - 0.03, 0.022 + layer * 0.046, layer * 0.02]} rotation={[0, layer * 0.12, 0]}>
    <boxGeometry args={[0.9, 0.04, 0.9]} /><meshStandardMaterial map={map} roughness={0.22} />
  </mesh>)}</group>
}

function WallTile() {
  const map = usePainted('glazed')
  return <group rotation={[-0.2, 0, 0]}>
    <mesh position={[0, 0.5, 0]}><boxGeometry args={[0.95, 0.95, 0.04]} /><meshStandardMaterial map={map} roughness={0.18} /></mesh>
    <mesh position={[0, 0.32, -0.16]} rotation={[0.5, 0, 0]}><boxGeometry args={[0.5, 0.62, 0.03]} /><meshStandardMaterial color="#8a8f96" roughness={0.8} /></mesh>
  </group>
}

function FilamentBulb() {
  const glass = { color: '#ffd98a', transparent: true, opacity: 0.4, roughness: 0.05, metalness: 0.1 }
  return <group>
    <mesh position={[0, 0.08, 0]}><cylinderGeometry args={[0.05, 0.03, 0.06, 16]} /><meshStandardMaterial color="#2a2f35" roughness={0.6} /></mesh>
    <mesh position={[0, 0.2, 0]}><cylinderGeometry args={[0.1, 0.085, 0.2, 24]} /><meshStandardMaterial {...steel} /></mesh>
    {[0.14, 0.2, 0.26].map(y => <mesh key={y} position={[0, y, 0]} rotation={[half, 0, 0]}><torusGeometry args={[0.098, 0.012, 8, 28]} /><meshStandardMaterial {...steel} /></mesh>)}
    <mesh position={[0, 0.62, 0]}><torusGeometry args={[0.1, 0.012, 8, 32]} /><meshStandardMaterial color="#ffb347" emissive="#ff9a1f" emissiveIntensity={2.4} /></mesh>
    <mesh position={[0, 0.44, 0]}><cylinderGeometry args={[0.012, 0.012, 0.28, 8]} /><meshStandardMaterial color="#d7dade" roughness={0.3} /></mesh>
    <mesh position={[0, 0.37, 0]}><cylinderGeometry args={[0.13, 0.1, 0.16, 24]} /><meshStandardMaterial {...glass} /></mesh>
    <mesh position={[0, 0.64, 0]}><sphereGeometry args={[0.3, 32, 24]} /><meshStandardMaterial {...glass} /></mesh>
  </group>
}

function CableRoll() {
  return <group>
    <mesh position={[0, 0.15, 0]} rotation={[half, 0, 0]}><torusGeometry args={[0.36, 0.15, 20, 56]} /><meshStandardMaterial color="#b3261e" roughness={0.55} /></mesh>
    {[0, 2.1, 4.2].map(turn => <group key={turn} rotation={[0, turn, 0]}><mesh position={[0.36, 0.15, 0]}><torusGeometry args={[0.158, 0.018, 8, 24]} /><meshStandardMaterial color="#16191c" roughness={0.7} /></mesh></group>)}
  </group>
}

function Pendant() {
  return <group>
    <mesh position={[0, 1.38, 0]}><cylinderGeometry args={[0.09, 0.09, 0.04, 24]} /><meshStandardMaterial {...brass} /></mesh>
    <mesh position={[0, 1.13, 0]}><cylinderGeometry args={[0.008, 0.008, 0.46, 8]} /><meshStandardMaterial color="#16191c" /></mesh>
    <mesh position={[0, 0.5, 0]}><sphereGeometry args={[0.4, 40, 20, 0, Math.PI * 2, 0, half]} /><meshStandardMaterial {...brass} side={DoubleSide} /></mesh>
    <mesh position={[0, 0.56, 0]}><sphereGeometry args={[0.09, 16, 12]} /><meshStandardMaterial color="#fff2cc" emissive="#ffd27a" emissiveIntensity={2.2} /></mesh>
  </group>
}

function Hammer() {
  return <group rotation={[0, 0.5, 0]}>
    <mesh position={[0, 0.05, 0]} rotation={[0, 0, half]}><cylinderGeometry args={[0.035, 0.04, 0.9, 16]} /><meshStandardMaterial color="#b07a3c" roughness={0.7} /></mesh>
    <mesh position={[-0.3, 0.05, 0]} rotation={[0, 0, half]}><cylinderGeometry args={[0.046, 0.046, 0.32, 16]} /><meshStandardMaterial color="#16191c" roughness={0.9} /></mesh>
    <mesh position={[0.45, 0.06, 0]}><boxGeometry args={[0.1, 0.1, 0.34]} /><meshStandardMaterial {...steel} /></mesh>
    <mesh position={[0.45, 0.06, 0.2]} rotation={[half, 0, 0]}><cylinderGeometry args={[0.06, 0.06, 0.08, 20]} /><meshStandardMaterial {...steel} /></mesh>
    {[0.425, 0.475].map(x => <mesh key={x} position={[x, 0.085, -0.23]} rotation={[0.45, 0, 0]}><boxGeometry args={[0.035, 0.03, 0.18]} /><meshStandardMaterial {...steel} /></mesh>)}
    {[0.12, -0.12].map(angle => <group key={angle} position={[0, 0.03, 0.5]} rotation={[0, angle, 0]}>
      <mesh><boxGeometry args={[0.7, 0.03, 0.04]} /><meshStandardMaterial {...steel} /></mesh>
      <mesh position={[-0.2, 0, 0]}><boxGeometry args={[0.32, 0.045, 0.055]} /><meshStandardMaterial color="#b3261e" roughness={0.8} /></mesh>
    </group>)}
  </group>
}

function Roller() {
  return <group rotation={[0, -0.4, 0]}>
    <mesh position={[0, 0.02, -0.02]}><boxGeometry args={[0.8, 0.04, 0.5]} /><meshStandardMaterial color="#2a2f35" roughness={0.6} /></mesh>
    <mesh position={[0, 0.13, 0]} rotation={[0, 0, half]}><cylinderGeometry args={[0.09, 0.09, 0.62, 28]} /><meshStandardMaterial color="#f4f1ea" roughness={1} /></mesh>
    <mesh position={[0.36, 0.13, 0.13]} rotation={[half, 0, 0]}><cylinderGeometry args={[0.012, 0.012, 0.26, 8]} /><meshStandardMaterial {...steel} /></mesh>
    <mesh position={[0.18, 0.13, 0.26]} rotation={[0, 0, half]}><cylinderGeometry args={[0.012, 0.012, 0.37, 8]} /><meshStandardMaterial {...steel} /></mesh>
    <mesh position={[0, 0.13, 0.5]} rotation={[half, 0, 0]}><cylinderGeometry args={[0.035, 0.03, 0.46, 16]} /><meshStandardMaterial color="#f2a900" roughness={0.6} /></mesh>
  </group>
}

function BreakerBoard() {
  return <group>
    <mesh position={[0, 0.5, 0]}><boxGeometry args={[0.72, 0.95, 0.16]} /><meshStandardMaterial color="#e6e8eb" roughness={0.5} /></mesh>
    <mesh position={[0, 0.55, 0.083]}><boxGeometry args={[0.64, 0.24, 0.01]} /><meshStandardMaterial color="#2a2f35" roughness={0.6} /></mesh>
    {Array.from({ length: 12 }, (_, way) => <mesh key={way} position={[-0.275 + way * 0.05, 0.55, 0.1]}>
      <boxGeometry args={[0.04, 0.17, 0.03]} /><meshStandardMaterial color={way === 0 ? '#f2a900' : '#f7f7f5'} roughness={0.5} />
    </mesh>)}
  </group>
}

function RoofSheet() {
  const geometry = useMemo(() => {
    const sheet = new PlaneGeometry(1.3, 0.9, 72, 9)
    sheet.rotateX(-half)
    const position = sheet.attributes.position
    for (let index = 0; index < position.count; index++) {
      const run = (position.getZ(index) + 0.45) / 0.3
      position.setY(index, 0.035 * Math.sin(position.getX(index) * 24) + 0.03 * (1 - (run - Math.floor(run))))
    }
    sheet.computeVertexNormals()
    return sheet
  }, [])
  return <group rotation={[0.28, 0.3, 0]} position={[0, 0.22, 0]}>
    <mesh geometry={geometry}><meshStandardMaterial color="#3d4147" roughness={1} side={DoubleSide} /></mesh>
    <mesh geometry={geometry} position={[0.06, -0.07, 0.05]}><meshStandardMaterial color="#34383d" roughness={1} side={DoubleSide} /></mesh>
  </group>
}

function Pipe() {
  return <group rotation={[0, 0.3, 0]}>{[[-0.055, 0.05], [0.055, 0.05], [0, 0.145]].map(([z, y]) => <mesh key={`${z}${y}`} position={[0, y, z]} rotation={[0, 0, half]}>
    <cylinderGeometry args={[0.05, 0.05, 1.7, 24, 1, true]} /><meshStandardMaterial color="#f4f4f1" roughness={0.45} side={DoubleSide} />
  </mesh>)}</group>
}

const parts: Record<ModelKind, ComponentType> = {
  'cement-bag': CementBag, 'paint-tin': PaintTin, 'tile-stack': TileStack, 'wall-tile': WallTile, 'filament-bulb': FilamentBulb, 'cable-roll': CableRoll,
  pendant: Pendant, hammer: Hammer, roller: Roller, 'breaker-board': BreakerBoard, 'roof-sheet': RoofSheet, pipe: Pipe,
}

function release(root: Object3D) {
  root.traverse(node => {
    const mesh = node as Mesh
    mesh.geometry?.dispose()
    for (const material of ([] as Material[]).concat(mesh.material ?? [])) {
      for (const value of Object.values(material)) if ((value as Texture | null)?.isTexture) (value as Texture).dispose()
      material.dispose()
    }
  })
}

/**
 * An uploaded GLB, loaded from this site only. Whatever its own units, it is centred, stood on the
 * ground and scaled to about one metre so it fits the same stages as the built-in shapes.
 */
function GlbModel({ url }: { url: string }) {
  const invalidate = useThree(state => state.invalidate)
  const [loaded, setLoaded] = useState<{ url: string; scene: Object3D | null }>()
  useEffect(() => {
    let cancelled = false, scene: Object3D | undefined
    new GLTFLoader().load(url, gltf => {
      scene = gltf.scene
      if (cancelled) { release(scene); return }
      const bounds = new Box3().setFromObject(scene), size = bounds.getSize(new Vector3()), centre = bounds.getCenter(new Vector3())
      const fit = 1 / Math.max(size.x, size.y, size.z, 0.0001)
      scene.scale.setScalar(fit)
      scene.position.set(-centre.x * fit, -bounds.min.y * fit, -centre.z * fit)
      setLoaded({ url, scene }); invalidate()
    }, undefined, () => { if (!cancelled) { setLoaded({ url, scene: null }); invalidate() } })
    return () => { cancelled = true; if (scene) release(scene) }
  }, [url, invalidate])
  if (loaded?.url !== url) return null
  // A file that fails to load shows a plain block, so the page and its buying controls keep working.
  return loaded.scene ? <primitive object={loaded.scene} /> : <mesh position={[0, 0.3, 0]}><boxGeometry args={[0.6, 0.6, 0.6]} /><meshStandardMaterial color="#c3c7cc" roughness={0.8} /></mesh>
}

export interface ModelSource { kind: ModelKind | null; modelUrl?: string | null }

export function ProductModel({ kind, modelUrl, scale = 1 }: ModelSource & { scale?: number }) {
  const Part = kind ? parts[kind] : null
  return <group scale={scale}>{modelUrl ? <GlbModel url={modelUrl} /> : Part ? <Part /> : null}</group>
}

export function StudioLight() {
  return <>
    <hemisphereLight args={['#ffffff', '#b9b2a5', 1.5]} />
    <directionalLight position={[3, 5, 4]} intensity={2.4} />
    <directionalLight position={[-4, 2, -3]} intensity={0.7} />
  </>
}

export function BlobShadow({ size = 1.9 }: { size?: number }) {
  const map = usePainted('shadow')
  return <mesh rotation={[-half, 0, 0]} position={[0, 0.002, 0]}><planeGeometry args={[size, size]} /><meshBasicMaterial map={map} transparent depthWrite={false} /></mesh>
}
