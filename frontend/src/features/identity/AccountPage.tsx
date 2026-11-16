import { useT } from '../../shared/i18n'
import { useState } from 'react'
import { useAuth } from './useAuth'
import { authError } from './api'

export function AccountPage() { const t = useT();
  const { user, loading, unavailable, signOut, refresh } = useAuth()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function logout() {
    if (busy) return
    setBusy(true); setError('')
    try { await signOut(); window.location.hash = '#/login' } catch (problem) { setError(authError(problem)) }
    finally { setBusy(false) }
  }
  if (loading) return <div className="container load-state" role="status">{t("Loading your account…")}</div>
  if (unavailable) return <div className="container load-state"><p role="alert">{t("We could not load your account.")}</p><button className="button primary" onClick={refresh}>{t("Try again")}</button></div>
  if (!user) return <section className="container account-page"><p className="eyebrow">{t("Your account")}</p><h1>{t("Sign in to continue")}</h1><p>{t("Keep your account details in one place.")}</p><a className="button primary" href="#/login">{t("Sign in")}</a><a className="text-link" href="#/register">{t("Create an account")}</a></section>
  return <section className="container account-page"><nav className="breadcrumbs" aria-label={t("Breadcrumb")}><a href="#/">{t("Home")}</a><span>/</span><span>{t("My account")}</span></nav>
    <p className="eyebrow">{t("My account")}</p><h1>{t('Welcome, {name}', { name: user.name })}</h1><p className="account-intro">{t("Your details, ready for your next project.")}</p>
    <div className="account-grid"><div className="account-panel"><h2>{t("Your details")}</h2><dl><div><dt>{t("Name")}</dt><dd>{user.name}</dd></div><div><dt>{t("Phone number")}</dt><dd>{user.phone}</dd></div><div><dt>{t("Email")}</dt><dd>{user.email || t("Not provided")}</dd></div><div><dt>{t("Contact status")}</dt><dd>{user.contactVerified ? t("Verified") : t("Not verified")}</dd></div></dl>
      {!user.contactVerified && <p className="account-note">{t("Contact verification is not available in this preview.")}</p>}
      {error && <p className="form-error" role="alert">{t(error)}</p>}<button className="button secondary" disabled={busy} onClick={() => void logout()}>{busy ? t("Signing out…") : t("Sign out")}</button></div>
      <div className="account-panel account-next"><p className="eyebrow">{t("Your next project")}</p><h2>{t('Your materials & orders.')}</h2><p>{t('Request delivery or shop pickup, review your submitted orders and follow in-app updates.')}</p><a className="button primary" href="#/orders">{t('My orders & updates')}</a><p><a className="text-link" href="#/cart">{t("View your cart")}</a></p><a className="text-link" href="#/shop">{t("Browse products")}</a></div>
    </div></section>
}
