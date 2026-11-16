/**
 * DRAFT policy text, written from what the app actually does. The shop must review and approve it
 * (requirements OD-17) before setting shopSettings.policies.confirmed to true. A lawyer should check it.
 */
export type PolicySlug = 'returns' | 'privacy' | 'terms'
interface Text { en: string; sw: string }
export interface Policy { title: Text; intro: Text; points: Text[] }

export const policies: Record<PolicySlug, Policy> = {
  returns: {
    title: { en: 'Returns and refunds', sw: 'Kurudisha bidhaa na kurejeshewa pesa' },
    intro: { en: 'Talk to the shop first. Returns are agreed case by case until the full returns policy is published.', sw: 'Wasiliana na duka kwanza. Kurudisha bidhaa kunakubaliwa kwa kila hali hadi sera kamili itakapotolewa.' },
    points: [
      { en: 'Keep your receipt. Returns are checked against the original order.', sw: 'Tunza risiti yako. Bidhaa zinazorudishwa hukaguliwa dhidi ya oda ya awali.' },
      { en: 'Goods are inspected by the shop. Only unused, resalable goods can be taken back into stock.', sw: 'Bidhaa hukaguliwa na duka. Ni bidhaa ambazo hazijatumika na zinazoweza kuuzwa tena tu zinazopokelewa.' },
      { en: 'Refunds are approved by the Owner and paid in cash or by mobile money. You get an update when a refund is sent.', sw: 'Kurejeshewa pesa huidhinishwa na mmiliki na hulipwa kwa pesa taslimu au kwa simu. Utapata taarifa pesa zikitumwa.' },
      { en: 'If the shop cancels a confirmed order you paid for, the money paid is refunded.', sw: 'Duka likighairi oda iliyothibitishwa uliyolipia, pesa ulizolipa zitarejeshwa.' },
    ],
  },
  privacy: {
    title: { en: 'Privacy notice', sw: 'Taarifa ya faragha' },
    intro: { en: 'What we keep about you, why, and who sees it.', sw: 'Tunachohifadhi kukuhusu, kwa nini, na nani anakiona.' },
    points: [
      { en: 'We keep your name, phone number, optional email, delivery addresses and your orders, so we can confirm, deliver and support your orders.', sw: 'Tunahifadhi jina, namba ya simu, barua pepe (si lazima), anwani za kupelekewa na oda zako, ili kuthibitisha, kupeleka na kusaidia oda zako.' },
      { en: 'Only shop staff who need it see your details. Drivers see the address and phone for deliveries assigned to them.', sw: 'Ni wafanyakazi wa duka wanaohitaji tu wanaoona taarifa zako. Madereva huona anwani na simu kwa usafirishaji waliopewa.' },
      { en: 'We do not take card payments online and do not sell your details.', sw: 'Hatupokei malipo ya kadi mtandaoni na hatuuzi taarifa zako.' },
      { en: 'Your browser keeps your cart and language choice on your device. Sign-in uses a secure session cookie.', sw: 'Kivinjari chako huhifadhi kikapu na lugha uliyochagua kwenye kifaa chako. Kuingia hutumia kidakuzi salama cha kikao.' },
      { en: 'Contact the shop to see or correct your details.', sw: 'Wasiliana na duka kuona au kurekebisha taarifa zako.' },
    ],
  },
  terms: {
    title: { en: 'Terms of use', sw: 'Masharti ya matumizi' },
    intro: { en: 'How ordering on this site works.', sw: 'Jinsi kuagiza kwenye tovuti hii kunavyofanya kazi.' },
    points: [
      { en: 'An order sent here is a request. It becomes an order only when the shop confirms it.', sw: 'Oda unayotuma hapa ni ombi. Inakuwa oda pale tu duka linapoithibitisha.' },
      { en: 'Prices are checked again when you send a request. The delivery fee is agreed with you before confirmation.', sw: 'Bei hukaguliwa tena unapotuma ombi. Gharama ya usafirishaji hukubaliwa nawe kabla ya uthibitisho.' },
      { en: 'Stock is held for you only after confirmation. The shop may decline a request and will tell you why.', sw: 'Bidhaa huhifadhiwa kwa ajili yako baada ya uthibitisho tu. Duka linaweza kukataa ombi na litakueleza sababu.' },
      { en: 'Photos are illustrative. Confirm finish, size and suitability with the shop before buying.', sw: 'Picha ni za mfano. Thibitisha umaliziaji, ukubwa na ufaafu na duka kabla ya kununua.' },
      { en: 'Payment is made to the shop in cash or by mobile money. Receipts from this site are not TRA fiscal (EFD) receipts.', sw: 'Malipo hufanywa dukani kwa pesa taslimu au kwa simu. Risiti za tovuti hii si risiti za kodi za TRA (EFD).' },
    ],
  },
}
