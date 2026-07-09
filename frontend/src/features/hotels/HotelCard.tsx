import { useState } from 'react'
import { ImageOff, MapPin, Star } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { AnimatedPrice } from '@/components/ui/animated-price'
import { TiltedCard } from '@/components/TiltedCard'
import { useSelectProduct } from '@/features/reservation/useSelectProduct'
import type { HotelProduct } from '@/types'

/**
 * Otel sonuç kartı — hem /hotels listesinde hem chat thread'inde kullanılır.
 * Görsel dil login vitrinindeki otel kartından: cam yüzey, teal yıldız,
 * hover'da teal kenar + 3B eğim (TiltedCard). Otel görseli TourVisio'dan gelir
 * (thumbnailFull); yoksa/yüklenmezse placeholder. Fiyat/uygunluk backend'den
 * geldiği gibi gösterilir. Seç, ürünü taslağa yazıp kontrollü rezervasyon
 * formuna yönlendirir; müsait değilse devre dışıdır.
 */
export function HotelCard({ product }: { product: HotelProduct }) {
  const select = useSelectProduct()
  const [imageFailed, setImageFailed] = useState(false)
  const showImage = Boolean(product.image) && !imageFailed
  return (
    <TiltedCard>
      <div className="glass-card flex flex-col gap-3 p-5 transition-all duration-300 hover:border-brand-teal/60 hover:shadow-[0_8px_30px_theme(colors.brand.teal/15%)] sm:flex-row sm:items-center sm:justify-between sm:gap-4">
        <div className="flex min-w-0 flex-col gap-3 sm:flex-row sm:items-center sm:gap-4">
          {showImage ? (
            <img
              src={product.image ?? undefined}
              alt={`${product.hotelName} otel görseli`}
              loading="lazy"
              onError={() => setImageFailed(true)}
              className="h-40 w-full shrink-0 rounded-xl object-cover sm:h-24 sm:w-32"
            />
          ) : (
            <div
              aria-hidden
              className="flex h-40 w-full shrink-0 items-center justify-center rounded-xl bg-white/5 text-white/30 sm:h-24 sm:w-32"
            >
              <ImageOff className="h-6 w-6" />
            </div>
          )}
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
        </div>
        {/* Mobilde fiyat solda / Seç sağda tek satır; sm+ sağa yaslı sütun. */}
        <div className="flex shrink-0 items-center justify-between gap-2 sm:flex-col sm:items-end">
          <AnimatedPrice
            amount={product.price}
            currency={product.currency}
            className="text-xl font-bold text-white"
          />
          <Button
            size="sm"
            disabled={!product.availability}
            className="rounded-full px-5"
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
    </TiltedCard>
  )
}
