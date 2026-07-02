import { MapPin, Star } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { AnimatedPrice } from '@/components/ui/animated-price'
import { useSelectProduct } from '@/features/reservation/useSelectProduct'
import type { HotelProduct } from '@/types'

/**
 * Otel sonuç kartı — hem /hotels listesinde hem chat thread'inde kullanılır.
 * Görsel dil login vitrinindeki otel kartından: cam yüzey, teal yıldız,
 * hover'da teal kenar + yükselme. Fiyat/uygunluk backend'den geldiği gibi
 * gösterilir. Seç, ürünü taslağa yazıp kontrollü rezervasyon formuna
 * yönlendirir; müsait değilse devre dışıdır.
 */
export function HotelCard({ product }: { product: HotelProduct }) {
  const select = useSelectProduct()
  return (
    <div className="glass-card flex items-center justify-between gap-4 p-5 transition-all duration-300 hover:-translate-y-0.5 hover:border-brand-teal/60 hover:shadow-[0_8px_30px_theme(colors.brand.teal/15%)]">
      <div className="min-w-0">
        <p className="flex items-center gap-1 text-xs text-white/70">
          <MapPin className="h-3 w-3 shrink-0" aria-hidden />
          {product.region}
        </p>
        <p className="mt-0.5 truncate text-lg font-semibold text-white">{product.hotelName}</p>
        <p className="mt-1 flex items-center gap-1 text-xs text-white/70">
          <Star className="h-3.5 w-3.5 fill-brand-teal text-brand-teal" aria-hidden />
          {product.stars}
          <span className="sr-only"> yıldız</span>
        </p>
        <div className="mt-2 flex flex-wrap gap-2">
          <Badge variant="glass">{product.boardType}</Badge>
          {!product.availability && <Badge variant="destructive">Müsait değil</Badge>}
        </div>
      </div>
      <div className="flex shrink-0 flex-col items-end gap-2">
        <AnimatedPrice
          amount={product.price}
          currency={product.currency}
          className="text-xl font-bold text-white"
        />
        <Button
          size="sm"
          disabled={!product.availability}
          className="rounded-full bg-gradient-to-r from-brand-blue to-brand-teal px-5 text-white shadow-[0_2px_12px_theme(colors.brand.teal/30%)] transition-shadow hover:shadow-[0_2px_20px_theme(colors.brand.teal/50%)]"
          aria-label={`${product.hotelName} otelini seç`}
          onClick={() =>
            select({
              productType: 'hotel',
              productId: product.id,
              title: product.hotelName,
              summary: `${product.region} · ${product.stars}★ · ${product.boardType}`,
              price: product.price,
              currency: product.currency,
            })
          }
        >
          Seç
        </Button>
      </div>
    </div>
  )
}
