import { useState } from 'react'
import { useT } from '../../shared/i18n'
import { shopSettings } from '../../shared/shopSettings'
import { deliveryArea } from '../../shared/deliveryArea'

export type Location = { lat: number; lng: number } | null

/**
 * Delivery address in parts: area, house/street details, a landmark, and an optional location from the phone.
 * The area list appears only once the shop confirms it; until then customers type their area.
 */
export function AddressFields({ location, onLocation }: { location: Location; onLocation: (value: Location) => void }) {
  const t = useT()
  const [locating, setLocating] = useState(false)
  const [locationError, setLocationError] = useState('')
  const areas = shopSettings.deliveryAreas
  const saved = deliveryArea()
  const [area, setArea] = useState(() => (areas.areas as readonly string[]).includes(saved) ? saved : saved ? 'other' : '')
  function locate() {
    if (!('geolocation' in navigator)) { setLocationError('This browser cannot share a location. Describe the place in words instead.'); return }
    setLocating(true); setLocationError('')
    navigator.geolocation.getCurrentPosition(
      position => { onLocation({ lat: position.coords.latitude, lng: position.coords.longitude }); setLocating(false) },
      () => { setLocationError('Location was not shared. Describe the place in words instead.'); setLocating(false) },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 60000 })
  }
  return <fieldset className="address-fields"><legend>{t('Delivery address')}</legend>
    <div className="checkout-pair">
      <div className="checkout-field">
        <label htmlFor="order-area">{t('Area or village')}</label>
        {areas.confirmed ? <>
          <select id="order-area" name={area === 'other' ? undefined : 'area'} value={area} onChange={event => setArea(event.target.value)} required>
            <option value="" disabled>{t('Choose your area')}</option>{areas.areas.map(a => <option key={a} value={a}>{a}</option>)}<option value="other">{t('Another area…')}</option>
          </select>
          {area === 'other' && <input aria-label={t('Area or village')} name="area" required minLength={2} maxLength={60} defaultValue={saved} />}
        </> : <input id="order-area" name="area" required minLength={2} maxLength={60} defaultValue={saved} placeholder={t('e.g. Nyamisangura, Tarime')} />}
      </div>
      <div className="checkout-field">
        <label htmlFor="order-landmark">{t('Nearest landmark')} <span>{t('(optional)')}</span></label>
        <input id="order-landmark" name="landmark" maxLength={80} placeholder={t('e.g. Opposite the market, near the church')} />
      </div>
    </div>
    <label htmlFor="order-address">{t('House, street or plot')}</label>
    <textarea id="order-address" name="address" autoComplete="street-address" required minLength={3} maxLength={250} rows={2} placeholder={t('e.g. Plot 12, blue gate')} />
    <div className="location-row">
      {location ? <p className="location-set">{t('Location added')} · {location.lat.toFixed(4)}, {location.lng.toFixed(4)} <button type="button" className="text-link" onClick={() => onLocation(null)}>{t('Remove')}</button></p>
        : <button type="button" className="button secondary" onClick={locate} disabled={locating}>{locating ? t('Getting your location…') : t('Add my current location')}</button>}
      <small className="commerce-muted">{t('Optional. Helps the driver find you. Your location is shared only with the shop for this order.')}</small>
      {locationError && <small role="alert" className="form-error">{t(locationError)}</small>}
    </div>
    <p className="commerce-muted">{t('The shop will confirm delivery coverage and agree the fee with you before confirmation.')}</p>
  </fieldset>
}
