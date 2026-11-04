import { useState } from 'react'
import { useAuth } from './useAuth'
import { authError } from './api'

export function AccountPage() {
  const { user, loading, unavailable, signOut, refresh } = useAuth()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function logout() {
    if (busy) return
    setBusy(true); setError('')
    try { await signOut(); window.location.hash = '#/login' } catch (problem) { setError(authError(problem)) }
    finally { setBusy(false) }
  }
  if (loading) return <div className="container load-state" role="status">Loading your account…</div>
  if (unavailable) return <div className="container load-state"><p role="alert">We could not load your account.</p><button className="button primary" onClick={refresh}>Try again</button></div>
  if (!user) return <section className="container account-page"><p className="eyebrow">Your account</p><h1>Sign in to continue</h1><p>Keep your account details in one place.</p><a className="button primary" href="#/login">Sign in</a><a className="text-link" href="#/register">Create an account</a></section>
  return <section className="container account-page"><nav className="breadcrumbs" aria-label="Breadcrumb"><a href="#/">Home</a><span>/</span><span>My account</span></nav>
    <p className="eyebrow">My account</p><h1>Welcome, {user.name}</h1><p className="account-intro">Your details, ready for your next project.</p>
    <div className="account-grid"><div className="account-panel"><h2>Your details</h2><dl><div><dt>Name</dt><dd>{user.name}</dd></div><div><dt>Phone number</dt><dd>{user.phone}</dd></div><div><dt>Email</dt><dd>{user.email || 'Not provided'}</dd></div><div><dt>Contact status</dt><dd>{user.contactVerified ? 'Verified' : 'Not verified'}</dd></div></dl>
      {!user.contactVerified && <p className="account-note">Contact verification is not available in this preview.</p>}
      {error && <p className="form-error" role="alert">{error}</p>}<button className="button secondary" disabled={busy} onClick={() => void logout()}>{busy ? 'Signing out…' : 'Sign out'}</button></div>
      <div className="account-panel account-next"><p className="eyebrow">Your next project</p><h2>Your materials &amp; orders.</h2><p>Request delivery or shop pickup, review your submitted orders and follow in-app updates.</p><a className="button primary" href="#/orders">My orders &amp; updates</a><p><a className="text-link" href="#/cart">View your cart</a></p><a className="text-link" href="#/shop">Browse products</a></div>
    </div></section>
}
