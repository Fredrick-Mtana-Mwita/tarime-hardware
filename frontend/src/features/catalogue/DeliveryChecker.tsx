import { useState } from 'react'
import type { FormEvent } from 'react'

export function DeliveryChecker() {
  const [area, setArea] = useState('')
  const [checkedArea, setCheckedArea] = useState('')
  function check(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setCheckedArea(area.trim())
  }
  return <section className="container section" aria-labelledby="delivery-title">
    <p className="eyebrow">From our Tarime store to your site</p>
    <h2 id="delivery-title">Check delivery to your area</h2>
    <form className="staff-form" onSubmit={check}>
      <label htmlFor="delivery-area">Town, village or neighbourhood</label>
      <input id="delivery-area" value={area} onChange={event => { setArea(event.target.value); setCheckedArea('') }} required maxLength={120} placeholder="Enter your delivery area" />
      <button className="button primary" disabled={!area.trim()}>Check delivery</button>
    </form>
    {checkedArea && <p role="status">Delivery to {checkedArea}: coverage and estimated fee are awaiting shop confirmation. Add your delivery address when submitting an order request.</p>}
  </section>
}
