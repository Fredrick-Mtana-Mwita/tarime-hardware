import { describe, expect, it } from 'vitest'
import { composeAddress, telLink, whatsappLink } from './contact'

describe('contact helpers', () => {
  it('builds a WhatsApp link from any phone format', () => {
    expect(whatsappLink('+255 712 345 678', 'Habari, nina swali kuhusu oda FPA-1')).toBe('https://wa.me/255712345678?text=Habari%2C%20nina%20swali%20kuhusu%20oda%20FPA-1')
  })
  it('turns local phone numbers into international call links', () => {
    expect(telLink('0784 446 244')).toBe('tel:+255784446244')
    expect(telLink('+255 767 347 552')).toBe('tel:+255767347552')
  })
  it('lays out an address for staff and drivers, within the server limit', () => {
    const address = composeAddress({ area: ' Nyamisangura ', details: 'Plot 12, blue gate', landmark: 'Opposite the market', location: { lat: -1.35, lng: 34.367891 } })
    expect(address).toBe('Area: Nyamisangura\nPlot 12, blue gate\nLandmark: Opposite the market\nGPS: -1.35000,34.36789 https://maps.google.com/?q=-1.35000,34.36789')
    expect(composeAddress({ area: '', details: 'Plot 12', landmark: '' })).toBe('Plot 12')
    expect(composeAddress({ area: 'x'.repeat(60), details: 'y'.repeat(250), landmark: 'z'.repeat(80), location: { lat: -12.34567, lng: -123.45678 } }).length).toBeLessThanOrEqual(500)
  })
})
