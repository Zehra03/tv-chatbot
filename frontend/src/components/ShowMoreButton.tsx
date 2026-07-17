import { ChevronDown } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/**
 * "Daha fazla göster (N)" düğmesi — useProgressiveReveal ile eşleşir
 * (madde 9). Koyu (AI/sonuç yüzeyleri) tonda cam çerçeve; kalan öğe sayısını
 * gösterir ki kullanıcı ne kadar daha olduğunu bilsin.
 */
export function ShowMoreButton({
  remaining,
  onClick,
  className,
}: {
  remaining: number
  onClick: () => void
  className?: string
}) {
  return (
    <Button
      type="button"
      variant="outline"
      onClick={onClick}
      className={cn(
        'w-full gap-2 rounded-full border-foreground/15 bg-foreground/5 text-muted-foreground hover:border-brand-teal/50 hover:text-foreground',
        className,
      )}
    >
      <ChevronDown className="h-4 w-4" aria-hidden />
      Daha fazla göster ({remaining})
    </Button>
  )
}
