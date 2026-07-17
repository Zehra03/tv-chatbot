import { Star } from 'lucide-react'
import { cn } from '@/lib/utils'

interface StarRatingProps {
  /** Dolu yıldız sayısı (otel sınıfı / puan). */
  count: number
  /** Toplam yıldız (varsayılan 5). */
  max?: number
  className?: string
  /** Yıldız kenar uzunluğu (px). */
  size?: number
}

/**
 * Yıldız derecelendirme — `count` dolu + kalan boş yıldız. Renk amber (rating
 * konvansiyonu, brand.warning); bilgi renge bağlı DEĞİL: erişilebilir etiket
 * sayıyı taşır (role="img" + aria-label). Otel detayı ve sonuç kartlarında ortak.
 */
export function StarRating({ count, max = 5, className, size = 14 }: StarRatingProps) {
  const filled = Math.max(0, Math.min(max, Math.round(count)))
  return (
    <span
      role="img"
      aria-label={`${filled} yıldız`}
      className={cn('inline-flex items-center gap-0.5', className)}
    >
      {Array.from({ length: max }).map((_, i) => (
        <Star
          key={i}
          aria-hidden
          style={{ width: size, height: size }}
          className={cn(
            'shrink-0',
            i < filled ? 'fill-warning text-warning' : 'fill-transparent text-muted-foreground/40',
          )}
        />
      ))}
    </span>
  )
}

export default StarRating
