import type { ReactNode } from 'react'
import { isClientPreview } from '../shared/preview'
export function PreviewFields({ children }: { children: ReactNode }) {
  return isClientPreview ? <fieldset className="preview-disabled-fields" disabled>{children}</fieldset> : <>{children}</>
}
