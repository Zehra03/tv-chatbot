import type { HTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

/**
 * Parıltılı yükleme iskeleti — koyu (gece uçuşu) yüzey için: cam zemin üzerinde
 * soldan sağa süpüren ışık (tailwind `shimmer` keyframe'i). Dekoratif —
 * çağıran yer aria-hidden sarmalayıcı ile kullanmalı; görünür yükleme
 * duyurusunu LoadingState (role="status") yapar.
 */
export function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        'animate-shimmer rounded-2xl border border-border bg-gradient-to-r from-muted via-muted/60 to-muted bg-[length:200%_100%] motion-reduce:animate-none',
        className,
      )}
      {...props}
    />
  )
}

export default Skeleton
