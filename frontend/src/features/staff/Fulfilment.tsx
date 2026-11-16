import { AddressText } from './AddressText'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { staffWrite, staffError } from './api'
import { useWorkspaceData } from './useWorkspaceData'
import { isClientPreview } from '../../shared/preview'

interface Driver { id: string; name: string }
interface Job { id: string; reference: string; status: string; fulfilment: string; branch_code: string; contact_name: string; contact_phone: string; address: string; instructions: string }
const labels: Record<string,string> = { READY_FOR_PICKUP: 'Mark ready for pickup', READY_FOR_DELIVERY: 'Assign driver and mark ready', OUT_FOR_DELIVERY: 'Confirm dispatch', DELIVERED: 'Confirm delivery', COLLECTED: 'Confirm collection' }
function JobCard({job, drivers, role, refresh}: {job: Job; drivers: Driver[]; role: string; refresh: () => void}) {
  const [busy,setBusy] = useState(false)
  const [error,setError] = useState('')
  const target = job.status === 'CONFIRMED' ? (job.fulfilment === 'PICKUP' ? 'READY_FOR_PICKUP' : 'READY_FOR_DELIVERY') :
    ({ READY_FOR_PICKUP:'COLLECTED', READY_FOR_DELIVERY:'OUT_FOR_DELIVERY', OUT_FOR_DELIVERY:'DELIVERED' } as Record<string,string>)[job.status]
  const handover = target === 'COLLECTED' || target === 'DELIVERED'
  const permitted = role !== 'SALES' || (target !== 'OUT_FOR_DELIVERY' && target !== 'DELIVERED')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    const fields = new FormData(event.currentTarget)
    setBusy(true)
    try {
      await staffWrite('/api/staff/fulfilment/'+job.id,{target,driver:fields.get('driver'),recipient:fields.get('recipient'),acknowledged:fields.has('acknowledged')})
      refresh()
    } catch (problem) { setError(staffError(problem)) } finally { setBusy(false) }
  }
  return <article className="commerce-panel"><h2>{job.reference}</h2><p>{job.branch_code} · {job.fulfilment} · {job.status.replaceAll('_',' ')}</p>
    <p>{job.contact_name} · {job.contact_phone}</p>{job.address && <p><AddressText value={job.address} /></p>}{job.instructions && <p>{job.instructions}</p>}
    {target && permitted && <form className="staff-form" onSubmit={submit}>
      {target === 'READY_FOR_DELIVERY' && <label>Assigned driver<select required name="driver" defaultValue=""><option value="">Select a driver</option>{drivers.map(d=><option key={d.id} value={d.id}>{d.name}</option>)}</select></label>}
      {handover && <><label>Recipient name<input name="recipient" disabled={isClientPreview} required maxLength={100}/></label><label><input type="checkbox" name="acknowledged" disabled={isClientPreview} required/> I handed the goods to this recipient.</label></>}
      {error && <p role="alert">{error}</p>}<button disabled={busy || isClientPreview} className="button primary">{labels[target]}</button>
    </form>}
    {role !== 'DELIVERY' && job.status !== 'OUT_FOR_DELIVERY' && <a className="text-link" href={`#/staff/orders/${job.id}`}>Open order to cancel it</a>}</article>
}
export function Fulfilment({role}: {role:string}) {
  const jobs = useWorkspaceData<{orders:Job[];drivers:Driver[]}>('/api/staff/fulfilment')
  return <><h1>Order fulfilment</h1><button className="button secondary" onClick={jobs.retry}>Refresh jobs</button>
    {jobs.error ? <p role="alert">{jobs.error}</p> : !jobs.data ? <p>Loading jobs…</p> : jobs.data.orders.length ? jobs.data.orders.map(job=><JobCard key={job.id+job.status} job={job} drivers={jobs.data!.drivers} role={role} refresh={jobs.retry}/>) : <p>No active handovers.</p>}</>
}
