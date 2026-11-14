import { useEffect, useState } from 'react'
import { assetUrl } from '../../shared/assetUrl'
import './DesignGallery.css'

const images = [
  'Bedroom with illuminated wall arches and a pendant chandelier',
  'Living room with a layered ceiling and warm lighting',
  'Kitchen with wood accents and illuminated cabinetry',
  'Decorative ceiling with recessed lighting and wood detailing',
]

export function DesignGallery() {
  const [index, setIndex] = useState(0)
  const [playing, setPlaying] = useState(false)
  const [hovered, setHovered] = useState(false)
  useEffect(() => {
    const preference = matchMedia('(prefers-reduced-motion: reduce)')
    const update = () => setPlaying(!preference.matches)
    update()
    preference.addEventListener('change', update)
    return () => preference.removeEventListener('change', update)
  }, [])
  useEffect(() => {
    if (!playing || hovered) return
    const timer = window.setInterval(() => setIndex(current => (current + 1) % images.length), 5000)
    return () => window.clearInterval(timer)
  }, [playing, hovered])
  return <div className="design-gallery" role="region" aria-label="Interior design gallery"
    onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)}
    onFocusCapture={() => setPlaying(false)}>
    <div className="design-gallery-images">
      {images.map((description, position) => <img key={description} src={assetUrl('/images/design-'+(position+1)+'.jpg')}
        alt={description} aria-hidden={position !== index} className={position === index ? 'visible' : ''}
        loading="lazy" width={position === 3 ? 640 : 736} height={position === 3 ? 640 : 1104} />)}
    </div>
    <div className="design-gallery-controls">
      <button type="button" aria-label="Previous interior" onClick={() => setIndex((index + images.length - 1) % images.length)}>←</button>
      <span>{index + 1} / {images.length}</span>
      <button type="button" aria-label="Next interior" onClick={() => setIndex((index + 1) % images.length)}>→</button>
      <button type="button" onClick={() => setPlaying(!playing)}>{playing ? 'Pause slideshow' : 'Play slideshow'}</button>
    </div>
  </div>
}
