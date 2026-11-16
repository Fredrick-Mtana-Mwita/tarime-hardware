import { useEffect, useState } from 'react'
import { useLanguage } from '../shared/i18n'

/** Product words with a matching icon that rotate in the empty search box, like "Search cement 🧱", then "Search paint 🎨". */
const icons = ['🧱', '🎨', '🔲', '🏠', '🔌', '🚰', '🔨', '💡']
const words = {
  en: { lead: 'Search', items: ['cement', 'paint', 'tiles', 'roofing sheets', 'cables', 'pipes', 'tools', 'lights'] },
  sw: { lead: 'Tafuta', items: ['saruji', 'rangi', 'vigae', 'mabati', 'waya', 'mabomba', 'zana', 'taa'] },
}
const reducedMotion = () => typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

/** Visual only (aria-hidden): the input keeps its own label for screen readers. Paused when reduced motion is asked for. */
export function SearchHint({ hidden }: { hidden: boolean }) {
  const lang = useLanguage()
  const [index, setIndex] = useState(0)
  const still = reducedMotion()
  useEffect(() => {
    if (hidden || still) return
    const timer = setInterval(() => setIndex(i => i + 1), 2400)
    return () => clearInterval(timer)
  }, [hidden, still])
  if (hidden) return null
  const { lead, items } = words[lang]
  return <span className="search-hint" aria-hidden="true">{lead} {still ? items.slice(0, 3).join(', ') + '…'
    : <span className="search-hint-word" key={lang + index}>{items[index % items.length]} <span className="search-hint-icon">{icons[index % icons.length]}</span></span>}</span>
}
