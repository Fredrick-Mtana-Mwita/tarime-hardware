import type { ReactNode } from 'react'
import { useT, useLanguage } from '../shared/i18n'
import { shopSettings } from '../shared/shopSettings'
import { contactReady, telLink, whatsappLink } from '../shared/contact'
import { policies } from '../shared/policies'
import type { PolicySlug } from '../shared/policies'

const shopLinks: [string, string][] = [
  ['#/shop', 'All products'],
  ['#/shop?category=cement', 'Cement and building'],
  ['#/shop?category=roofing', 'Roofing'],
  ['#/shop?category=paint', 'Paint and finishing'],
  ['#/shop?category=electrical', 'Electrical'],
  ['#/shop?category=plumbing', 'Plumbing'],
  ['#/shop?category=tools', 'Tools'],
]

function Icon({ children }: { children: ReactNode }) {
  return <svg className="sf-icon" aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">{children}</svg>
}
const pin = <Icon><path d="M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z" /><circle cx="12" cy="9.5" r="2.5" /></Icon>
const clock = <Icon><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></Icon>
const phoneIcon = <Icon><path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z" /></Icon>
const mail = <Icon><rect x="3" y="5" width="18" height="14" rx="2" /><path d="m3 7 9 6 9-6" /></Icon>

/** Contact details and policy links appear only once the shop confirms them in shopSettings. */
export function Footer() {
  const t = useT(); const lang = useLanguage()
  const c = shopSettings.contact
  const ready = contactReady()
  return <footer className="site-footer">
    <div className="container sf-grid">
      <section className="sf-brand" aria-label="Tarime Hardware">
        <a href="#/" className="sf-logo" aria-label={t('Tarime Hardware home')}><span className="logo-mark">T</span><span><strong>TARIME</strong><small>HARDWARE</small></span></a>
        <p>{t('Building materials, tools and finishing supplies for homes and sites in Tarime and nearby.')}</p>
        {ready && <a className="sf-whatsapp" href={whatsappLink(c.whatsapp, t('Hello Tarime Hardware, I have a question.'))} target="_blank" rel="noopener noreferrer">
          <svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M12 2a10 10 0 0 0-8.6 15.1L2 22l5-1.3A10 10 0 1 0 12 2zm4.5 12.1c-.2-.1-1.5-.7-1.7-.8s-.4-.1-.6.1-.7.8-.8 1-.3.2-.5.1a6.7 6.7 0 0 1-3.3-2.9c-.2-.4.2-.4.7-1.3a.5.5 0 0 0 0-.5c0-.1-.6-1.4-.8-1.9s-.4-.4-.6-.4h-.5a1 1 0 0 0-.7.3 3 3 0 0 0-.9 2.2 5.2 5.2 0 0 0 1.1 2.7 11.8 11.8 0 0 0 4.5 4c1.7.7 2.3.8 3.2.6a2.7 2.7 0 0 0 1.8-1.2 2.2 2.2 0 0 0 .2-1.2c-.1-.1-.3-.2-.5-.3z" /></svg>
          {t('Chat on WhatsApp')}</a>}
      </section>
      <nav className="sf-col" aria-label={t('Shop')}>
        <h2>{t('Shop')}</h2>
        <ul>{shopLinks.map(([href, label]) => <li key={href}><a href={href}>{t(label)}</a></li>)}
          <li><a href="#/design-services">{t('Interior and exterior design')}</a></li></ul>
      </nav>
      {ready && <section className="sf-col" aria-label={t('Visit us')}>
        <h2>{t('Visit us')}</h2>
        <ul className="sf-details">
          <li>{pin}<span>{c.address[lang]}</span></li>
          <li>{clock}<span>{c.hours[lang]}</span></li>
        </ul>
      </section>}
      {ready && <section className="sf-col" aria-label={t('Contact us')}>
        <h2>{t('Contact us')}</h2>
        <ul className="sf-details">
          {c.phones.map(phone => <li key={phone}>{phoneIcon}<a href={telLink(phone)}>{phone}</a></li>)}
          {c.emails.map(email => <li key={email}>{mail}<a href={`mailto:${email}`}>{email}</a></li>)}
        </ul>
      </section>}
    </div>
    <div className="container sf-bottom">
      <span>© {new Date().getFullYear()} Tarime Hardware · Tarime, Tanzania</span>
      {shopSettings.policies.confirmed && <nav aria-label={t('Shop policies')}>{(Object.keys(policies) as PolicySlug[]).map(slug => <a key={slug} href={`#/policies/${slug}`}>{policies[slug].title[lang]}</a>)}</nav>}
    </div>
  </footer>
}

/** Floating chat button, only when a real WhatsApp number is confirmed. */
export function WhatsAppButton({ orderReference }: { orderReference?: string }) {
  const t = useT()
  if (!contactReady()) return null
  const message = orderReference ? t('Hello Tarime Hardware, I have a question about order {reference}.', { reference: orderReference }) : t('Hello Tarime Hardware, I have a question.')
  return <a className="whatsapp-button" href={whatsappLink(shopSettings.contact.whatsapp, message)} target="_blank" rel="noopener noreferrer" aria-label={t('Chat with the shop on WhatsApp')}>
    <svg aria-hidden="true" viewBox="0 0 24 24" width="26" height="26" fill="currentColor"><path d="M12 2a10 10 0 0 0-8.6 15.1L2 22l5-1.3A10 10 0 1 0 12 2zm0 18.2a8.2 8.2 0 0 1-4.2-1.2l-.3-.2-3 .8.8-2.9-.2-.3A8.2 8.2 0 1 1 12 20.2zm4.5-6.1c-.2-.1-1.5-.7-1.7-.8s-.4-.1-.6.1-.7.8-.8 1-.3.2-.5.1a6.7 6.7 0 0 1-3.3-2.9c-.2-.4.2-.4.7-1.3a.5.5 0 0 0 0-.5c0-.1-.6-1.4-.8-1.9s-.4-.4-.6-.4h-.5a1 1 0 0 0-.7.3 3 3 0 0 0-.9 2.2 5.2 5.2 0 0 0 1.1 2.7 11.8 11.8 0 0 0 4.5 4c1.7.7 2.3.8 3.2.6a2.7 2.7 0 0 0 1.8-1.2 2.2 2.2 0 0 0 .2-1.2c-.1-.1-.3-.2-.5-.3z" /></svg>
  </a>
}
