import { useEffect, useMemo } from 'react'
import { CanvasTexture, RepeatWrapping, SRGBColorSpace } from 'three'

// Surface pictures painted in code, so the project owns them and nothing is downloaded.
function random(seed: number) { return () => { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296 } }

const painters = {
  /** Four 60 cm marble-look tiles (2 x 2) with grout lines. */
  marble(c: CanvasRenderingContext2D, size: number) {
    const next = random(7), half = size / 2
    for (let tile = 0; tile < 4; tile++) {
      const x = (tile % 2) * half, y = Math.floor(tile / 2) * half
      c.fillStyle = ['#f3f0ea', '#efece6', '#f5f2ed', '#f0ede8'][tile]
      c.fillRect(x, y, half, half)
      c.save(); c.beginPath(); c.rect(x, y, half, half); c.clip()
      for (let vein = 0; vein < 7; vein++) {
        let px = x + next() * half, py = y + next() * half
        c.beginPath(); c.moveTo(px, py)
        for (let step = 0; step < 6; step++) { px += (next() - 0.35) * half * 0.4; py += (next() - 0.5) * half * 0.4; c.lineTo(px, py) }
        c.strokeStyle = `rgba(118,116,120,${0.1 + next() * 0.22})`; c.lineWidth = 0.6 + next() * 2.4; c.stroke()
      }
      c.restore()
      c.strokeStyle = '#c4beb2'; c.lineWidth = 3; c.strokeRect(x + 1.5, y + 1.5, half - 3, half - 3)
    }
  },
  /** Sixteen small hand-glazed tiles (4 x 4) with pale grout. */
  glazed(c: CanvasRenderingContext2D, size: number) {
    const next = random(21), cell = size / 4
    c.fillStyle = '#e9e5db'; c.fillRect(0, 0, size, size)
    for (let row = 0; row < 4; row++) for (let column = 0; column < 4; column++) {
      const x = column * cell + 4, y = row * cell + 4, edge = cell - 8
      const shade = c.createLinearGradient(x, y, x + edge, y + edge)
      const hue = 162 + next() * 14, light = 32 + next() * 10
      shade.addColorStop(0, `hsl(${hue},38%,${light + 9}%)`); shade.addColorStop(1, `hsl(${hue},40%,${light}%)`)
      c.fillStyle = shade; c.fillRect(x, y, edge, edge)
    }
  },
  /** Kraft paper sack with a plain, unbranded label. */
  bag(c: CanvasRenderingContext2D, size: number) {
    const next = random(3)
    c.fillStyle = '#c9ae84'; c.fillRect(0, 0, size, size)
    for (let speck = 0; speck < 900; speck++) { c.fillStyle = `rgba(90,66,36,${next() * 0.12})`; c.fillRect(next() * size, next() * size, 2, 2) }
    c.fillStyle = '#16191c'; c.fillRect(0, size * 0.34, size, size * 0.32)
    c.fillStyle = '#f2a900'; c.fillRect(0, size * 0.3, size, size * 0.03); c.fillRect(0, size * 0.67, size, size * 0.03)
    c.fillStyle = '#ffffff'; c.font = `800 ${size * 0.17}px Arial, sans-serif`; c.textAlign = 'center'; c.textBaseline = 'middle'
    c.fillText('CEMENT', size / 2, size / 2)
  },
  /** Soft round shadow placed under a model instead of real-time shadows. */
  shadow(c: CanvasRenderingContext2D, size: number) {
    const fade = c.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2)
    fade.addColorStop(0, 'rgba(22,25,28,0.26)'); fade.addColorStop(0.55, 'rgba(22,25,28,0.09)'); fade.addColorStop(1, 'rgba(22,25,28,0)')
    c.fillStyle = fade; c.fillRect(0, 0, size, size)
  },
}
export type Painted = keyof typeof painters
const sources = new Map<Painted, HTMLCanvasElement>()

function source(kind: Painted) {
  let canvas = sources.get(kind)
  if (!canvas) {
    canvas = document.createElement('canvas'); canvas.width = canvas.height = 512
    const context = canvas.getContext('2d')
    if (context) painters[kind](context, 512)
    sources.set(kind, canvas)
  }
  return canvas
}

/** A repeating texture for one surface. The picture is painted once and shared; the GPU copy is freed on unmount. */
export function usePainted(kind: Painted, repeatX = 1, repeatY = 1) {
  const texture = useMemo(() => {
    const made = new CanvasTexture(source(kind))
    made.colorSpace = SRGBColorSpace; made.wrapS = made.wrapT = RepeatWrapping; made.anisotropy = 4
    made.repeat.set(repeatX, repeatY)
    return made
  }, [kind, repeatX, repeatY])
  useEffect(() => () => texture.dispose(), [texture])
  return texture
}
