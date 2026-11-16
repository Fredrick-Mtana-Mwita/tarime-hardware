import { describe, expect, it } from 'vitest'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { AddressText } from './AddressText'

const html = (value: string) => renderToStaticMarkup(createElement(AddressText, { value }))

describe('AddressText', () => {
  it('keeps lines and turns only well-formed GPS lines into a maps link', () => {
    const out = html('Area: Nyamisangura\nPlot 12\nGPS: -1.34917,34.36731 https://maps.google.com/?q=-1.34917,34.36731')
    expect(out).toContain('<span>Area: Nyamisangura</span><span>Plot 12</span>')
    expect(out).toContain('href="https://maps.google.com/?q=-1.34917,34.36731"')
    expect(html('GPS: javascript:alert(1)')).not.toContain('href')
  })
})
