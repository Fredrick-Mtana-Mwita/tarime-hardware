import { setLanguage, useLanguage, useT } from '../shared/i18n'
import type { Lang } from '../shared/i18n'

const options: { value: Lang; label: string }[] = [{ value: 'sw', label: 'Kiswahili' }, { value: 'en', label: 'English' }]

/** Compact switch in the top bar, always visible. */
export function LanguageSwitch() {
  const lang = useLanguage(); const t = useT()
  return <div className="language-switch" role="group" aria-label={t('Language')}>
    {options.map(o => <button key={o.value} type="button" lang={o.value} aria-pressed={lang === o.value} onClick={() => setLanguage(o.value)}>{o.value === 'sw' ? 'SW' : 'EN'}<span className="sr-only"> {o.label}</span></button>)}
  </div>
}
