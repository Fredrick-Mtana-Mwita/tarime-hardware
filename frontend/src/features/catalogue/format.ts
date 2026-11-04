export function money(price: number | null, currency = 'TZS') {
  return price === null ? 'Ask for a quote' : `${currency === 'TZS' ? 'TSh' : currency} ${new Intl.NumberFormat('en-TZ', { maximumFractionDigits: 2 }).format(price)}`
}
