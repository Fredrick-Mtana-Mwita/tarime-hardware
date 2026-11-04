export function PreviewNotice() {
  return <aside className="client-preview-notice" aria-label="Client preview notice"><strong>CLIENT PREVIEW</strong><span>Sample data · No real sign-in, orders or payments</span><nav aria-label="Preview pages"><a href="#/">Storefront</a><a href="#/staff/inventory">Inventory preview</a><a href="#/login">Login design</a></nav></aside>
}
export function PreviewUnavailable() {
  return <main className="container commerce-page"><p className="eyebrow">Client preview</p><h1>This action needs the full application</h1><p className="commerce-intro">This site is for reviewing the design. Real accounts, order submission and private records are not available here.</p><a className="button primary" href="#/shop">Continue browsing</a><a className="button secondary" href="#/staff/inventory">View inventory design</a></main>
}
