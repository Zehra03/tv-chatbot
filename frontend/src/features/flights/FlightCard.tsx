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
 * Gidiş-dönüş kartı iki bacağı da AYRI satır olarak gösterir. Sağlayıcı bu turu
 * tek teklif/tek jetonla sattığı için bilet gerçekten tektir — bacakları ayrı
 * satırlar hâlinde göstermek onu bölmez, yalnız okunur kılar. Fiyat ikisinin
 * toplamı olduğundan tek bacağın yanında tek yön fiyatı gibi okunmasın diye
 * açıkça etiketlenir.
 *
 * `compact`: dar sonuç panelinde (madde 8) kullanılan sıkı düzen — rota tek
 * satır (kalkış → varış), küçük tipografi; genişlikten bağımsız kompakt kalır.
 */
/** Tek bacak: kalkış/varış kodu + şehir + saat, ortada rota çizgisi ve aktarma bilgisi. */
function LegRow({
  label,
  airline,
  from,
  fromCity,
  to,
  toCity,
  departTime,
  arriveTime,
  stops,
  inbound = false,
}: {
  /** "Gidiş"/"Dönüş" — yalnız gidiş-dönüşte; tek yönde etiket yok (kart sadeliği korunur). */
  label?: string
  airline?: string | null
  from: string
  fromCity?: string | null
  to: string
  toCity?: string | null
  departTime: string
  arriveTime?: string | null
  stops: number
  inbound?: boolean
}) {
  return (
    <div>
      {label && (
        <p className="mb-1 flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-white/50">
          <span>{label}</span>
          {airline && (
            <>
              <span aria-hidden>·</span>
              <span className="normal-case text-white/70">{airline}</span>
            </>
          )}
        </p>
      )}
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-2xl font-bold text-white">{from}</p>
          {fromCity && <p className="truncate text-xs font-medium text-white/80">{fromCity}</p>}
          <p className="mt-0.5 text-xs text-white/70">{formatDateTime(departTime)}</p>
        </div>
        <div className="flex min-w-16 flex-1 flex-col items-center px-2">
          <div className="flex w-full items-center gap-1">
            <span className="h-1.5 w-1.5 rounded-full bg-white/60" />
            <span className="h-px flex-1 bg-white/40" />
            {/* Dönüşte uçak geri döner: yön, sırayı okumadan görünür olsun. */}
            <Plane
              className={`h-3.5 w-3.5 shrink-0 text-white/70${inbound ? ' rotate-180' : ''}`}
              aria-hidden
            />
            <span className="h-px flex-1 bg-white/40" />
            <span className="h-1.5 w-1.5 rounded-full bg-white/60" />
          </div>
          <p className="mt-1 text-[11px] text-white/60">
            {stops === 0 ? 'Direkt' : `${stops} aktarma`}
          </p>
        </div>
        <div className="min-w-0 text-right">
          <p className="truncate text-2xl font-bold text-white">{to}</p>
          {toCity && <p className="truncate text-xs font-medium text-white/80">{toCity}</p>}
          {arriveTime && (
            <p className="mt-0.5 text-xs text-white/70">{formatDateTime(arriveTime)}</p>
          )}
        </div>
      </div>
    </div>
  )
}

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
  // Dönüş bacağının VARLIĞI turu gidiş-dönüş yapar, tripType etiketi değil: dönüşsüz bir ürüne
  // "gidiş-dönüş" dendiğinde kart gösteremediği bir dönüşü vaat etmiş olur (backend de aynı kuralı
  // uygular — FlightProductApiDto).
  const isRoundTrip = product.tripType === 'round_trip' && !!product.returnDepartTime

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
        {isRoundTrip && product.returnDepartTime && (
          // Dar panelde de dönüş görünmeli: gidişi tek yön sanıp seçmesin.
          <div className="mt-1.5 flex items-center gap-2 text-sm font-semibold text-white/80">
            <span className="truncate">{product.destination}</span>
            <span className="flex flex-1 items-center gap-1 text-white/40">
              <span className="h-px flex-1 bg-white/30" />
              <Plane className="h-3 w-3 shrink-0 rotate-180" aria-hidden />
              <span className="h-px flex-1 bg-white/30" />
            </span>
            <span className="truncate text-right">{product.origin}</span>
          </div>
        )}
        <div className="mt-2 flex items-center justify-between gap-2">
          <div className="flex flex-wrap items-center gap-1.5 text-[11px] text-white/70">
            <span>{formatDateTime(product.departTime)}</span>
            <span aria-hidden>·</span>
            <span>{stopsLabel}</span>
            {isRoundTrip && product.returnDepartTime && (
              <>
                <span aria-hidden>·</span>
                <span>Dönüş: {formatDateTime(product.returnDepartTime)}</span>
              </>
            )}
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
        <div className="shrink-0 text-right">
          <AnimatedPrice
            amount={product.price}
            currency={product.currency}
            className="text-xl font-bold text-white"
          />
          {isRoundTrip && (
            // Fiyat iki bacağın toplamı; gidiş satırının yanında tek yön gibi okunmamalı.
            <p className="text-[11px] text-white/60">gidiş-dönüş toplamı</p>
          )}
        </div>
      </div>

      {/* Rota çizgisi — login vitrin kartındaki desen. Gidiş-dönüşte her bacak kendi satırında. */}
      <div className="mt-4 space-y-4">
        <LegRow
          label={isRoundTrip ? 'Gidiş' : undefined}
          airline={product.airline}
          from={product.origin}
          fromCity={product.originCity}
          to={product.destination}
          toCity={product.destinationCity}
          departTime={product.departTime}
          arriveTime={product.arriveTime}
          stops={product.stops}
        />
        {isRoundTrip && product.returnDepartTime && (
          <LegRow
            label="Dönüş"
            // Dönüşü başka bir havayolu uçurabilir; başlıktaki havayolu gidişinkidir.
            airline={product.returnAirline ?? product.airline}
            from={product.destination}
            fromCity={product.destinationCity}
            to={product.origin}
            toCity={product.originCity}
            departTime={product.returnDepartTime}
            arriveTime={product.returnArriveTime}
            stops={product.returnStops ?? 0}
            inbound
          />
        )}
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
