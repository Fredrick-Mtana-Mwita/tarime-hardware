import { useT } from '../../shared/i18n'
import { useEffect, useState } from 'react'
import { assetUrl } from '../../shared/assetUrl'
import './DesignGallery.css'

const images = [
  'Bedroom with illuminated wall arches and a pendant chandelier',
  'Living room with a layered ceiling and warm lighting',
  'Kitchen with wood accents and illuminated cabinetry',
  'Decorative ceiling with recessed lighting and wood detailing',
]

export function DesignGallery() { const t = useT();
  const [index, setIndex] = useState(0)
  // Turns every 5 seconds, pauses while the pointer is over it, and stays still for visitors who turn off animations.
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
  return <div className="design-gallery" role="region" aria-label={t("Interior design gallery")}
    onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)}>
    <div className="design-gallery-images">
      {images.map((description, position) => <img key={description} src={assetUrl('/images/design-'+(position+1)+'.jpg')}
        alt={t(description)} aria-hidden={position !== index} className={position === index ? 'visible' : ''}
        loading="lazy" width={position === 3 ? 640 : 736} height={position === 3 ? 640 : 1104} />)}
    </div>
  </div>
}
