import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

/**
 * Tutarlı boş/ipucu durumu — liste boşken veya arama beklerken.
 * icon/title verilmezse eski tek satırlık hâliyle render eder (mevcut çağrı
 * yerleri değişmeden çalışır); verilirse ortalanmış zengin yerleşime geçer.
 * tone="dark" gece uçuşu (koyu) yüzeyler içindir.
 */
interface EmptyStateProps {
  children: ReactNode
  icon?: ReactNode
  title?: string
  tone?: 'light' | 'dark'
  className?: string
  /** Kurtarma aksiyonu (§6) — gövdenin altında render edilir (ör. "Filtreleri temizle"). */
  action?: ReactNode
}

export function EmptyState({ children, icon, title, tone = 'light', className, action }: EmptyStateProps) {
  const dark = tone === 'dark'
  const bodyClass = dark ? 'text-muted-foreground' : 'text-muted-foreground'

  if (!icon && !title && !action) {
    return <p className={cn('text-sm', bodyClass, className)}>{children}</p>
  }

  return (
    <div className={cn('flex flex-col items-center gap-2 py-10 text-center', className)}>
      {icon && (
        <div
          aria-hidden="true"
          className={cn(
            'mb-1 flex h-12 w-12 items-center justify-center rounded-full border',
            dark ? 'border-border bg-muted text-muted-foreground' : 'bg-muted/60 text-muted-foreground',
          )}
        >
          {icon}
        </div>
      )}
      {title && (
        <p className={cn('font-semibold', dark ? 'text-foreground' : 'text-foreground')}>{title}</p>
      )}
      <p className={cn('max-w-sm text-sm', bodyClass)}>{children}</p>
      {action && <div className="mt-2">{action}</div>}
    </div>
  )
}
