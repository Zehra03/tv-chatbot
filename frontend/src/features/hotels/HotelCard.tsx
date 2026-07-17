import { useState } from 'react'
import { ImageOff, MapPin, Star } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { AnimatedPrice } from '@/components/ui/animated-price'
import { useSelectProduct } from '@/features/reservation/useSelectProduct'
import { buildHotelDraft } from '@/features/reservation/buildDraft'
import { cn } from '@/lib/utils'
import type { HotelProduct, HotelSearchCriteria } from '@/types'

/**
 * Board (pansiyon) rozetinde gösterilecek temiz etiketi döndürür; TANINMAYAN ham metin için `null`.
 * `boardType` TourVisio'dan serbest metin gelir ve test ortamında çöp olabilir ("lokal name deneme",
 * "low level yerel dil"). Kullanıcıya yalnızca anlamlı, bilinen bir pansiyon tipi gösterilir; tanınmayan
 * değerde rozet hiç basılmaz (kart rozetsiz de geçerli). Bilinen küme backend `BoardNormalizer` ile aynıdır.
 */
export function boardBadgeLabel(boardType?: string | null): string | null {
  if (!boardType) return null
  const b = boardType.trim().toLowerCase()
  const hasToken = (code: string) => b.split(/[^a-z]+/).includes(code)
  if (/all inclusive|all-inclusive|herşey dahil|her şey dahil|hersey dahil|ultra/.test(b) || hasToken('ai'))
    return 'Herşey Dahil'
  if (/full board|tam pansiyon/.test(b) || hasToken('fb')) return 'Tam Pansiyon'
  if (/half board|yarım pansiyon|yarim pansiyon/.test(b) || hasToken('hb')) return 'Yarım Pansiyon'
  if (/breakfast|kahvaltı|kahvalti|bed and breakfast|bed & breakfast|oda kahvaltı/.test(b) || hasToken('bb'))
    return 'Oda Kahvaltı'
  if (/room only|sadece oda/.test(b) || hasToken('ro')) return 'Sadece Oda'
  return null
}

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
}: {
  product: HotelProduct
  /** Arama kriteri — otel snapshot'ının check-in/out, oda/kişi alanlarını doldurur (booking için şart). */
  criteria?: Partial<HotelSearchCriteria>
  compact?: boolean
}) {
  const select = useSelectProduct()
  const [imageFailed, setImageFailed] = useState(false)
  const showImage = Boolean(product.image) && !imageFailed

  // Taslak ürün + kriterden kurulur; kriter eksikse null → "Seç" kapalı (eksik zorunlu alanla
  // booking başlatılamaz). Her iki düzen de aynı taslağı yazar — chatbot booking yapmaz.
  const draft = buildHotelDraft(product, criteria)
  // Anlamsız/çöp board metnini gizle: sadece tanınan pansiyon tipinde rozet göster.
  const boardLabel = boardBadgeLabel(product.boardType)
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
          'glass-card flex items-center gap-3 p-3 transition-all duration-300 hover:border-brand-teal/60',
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
            className="flex h-14 w-14 shrink-0 items-center justify-center rounded-lg bg-white/5 text-white/30"
          >
            <ImageOff className="h-5 w-5" />
          </div>
        )}
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold text-white">{product.hotelName}</p>
          <p className="mt-0.5 flex items-center gap-1 text-[11px] text-white/70">
            <MapPin className="h-3 w-3 shrink-0" aria-hidden />
            <span className="truncate">{product.region}</span>
            <Star className="ml-1 h-3 w-3 shrink-0 fill-brand-teal text-brand-teal" aria-hidden />
            {product.stars}
            <span className="sr-only"> yıldız</span>
          </p>
          <div className="mt-1 flex flex-wrap items-center gap-1.5">
            {boardLabel && (
              <Badge variant="glass" className="px-1.5 py-0 text-[10px]">
                {boardLabel}
              </Badge>
            )}
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
            className="text-base font-bold text-white"
          />
          <Button
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
        'glass-card flex flex-col gap-3 p-5 transition-all duration-300 hover:border-brand-teal/60 hover:shadow-[0_8px_30px_theme(colors.brand.teal/15%)] sm:flex-row sm:items-center sm:justify-between sm:gap-4',
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
            {boardLabel && <Badge variant="glass">{boardLabel}</Badge>}
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
