import { useT } from '../shared/i18n'
export function PreviewNotice() { const t = useT();
  return <aside className="client-preview-notice" aria-label={t("Client preview notice")}><strong>{t("CLIENT PREVIEW")}</strong><span>{t("Sample data · No real sign-in, orders or payments")}</span><nav aria-label={t("Preview pages")}><a href="#/">{t("Storefront")}</a><a href="#/staff/inventory">{t("Inventory preview")}</a><a href="#/login">{t("Login design")}</a></nav></aside>
}
export function PreviewUnavailable() { const t = useT();
  return <main className="container commerce-page"><p className="eyebrow">{t("Client preview")}</p><h1>{t("This action needs the full application")}</h1><p className="commerce-intro">{t("This site is for reviewing the design. Real accounts, order submission and private records are not available here.")}</p><a className="button primary" href="#/shop">{t("Continue browsing")}</a><a className="button secondary" href="#/staff/inventory">{t("View inventory design")}</a></main>
}
