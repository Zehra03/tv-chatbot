import type { ReactNode } from 'react'

/** Tutarlı boş/ipucu durumu — liste boşken veya arama beklerken. */
export function EmptyState({ children }: { children: ReactNode }) {
  return <p className="text-sm text-muted-foreground">{children}</p>
}
