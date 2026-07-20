import { useState, type ReactNode } from 'react'
import { ImageOff, MapPin, Sparkles } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { AnimatedPrice } from '@/components/ui/animated-price'
import { StarRating } from '@/components/ui/star-rating'
import { useSelectProduct } from '@/features/reservation/useSelectProduct'
import { buildHotelDraft } from '@/features/reservation/buildDraft'
import { formatPrice } from '@/utils/format'
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
 * Düz (flat) yüzey: dolu kart + kenarlık + yumuşak gölge; hover'da hafif yükselir
 * (translateY, scale değil — görsel bulanıklaşmasın). Puan amber `StarRating` ile
 * (detay sayfasıyla tek dil, §4). Otel görseli TourVisio'dan gelir (thumbnailFull);
 * yoksa/yüklenmezse placeholder. Fiyat/uygunluk backend'den geldiği gibi gösterilir.
 * Seç, ürünü taslağa yazıp kontrollü rezervasyon formuna yönlendirir; müsait değilse
 * ya da kriter eksikse devre dışıdır.
 *
 * `compact`: dar sonuç panelinde (madde 8) kullanılan sıkı yatay düzen —
 * küçük küçük resim + tek satır bilgi; genişlikten bağımsız kompakt kalır.
 */

/** Kartta en fazla gösterilecek rozet (§5) — fazlası öncelik sırasına göre kırpılır. */
const MAX_BADGES = 2

/**
 * Konaklama gecesi — kriterdeki checkIn/checkOut farkından; ikisi yoksa `nights`
 * alanından (chat "5 gece" der ama checkOut taşımaz, buildHotelDraft ile simetrik).
 * Türetilemezse null → kart yalnız toplam fiyatı gösterir.
 */
