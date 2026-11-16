import { shopSettings } from '../../shared/shopSettings'
import type { Variant } from './types'
import { translate } from '../../shared/i18n'
import type { Lang } from '../../shared/i18n'

/** "Only N left" when stock is genuinely low. */
export function lowStockText(variant: Variant | undefined, lang: Lang = 'en'): string | null {
  if (!variant || !variant.inStock || variant.available <= 0 || variant.available > shopSettings.lowStockDisplay) return null
  const amount = Number.isInteger(variant.available) ? variant.available : Number(variant.available.toFixed(3))
  return variant.fractional ? translate(lang, 'Only {n} {unit} left', { n: amount, unit: variant.unit }) : translate(lang, 'Only {n} left', { n: amount })
}

