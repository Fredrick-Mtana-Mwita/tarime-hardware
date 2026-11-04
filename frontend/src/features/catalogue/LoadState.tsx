export function SampleNotice() { return <p className="sample-notice">Preview catalogue · Sample products, prices and illustrative photos. Confirm specifications with the shop.</p> }
export function LoadState({ error, retry }: { error?: string; retry: () => void }) {
  return <div className="load-state" role={error ? 'alert' : 'status'}><p>{error || 'Loading products…'}</p>
    {error && <button className="button primary" onClick={retry}>Try again</button>}</div>
}
