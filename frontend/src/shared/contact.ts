import { shopSettings } from './shopSettings'

/** wa.me link with an optional prefilled message. Only digits are kept from the number. */
export function whatsappLink(number: string, message?: string) {
  const digits = number.replace(/\D/g, '')
  return `https://wa.me/${digits}` + (message ? `?text=${encodeURIComponent(message)}` : '')
}
/** tel: link in international form, e.g. "0784 446 244" becomes tel:+255784446244. */
export function telLink(phone: string) {
  const digits = phone.replace(/\D/g, '')
  return 'tel:+' + (digits.startsWith('0') ? '255' + digits.slice(1) : digits)
}
export const contactReady = () => shopSettings.contact.confirmed && /^\d{9,15}$/.test(shopSettings.contact.whatsapp.replace(/\D/g, ''))

export interface AddressParts { area: string; details: string; landmark: string; location?: { lat: number; lng: number } | null }
/**
 * One address text for the order, laid out for staff and drivers. Labels stay in English because staff read them.
 * The server accepts up to 500 characters; the form limits each part (60 / 250 / 80) so the total fits.
 */
export function composeAddress({ area, details, landmark, location }: AddressParts) {
  const lines = [area.trim() && `Area: ${area.trim()}`, details.trim(), landmark.trim() && `Landmark: ${landmark.trim()}`,
    location && `GPS: ${location.lat.toFixed(5)},${location.lng.toFixed(5)} https://maps.google.com/?q=${location.lat.toFixed(5)},${location.lng.toFixed(5)}`]
  return lines.filter(Boolean).join('\n')
}
