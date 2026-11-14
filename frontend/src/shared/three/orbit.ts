export interface View { target: [number, number, number]; azimuth: number; polar: number; distance: number }
export interface OrbitLimits { minDistance: number; maxDistance: number; minPolar?: number; maxPolar?: number; minAzimuth?: number; maxAzimuth?: number }
export interface OrbitApi {
  /** Turns the camera around the subject, in radians. */
  rotate(azimuth: number, polar?: number): void
  /** Multiplies the camera distance; values below 1 move closer. */
  zoom(factor: number): void
  goTo(view: View): void
  reset(): void
}
