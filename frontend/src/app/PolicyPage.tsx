import { useLanguage, useT } from '../shared/i18n'
import { shopSettings } from '../shared/shopSettings'
import { policies } from '../shared/policies'
import type { PolicySlug } from '../shared/policies'

export function PolicyPage({ slug }: { slug: string }) {
  const t = useT(); const lang = useLanguage()
  const policy = slug in policies ? policies[slug as PolicySlug] : null
  if (!policy) return <div className="container load-state"><h1>{t('Page not found')}</h1><a className="button primary" href="#/shop">{t('Browse products')}</a></div>
  if (!shopSettings.policies.confirmed) return <div className="container commerce-page"><p className="eyebrow">{t('Shop policies')}</p><h1>{policy.title[lang]}</h1><p className="commerce-intro">{t('The shop is preparing this page. Contact the shop with any questions.')}</p></div>
  return <article className="container commerce-page policy-page"><p className="eyebrow">{t('Shop policies')}</p><h1>{policy.title[lang]}</h1><p className="commerce-intro">{policy.intro[lang]}</p>
    <ul>{policy.points.map(point => <li key={point.en}>{point[lang]}</li>)}</ul></article>
}
