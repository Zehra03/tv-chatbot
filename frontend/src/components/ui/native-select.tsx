import * as React from 'react'
import { cn } from '@/lib/utils'

/**
 * Tasarım sistemine uyumlu yerel <select> — filtre çubukları gibi basit
 * kullanımlar için (Radix Select bağımlılığına gerek bırakmaz). Input ile
 * aynı yükseklik/kenarlık dilini kullanır.
 */
const NativeSelect = React.forwardRef<HTMLSelectElement, React.ComponentProps<'select'>>(
  ({ className, ...props }, ref) => (
    <select
      ref={ref}
      className={cn(
        'h-9 rounded-md border border-input bg-background px-3 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50',
        className,
      )}
      {...props}
    />
  ),
)
NativeSelect.displayName = 'NativeSelect'

export { NativeSelect }
