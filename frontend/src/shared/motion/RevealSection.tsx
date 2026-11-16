import type { ReactNode } from 'react'
import { motion } from 'motion/react'
import { revealProps, revealTransition } from './reveal'

/** A page section that reveals itself on scroll. */
export function RevealSection({ className, children }: { className?: string; children: ReactNode }) {
  return <motion.section className={className} {...revealProps} transition={revealTransition()}>{children}</motion.section>
}
