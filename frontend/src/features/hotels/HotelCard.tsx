import { useState } from 'react'
import { ImageOff, MapPin, Sparkles, Star } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { AnimatedPrice } from '@/components/ui/animated-price'
import { useSelectProduct } from '@/features/reservation/useSelectProduct'
import { buildHotelDraft } from '@/features/reservation/buildDraft'
import { cn } from '@/lib/utils'
import type { HotelProduct, HotelSearchCriteria } from '@/types'

/**
 * Otel sonuç kartı — hem /hotels listesinde hem chat sonuç panelinde kullanılır.
 * Görsel dil login vitrinindeki otel kartından: cam yüzey, teal yıldız,
 * hover'da teal kenar. Otel görseli TourVisio'dan gelir (thumbnailFull);
 * yoksa/yüklenmezse placeholder. Fiyat/uygunluk backend'den geldiği gibi
 * gösterilir. Seç, ürünü taslağa yazıp kontrollü rezervasyon formuna
 * yönlendirir; müsait değilse ya da kriter eksikse devre dışıdır.
 *
 * `compact`: dar sonuç panelinde (madde 8) kullanılan sıkı yatay düzen —
 * küçük küçük resim + tek satır bilgi; genişlikten bağımsız kompakt kalır.
 */
export function HotelCard({
  product,
  criteria,
  compact = false,
  recommended = false,
}: {
  product: HotelProduct
  /** Arama kriteri — otel snapshot'ının check-in/out, oda/kişi alanlarını doldurur (booking için şart). */
  criteria?: Partial<HotelSearchCriteria>
  compact?: boolean
  /** Listedeki en uygun fiyat (müsait olanlar arasında) — peach vurgu + "En uygun" rozeti. */
  recommended?: boolean
}) {
  const select = useSelectProduct()
  const [imageFailed, setImageFailed] = useState(false)
  const showImage = Boolean(product.image) && !imageFailed

  // Taslak ürün + kriterden kurulur; kriter eksikse null → "Seç" kapalı (eksik zorunlu alanla
  // booking başlatılamaz). Her iki düzen de aynı taslağı yazar — chatbot booking yapmaz.
  const draft = buildHotelDraft(product, criteria)
  const onSelect = () => {
    if (draft) select(draft)
  }
  const canSelect = product.availability && draft !== null

  if (compact) {
    return (
      // Tüm kart tıklanabilir (fare) — Seç düğmesi klavye/ekran-okuyucu yolu olarak
      // kalır; düğme tıklaması stopPropagation ile kartın onClick'ini ikiye katlamaz.
      <div
        className={cn(
          'glass-card flex items-center gap-3 p-3 transition-all duration-300 hover:border-primary/60 hover:shadow-soft',
          recommended && 'border-brand-peach/60 ring-1 ring-brand-peach/30',
          canSelect && 'cursor-pointer',
        )}
        onClick={canSelect ? onSelect : undefined}
      >
        {showImage ? (
          <img
            src={product.image ?? undefined}
            alt={`${product.hotelName} otel görseli`}
            loading="lazy"
            onError={() => setImageFailed(true)}
            className="h-14 w-14 shrink-0 rounded-lg object-cover"
          />
        ) : (
          <div
            aria-hidden
            className="flex h-14 w-14 shrink-0 items-center justify-center rounded-lg bg-muted text-foreground/30"
          >
            <ImageOff className="h-5 w-5" />
          </div>
        )}
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold text-foreground">{product.hotelName}</p>
          <p className="mt-0.5 flex items-center gap-1 text-[11px] text-foreground/70">
            <MapPin className="h-3 w-3 shrink-0" aria-hidden />
            <span className="truncate">{product.region}</span>
            <Star className="ml-1 h-3 w-3 shrink-0 fill-primary text-primary" aria-hidden />
            {product.stars}
            <span className="sr-only"> yıldız</span>
          </p>
          <div className="mt-1 flex flex-wrap items-center gap-1.5">
            {recommended && (
              <Badge variant="promo" className="gap-1 px-1.5 py-0 text-[10px]">
                <Sparkles className="h-2.5 w-2.5" aria-hidden />
                En uygun
              </Badge>
            )}
            <Badge variant="secondary" className="px-1.5 py-0 text-[10px]">
              {product.boardType}
            </Badge>
            {!product.availability && (
              <Badge variant="destructive" className="px-1.5 py-0 text-[10px]">
                Müsait değil
              </Badge>
            )}
          </div>
        </div>
        <div className="flex shrink-0 flex-col items-end gap-1">
          <AnimatedPrice
            amount={product.price}
            currency={product.currency}
            className="text-base font-bold text-foreground"
          />
          <Button
            variant="cta"
            size="sm"
            disabled={!canSelect}
            className="h-7 rounded-full px-3 text-xs"
            aria-label={`${product.hotelName} otelini seç`}
            onClick={(e) => {
              e.stopPropagation()
              onSelect()
            }}
          >
            Seç
          </Button>
        </div>
      </div>
    )
  }

  return (
    // Tüm kart tıklanabilir (fare); Seç klavye/ekran-okuyucu yolu olarak kalır.
    <div
      className={cn(
        'glass-card flex flex-col gap-3 p-5 transition-all duration-300 hover:border-primary/60 hover:shadow-soft sm:flex-row sm:items-center sm:justify-between sm:gap-4',
        recommended && 'border-brand-peach/60 ring-1 ring-brand-peach/30',
        canSelect && 'cursor-pointer',
      )}
      onClick={canSelect ? onSelect : undefined}
    >
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
            className="flex h-40 w-full shrink-0 items-center justify-center rounded-xl bg-muted text-foreground/30 sm:h-24 sm:w-32"
          >
            <ImageOff className="h-6 w-6" />
          </div>
        )}
        <div className="min-w-0">
          <p className="flex items-center gap-1 text-xs text-foreground/70">
            <MapPin className="h-3 w-3 shrink-0" aria-hidden />
            {product.region}
          </p>
          <p className="mt-0.5 truncate text-lg font-semibold text-foreground">{product.hotelName}</p>
          <p className="mt-1 flex items-center gap-1 text-xs text-foreground/70">
            <Star className="h-3.5 w-3.5 fill-primary text-primary" aria-hidden />
            {product.stars}
            <span className="sr-only"> yıldız</span>
          </p>
          <div className="mt-2 flex flex-wrap gap-2">
            {recommended && (
              <Badge variant="promo" className="gap-1">
                <Sparkles className="h-3 w-3" aria-hidden />
                En uygun fiyat
              </Badge>
            )}
            <Badge variant="secondary">{product.boardType}</Badge>
            {!product.availability && <Badge variant="destructive">Müsait değil</Badge>}
          </div>
        </div>
      </div>
      {/* Mobilde fiyat solda / Seç sağda tek satır; sm+ sağa yaslı sütun. */}
      <div className="flex shrink-0 items-center justify-between gap-2 sm:flex-col sm:items-end">
        <AnimatedPrice
          amount={product.price}
          currency={product.currency}
          className="text-xl font-bold text-foreground"
        />
        <Button
          variant="cta"
          size="sm"
          disabled={!canSelect}
          className="rounded-full px-5"
          aria-label={`${product.hotelName} otelini seç`}
          onClick={(e) => {
            e.stopPropagation()
            onSelect()
          }}
        >
          Seç
        </Button>
      </div>
    </div>
  )
}
