import { Plane } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { AnimatedPrice } from '@/components/ui/animated-price'
import { useSelectProduct } from '@/features/reservation/useSelectProduct'
import { buildFlightDraft } from '@/features/reservation/buildDraft'
import { formatDateTime } from '@/utils/format'
import type { FlightProduct, FlightSearchCriteria } from '@/types'

/**
 * Uçuş sonuç kartı — hem /flights listesinde hem chat sonuç panelinde kullanılır.
 * Görsel dil login vitrinindeki uçuş kartından: cam yüzey, noktalı rota
 * çizgisi, hover'da teal kenar. Fiyat backend'den geldiği gibi gösterilir
 * (AnimatedPrice sr-only ikizinde formatPrice). Seç, ürünü taslağa yazıp
 * kontrollü rezervasyon formuna yönlendirir.
 *
 * `compact`: dar sonuç panelinde (madde 8) kullanılan sıkı düzen — rota tek
 * satır (kalkış → varış), küçük tipografi; genişlikten bağımsız kompakt kalır.
 */
export function FlightCard({
  product,
  criteria,
  compact = false,
}: {
  product: FlightProduct
  /** Arama kriteri — uçuş snapshot'ının yolcu sayısını doldurur (yoksa 1). */
  criteria?: Partial<FlightSearchCriteria>
  compact?: boolean
}) {
  const select = useSelectProduct()

  // Uçuşta tüm snapshot alanları üründe var; kriter yalnız yolcu sayısını taşır — booking yok,
  // taslak yazılıp kontrollü forma gidilir.
  const onSelect = () => select(buildFlightDraft(product, criteria))

  const stopsLabel = product.stops === 0 ? 'Direkt' : `${product.stops} aktarma`

  if (compact) {
    return (
      // Tüm kart tıklanabilir (fare); Seç düğmesi klavye/ekran-okuyucu yolu olarak
      // kalır ve stopPropagation ile kartın onClick'ini ikiye katlamaz.
      <div
        className="glass-card cursor-pointer p-3 transition-all duration-300 hover:border-brand-teal/60"
        onClick={onSelect}
      >
        <div className="flex items-center justify-between gap-2">
          <p className="flex min-w-0 items-center gap-1.5 text-xs font-medium text-white/70">
            <Plane className="h-3.5 w-3.5 shrink-0 text-brand-teal" aria-hidden />
            <span className="truncate font-semibold text-white">{product.airline}</span>
          </p>
          <AnimatedPrice
            amount={product.price}
            currency={product.currency}
            className="shrink-0 text-base font-bold text-white"
          />
        </div>
        <div className="mt-2 flex items-center gap-2 text-sm font-semibold text-white">
          <span className="truncate">{product.origin}</span>
          <span className="flex flex-1 items-center gap-1 text-white/40">
            <span className="h-px flex-1 bg-white/30" />
            <Plane className="h-3 w-3 shrink-0" aria-hidden />
            <span className="h-px flex-1 bg-white/30" />
          </span>
          <span className="truncate text-right">{product.destination}</span>
        </div>
        <div className="mt-2 flex items-center justify-between gap-2">
          <div className="flex flex-wrap items-center gap-1.5 text-[11px] text-white/70">
            <span>{formatDateTime(product.departTime)}</span>
            <span aria-hidden>·</span>
            <span>{stopsLabel}</span>
          </div>
          <Button
            size="sm"
            className="h-7 rounded-full px-3 text-xs"
            aria-label={`${product.airline} ${product.origin} ${product.destination} uçuşunu seç`}
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
      className="glass-card cursor-pointer p-5 transition-all duration-300 hover:border-brand-teal/60 hover:shadow-[0_8px_30px_theme(colors.brand.teal/15%)]"
      onClick={onSelect}
    >
      <div className="flex items-center justify-between gap-3">
        <p className="flex min-w-0 items-center gap-2 text-xs font-medium text-white/70">
          <Plane className="h-4 w-4 shrink-0 text-brand-teal" aria-hidden />
          <span className="truncate font-semibold text-white">{product.airline}</span>
        </p>
        <AnimatedPrice
          amount={product.price}
          currency={product.currency}
          className="shrink-0 text-xl font-bold text-white"
        />
      </div>

      {/* Rota çizgisi — login vitrin kartındaki desen. */}
      <div className="mt-4 flex items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-2xl font-bold text-white">{product.origin}</p>
          {product.originCity && (
            <p className="truncate text-xs font-medium text-white/80">{product.originCity}</p>
          )}
          <p className="mt-0.5 text-xs text-white/70">{formatDateTime(product.departTime)}</p>
        </div>
        <div className="flex min-w-16 flex-1 flex-col items-center px-2">
          <div className="flex w-full items-center gap-1">
            <span className="h-1.5 w-1.5 rounded-full bg-white/60" />
            <span className="h-px flex-1 bg-white/40" />
            <Plane className="h-3.5 w-3.5 shrink-0 text-white/70" aria-hidden />
            <span className="h-px flex-1 bg-white/40" />
            <span className="h-1.5 w-1.5 rounded-full bg-white/60" />
          </div>
          <p className="mt-1 text-[11px] text-white/60">{stopsLabel}</p>
        </div>
        <div className="min-w-0 text-right">
          <p className="truncate text-2xl font-bold text-white">{product.destination}</p>
          {product.destinationCity && (
            <p className="truncate text-xs font-medium text-white/80">{product.destinationCity}</p>
          )}
          {product.tripType === 'round_trip' && product.returnDepartTime && (
            <p className="mt-0.5 text-xs text-white/70">
              Dönüş: {formatDateTime(product.returnDepartTime)}
            </p>
          )}
        </div>
      </div>

      <div className="mt-4 flex items-center justify-between gap-3">
        <div className="flex flex-wrap gap-2">
          <Badge variant="glass">Bagaj: {product.baggage}</Badge>
        </div>
        <Button
          size="sm"
          className="rounded-full px-5"
          aria-label={`${product.airline} ${product.origin} ${product.destination} uçuşunu seç`}
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
