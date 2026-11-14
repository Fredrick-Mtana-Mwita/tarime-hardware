import { FloatingProducts } from '../effects/FloatingProducts'
import { Toggle3D } from '../Toggle3D'
import '../experiences.css'

const doors = [
  { href: '#/3d/showroom', title: 'Virtual showroom', text: 'Move between four display areas and press a product to see its price and add it to your cart.' },
  { href: '#/3d/room', title: 'Room visualizer', text: 'Try floor tiles, wall finishes and lights from the catalogue in a living room or a bathroom.' },
  { href: '#/3d/configure', title: 'Product configurator', text: 'Choose a size or option and watch the model change, with the real price and stock beside it.' },
  { href: '#/shop', title: 'Product 3D views', text: 'Open a product and choose “3D view” to turn it around, zoom in and reset.' },
]

export function ExperienceHub() {
  return <div className="x3-page">
    <section className="container"><div className="x3-banner x3-hero">
      <div className="x3-banner-copy"><p className="eyebrow">3D experience</p><h1>See it before you buy it.</h1>
        <p>Explore Tarime Hardware's materials in 3D. Everything you pick here is a real catalogue product with its current price.</p>
        <a className="button primary" href="#/3d/showroom">Enter the showroom →</a></div>
      <div className="x3-banner-art" aria-hidden="true"><FloatingProducts /></div>
    </div></section>
    <section className="container section"><ul className="x3-doors">{doors.map(door => <li key={door.href}><a href={door.href}><h2>{door.title}</h2><p>{door.text}</p><span aria-hidden="true">Open →</span></a></li>)}</ul></section>
    <section className="container section x3-about">
      <h2>Good to know</h2>
      <ul>
        <li>The 3D models are illustrative shapes, not exact copies of branded products. Photos and specifications are on each product page.</li>
        <li>Prices and stock always come from the shop's catalogue and are checked again in your cart.</li>
        <li>You can use every page without 3D. Switch it off here if your phone is slow or you prefer still pictures.</li>
      </ul>
      <Toggle3D />
    </section>
  </div>
}
