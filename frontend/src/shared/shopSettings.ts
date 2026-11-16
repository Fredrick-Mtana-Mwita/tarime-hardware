/**
 * Shop details shown to customers.
 *
 * TO BE CONFIRMED BY THE SHOP before launch. Every group with `confirmed: false` is either hidden
 * or shown in a safe, generic form, so placeholder values never reach customers as promises.
 * Change the values, set `confirmed: true`, rebuild.
 */
export const shopSettings = {
  delivery: {
    confirmed: false,
    /** Where same-day delivery applies. */
    area: 'Tarime town',
    /** Latest order time for same-day delivery, shop time (EAT), 24-hour HH:MM. */
    sameDayCutoff: '15:00',
    /** Days the shop delivers: 0 = Sunday ... 6 = Saturday. */
    deliveryDays: [1, 2, 3, 4, 5, 6],
  },
  /** Shown in the footer and the WhatsApp button only once confirmed. */
  contact: {
    // Given by the shop owner on 15 Nov 2026. All three numbers take calls and WhatsApp.
    confirmed: true,
    phones: ['0784 446 244', '0767 446 244', '0767 347 552'],
    /** Number used for the WhatsApp chat button, in international format without + or spaces, for wa.me links. */
    whatsapp: '255784446244',
    emails: ['tarimehardware@gmail.com', 'tarimehardware@outlook.com'],
    address: { en: 'Nyerere Street, opposite the bus stand, Tarime', sw: 'Mtaa wa Nyerere, mkabala na stendi ya mabasi, Tarime' },
    /** The owner gave the times only, not the days, so no days are shown. */
    hours: { en: 'Open 7:30am – 7:00pm', sw: 'Tunafungua saa 1:30 asubuhi – saa 1:00 jioni' },
  },
  /** Returns, privacy and terms pages (text in shared/policies.ts). Linked only once the shop approves the wording. */
  policies: { confirmed: false },
  /**
   * Areas offered as a list at checkout. Until confirmed, customers type their area instead,
   * so an incomplete list never stops anyone from ordering. The names below are a starting point only.
   */
  deliveryAreas: {
    confirmed: false,
    areas: ['Nyamisangura', 'Bomani', 'Turwa', 'Sabasaba', 'Nkende', 'Ketare', 'Kenyamanyori', 'Nyandoto'],
  },
  /** Show "Only N left" when available stock is at or below this. Display only; staff low-stock alerts are separate. */
  lowStockDisplay: 10,
} as const
