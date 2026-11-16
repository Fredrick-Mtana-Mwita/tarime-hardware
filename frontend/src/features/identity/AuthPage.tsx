import { useT } from '../../shared/i18n'
import { PreviewFields } from '../../preview/PreviewFields'
import { isClientPreview } from '../../shared/preview'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { useAuth } from './useAuth'
import { authError } from './api'
import { AccountPage } from './AccountPage'

function AuthForm({ mode, next }: { mode: 'login' | 'register'; next: string | null }) { const t = useT();
  const { signIn, register } = useAuth()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [visible, setVisible] = useState(false)
  const creating = mode === 'register'
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return
    const form = new FormData(event.currentTarget)
    const phone = String(form.get('phone') || '')
    const password = String(form.get('password') || '')
    setBusy(true); setError('')
    try {
      if (creating) await register({ name: String(form.get('name') || ''), phone, email: String(form.get('email') || '').trim(), password })
      else await signIn(phone, password)
      window.location.hash = next === 'checkout' ? '#/checkout' : next === 'orders' ? '#/orders' : '#/account'
    } catch (problem) { setError(authError(problem)) } finally { setBusy(false) }
  }
  return (
      <div className="auth-form"><p className="eyebrow">{t("Tarime Hardware")}</p><h1>{creating ? t("Create your account") : t("Sign in")}</h1><p>{creating ? t("A few details to get started.") : t("Sign in with your phone number and password.")}</p>
        <form onSubmit={event => void submit(event)} aria-label={creating ? t("Create account") : t("Sign in")}>{isClientPreview && <p className="client-preview-form-note">{t("Design preview only. Sign-in and registration are disabled; please do not enter real details.")}</p>}<PreviewFields>
          {creating && <><label htmlFor="customer-name">{t("Full name")}</label><input id="customer-name" name="name" autoComplete="name" required minLength={2} maxLength={100} /><label htmlFor="customer-email">{t('Email')} <span>{t('(optional)')}</span></label><input id="customer-email" name="email" autoComplete="email" type="email" maxLength={254} /></>}
          <label htmlFor="customer-phone">{t("Phone number")}</label><input id="customer-phone" name="phone" type="tel" autoComplete="tel" required maxLength={40} placeholder={t("e.g. 0712 345 678")} aria-describedby="phone-hint" /><small id="phone-hint">{t("Use your Tanzania number or include an international country code.")}</small>
          <label htmlFor="customer-password">{t("Password")}</label><div className="password-input"><input id="customer-password" name="password" type={visible ? 'text' : 'password'} autoComplete={creating ? 'new-password' : 'current-password'} required minLength={creating ? 12 : undefined} maxLength={128} aria-describedby={creating ? 'password-hint' : undefined} /><button type="button" onClick={() => setVisible(!visible)} aria-label={visible ? t("Hide password") : t("Show password")}>{visible ? t("Hide") : t("Show")}</button></div>
          {creating && <small id="password-hint">{t("Use 12 to 128 characters. A memorable passphrase works well.")}</small>}
          {error && <p role="alert" className="form-error">{t(error)}</p>}
          <button className="button primary auth-submit" type="submit" disabled={busy}>{busy ? (creating ? t("Creating account…") : t("Signing in…")) : creating ? t("Create account") : t("Sign in")}</button>
        </PreviewFields></form><div className="auth-secondary-links"><a className="back-shopping" href="#/shop">{t("← Continue browsing")}</a>{!creating && <a className="back-shopping" href="#/staff/login">{t("Staff sign-in")}</a>}</div>
      </div>
  )
}


export function AuthPage({ mode, next = null }: { mode: 'login' | 'register'; next?: string | null }) { const t = useT();
  const { user, loading } = useAuth()
  const creating = mode === 'register'
  if (loading) return <div className="container load-state" role="status">{t("Checking your session…")}</div>
  if (user) return next === 'checkout' || next === 'orders' ? <div className="container load-state"><p>{t("You’re signed in.")}</p><a className="button primary" href={next === 'checkout' ? '#/checkout' : '#/orders'}>{next === 'checkout' ? t('Continue to checkout →') : t('Continue to my orders →')}</a></div> : <AccountPage />
  return <div className="container auth-page">
    <section className={`auth-card${creating ? ' is-registering' : ''}`} aria-label={t("Customer account")}>
      <div className="auth-form-slot"><AuthForm key={mode} mode={mode} next={next} /></div>
      <aside className="auth-welcome">
        <p className="eyebrow">{t("Your next project starts here")}</p>
        <h2>{creating ? t("Welcome back!") : t("New to Tarime Hardware?")}</h2>
        <p>{creating ? t("Already part of Tarime Hardware? Sign in and get ready for your next build.") : t("Great projects start with the right materials. Create your account and start your next build with us.")}</p>
        <a className="button auth-panel-switch" href={(creating ? '#/login' : '#/register') + (next === 'checkout' || next === 'orders' ? '?next=' + next : '')}>{creating ? t("Sign in") : t("Create an account")}</a>
      </aside>
    </section>
  </div>
}
