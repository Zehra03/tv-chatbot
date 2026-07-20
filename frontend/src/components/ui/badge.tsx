import type { HTMLAttributes } from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'

const badgeVariants = cva(
  'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors',
  {
    variants: {
      variant: {
        default: 'border-transparent bg-primary text-primary-foreground',
        secondary: 'border-transparent bg-secondary text-secondary-foreground',
        destructive: 'border-transparent bg-destructive text-destructive-foreground',
        outline: 'text-foreground',
        // Durum rozetleri (rezervasyon/sonuç) — hue tint + okunur foreground yazı
        // (AA: #22C55E/#F59E0B yazı olarak açık zeminde geçmez; renk tek gösterge değil,
        // metin etiketi taşır).
        success: 'border-transparent bg-success/15 text-foreground',
        warning: 'border-transparent bg-warning/15 text-foreground',
        // Yumuşak turuncu (peach) vurgu — "en uygun/önerilen" kart etiketi (palette
        // Soft Accent). Lacivert yazı: peach beyaz yazıyla AA geçmez, lacivert geçer.
        promo: 'border-transparent bg-brand-peach text-brand-navy',
        // Düz nötr / mavi-vurgulu chip (eski cam varyantların düz karşılığı).
        glass: 'border-border bg-secondary text-secondary-foreground',
        glassAccent: 'border-primary/30 bg-primary/10 text-primary',
      },
    },
    defaultVariants: { variant: 'default' },
  },
)

export interface BadgeProps
  extends HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <div className={cn(badgeVariants({ variant }), className)} {...props} />
}

export { badgeVariants }
