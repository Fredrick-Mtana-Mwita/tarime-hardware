import { Component, lazy, Suspense } from 'react'
import type { ReactNode } from 'react'

// Each page is its own download, so Three.js never loads for shoppers who only browse and check out.
const ExperienceHub = lazy(() => import('./hub/ExperienceHub').then(module => ({ default: module.ExperienceHub })))
const ShowroomPage = lazy(() => import('./showroom/ShowroomPage').then(module => ({ default: module.ShowroomPage })))
const RoomPage = lazy(() => import('./room-visualizer/RoomPage').then(module => ({ default: module.RoomPage })))
const ConfiguratorPage = lazy(() => import('./configurator/ConfiguratorPage').then(module => ({ default: module.ConfiguratorPage })))

class LoadBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }
  static getDerivedStateFromError() { return { failed: true } }
  render() {
    return this.state.failed ? <div className="container load-state" role="alert"><p>The 3D experience could not be loaded. Check your connection and try again.</p>
      <button type="button" className="button primary" onClick={() => window.location.reload()}>Try again</button> <a className="button secondary" href="#/shop">Browse products</a></div> : this.props.children
  }
}

export function ExperienceRoute({ path, params }: { path: string; params: URLSearchParams }) {
  const page = path === '/3d' ? <ExperienceHub /> : path === '/3d/showroom' ? <ShowroomPage /> : path === '/3d/room' ? <RoomPage />
    : path === '/3d/configure' ? <ConfiguratorPage params={params} /> : path.startsWith('/3d/configure/') ? <ConfiguratorPage id={path.split('/')[3]} params={params} /> : null
  if (!page) return <div className="container load-state"><h1>Page not found</h1><a className="button primary" href="#/3d">Open the 3D experience</a></div>
  return <LoadBoundary key={path}><Suspense fallback={<div className="container load-state" role="status"><p>Loading the 3D experience…</p></div>}>{page}</Suspense></LoadBoundary>
}