function nightsFromCriteria(criteria?: Partial<HotelSearchCriteria>): number | null {
  if (!criteria) return null
  const { checkIn, checkOut, nights } = criteria
  if (checkIn && checkOut) {
    const diff = Math.round((Date.parse(checkOut) - Date.parse(checkIn)) / 86_400_000)
    return diff > 0 ? diff : null
  }
  return nights && nights > 0 ? nights : null
}

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
  // Anlamsız/çöp board metnini gizle: sadece tanınan pansiyon tipinde rozet göster.
  const boardLabel = boardBadgeLabel(product.boardType)
  const onSelect = () => {
    if (draft) select(draft)
  }
  const canSelect = product.availability && draft !== null

  // Fiyat: product.price konaklama TOPLAMI (backend totalAmount'u bundan doğrular).
  // Gece biliniyorsa gecelik de gösterilir — "toplam mı gecelik mi" belirsizliği kalmasın (§3/§5).
  const nights = nightsFromCriteria(criteria)
  const perNight = nights ? Math.round(product.price / nights) : null

  if (compact) {
    // Rozetler öncelik sırasıyla, en fazla MAX_BADGES: uygunluk (kritik) > en uygun > pansiyon.
    const badges: ReactNode[] = [
      !product.availability && (
        <Badge key="avail" variant="destructive" className="px-1.5 py-0 text-[10px]">
          Müsait değil
        </Badge>
      ),
      recommended && (
        <Badge key="reco" variant="promo" className="gap-1 px-1.5 py-0 text-[10px]">
          <Sparkles className="h-2.5 w-2.5" aria-hidden />
          En uygun
        </Badge>
      ),
      <Badge key="board" variant="secondary" className="px-1.5 py-0 text-[10px]">
        {product.boardType}
      </Badge>,
    ]
      .filter(Boolean)
      .slice(0, MAX_BADGES)

    return (
      // Tüm kart tıklanabilir (fare) — Seç düğmesi klavye/ekran-okuyucu yolu olarak
      // kalır; düğme tıklaması stopPropagation ile kartın onClick'ini ikiye katlamaz.
      <div
        className={cn(
          'glass-card flex items-center gap-3 p-3 transition-all duration-300 hover:border-primary/60 hover:shadow-soft motion-safe:hover:-translate-y-0.5',
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
            width={56}
            height={56}
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
          <p className="mt-0.5 flex items-center gap-1.5 text-[11px] text-foreground/70">
            <MapPin className="h-3 w-3 shrink-0" aria-hidden />
            <span className="truncate">{product.region}</span>
            <StarRating count={product.stars} size={11} className="shrink-0" />
          </p>
          <div className="mt-1 flex flex-wrap items-center gap-1.5">{badges}</div>
        </div>
        <div className="flex shrink-0 flex-col items-end gap-1">
          <div className="text-right">
            <AnimatedPrice
              amount={product.price}
              currency={product.currency}
              className="text-base font-bold text-foreground"
            />
            {perNight !== null && (
              <p className="text-[10px] leading-tight text-foreground/60 tabular-nums">
                {nights} gece · {formatPrice(perNight, product.currency)}/gece
              </p>
            )}
          </div>
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

  // Rozetler öncelik sırasıyla, en fazla MAX_BADGES: uygunluk (kritik) > en uygun > pansiyon.
  const badges: ReactNode[] = [
    !product.availability && (
      <Badge key="avail" variant="destructive">
        Müsait değil
      </Badge>
    ),
    recommended && (
      <Badge key="reco" variant="promo" className="gap-1">
        <Sparkles className="h-3 w-3" aria-hidden />
        En uygun fiyat
      </Badge>
    ),
    <Badge key="board" variant="secondary">
      {product.boardType}
    </Badge>,
  ]
    .filter(Boolean)
    .slice(0, MAX_BADGES)

  return (
    // Tüm kart tıklanabilir (fare); Seç klavye/ekran-okuyucu yolu olarak kalır.
    <div
      className={cn(
        'glass-card flex flex-col gap-3 p-5 transition-all duration-300 hover:border-primary/60 hover:shadow-soft motion-safe:hover:-translate-y-0.5 sm:flex-row sm:items-center sm:justify-between sm:gap-4',
        recommended && 'border-brand-peach/60 ring-1 ring-brand-peach/30',
        canSelect && 'cursor-pointer',
      )}
      onClick={canSelect ? onSelect : undefined}
    >
      <div className="flex min-w-0 flex-col gap-3 sm:flex-row sm:items-center sm:gap-4">
        {showImage ? (
          // Oran 4:3 zorlanır (object-cover) → tüm kartlar aynı yükseklikte, liste zıplamaz (§5);
          // width/height CLS'i sıfırlar (§11). Mobilde geniş banner, sm+'ta 128×96 küçük görsel.
          <img
            src={product.image ?? undefined}
            alt={`${product.hotelName} otel görseli`}
            loading="lazy"
            width={128}
            height={96}
            onError={() => setImageFailed(true)}
            className="h-40 w-full shrink-0 rounded-xl object-cover sm:h-auto sm:w-32 sm:aspect-[4/3]"
          />
        ) : (
          <div
            aria-hidden
            className="flex h-40 w-full shrink-0 items-center justify-center rounded-xl bg-muted text-foreground/30 sm:h-auto sm:w-32 sm:aspect-[4/3]"
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
          <StarRating count={product.stars} className="mt-1" />
          <div className="mt-2 flex flex-wrap gap-2">{badges}</div>
        </div>
      </div>
      {/* Mobilde fiyat solda / Seç sağda tek satır; sm+ sağa yaslı sütun. */}
      <div className="flex shrink-0 items-center justify-between gap-2 sm:flex-col sm:items-end">
        <div className="text-right">
          <AnimatedPrice
            amount={product.price}
            currency={product.currency}
            className="text-xl font-bold text-foreground"
          />
          {perNight !== null && (
            <p className="text-[11px] text-foreground/60 tabular-nums">
              {nights} gece · {formatPrice(perNight, product.currency)}/gece
            </p>
          )}
        </div>
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
