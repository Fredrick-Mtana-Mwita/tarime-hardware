import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useT } from '../shared/i18n'
import { setDeliveryArea, useDeliveryArea } from '../shared/deliveryArea'

/** Compact "Deliver to" control in the header. The area is remembered and prefilled at checkout. */
export function DeliverTo() {
  const t = useT()
  const area = useDeliveryArea()
  const [open, setOpen] = useState(false)
  const [checked, setChecked] = useState(false)
  const box = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    const outside = (event: PointerEvent) => { if (box.current && !box.current.contains(event.target as Node)) setOpen(false) }
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') setOpen(false) }
    document.addEventListener('pointerdown', outside); document.addEventListener('keydown', escape)
    return () => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', escape) }
  }, [open])
  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setDeliveryArea(String(new FormData(event.currentTarget).get('area') || ''))
    setChecked(true)
  }
  return <div className="deliver-to" ref={box}>
    <button type="button" className="deliver-to-button" aria-expanded={open} aria-controls="deliver-to-panel" onClick={() => { setOpen(!open); setChecked(false) }}>
      <svg aria-hidden="true" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z" /><circle cx="12" cy="9.5" r="2.5" /></svg>
      <span><small>{t('Deliver to')}</small><strong>{area || t('Set location')}</strong></span>
    </button>
    {open && <div className="deliver-to-panel" id="deliver-to-panel" role="dialog" aria-label={t('Check delivery to your area')}>
      <p className="deliver-to-title">{t('Check delivery to your area')}</p>
      <form onSubmit={save}>
        <label className="sr-only" htmlFor="deliver-to-area">{t('Town, village or neighbourhood')}</label>
        <input id="deliver-to-area" name="area" defaultValue={area} required minLength={2} maxLength={60} placeholder={t('Town, village or neighbourhood')} autoFocus autoComplete="address-level2" />
        <button className="button primary">{t('Check delivery')}</button>
      </form>
      {checked && area && <p role="status" className="deliver-to-result">{t('Delivery to {area}: coverage and estimated fee are awaiting shop confirmation. Add your delivery address when submitting an order request.', { area })}</p>}
    </div>}
  </div>
}
