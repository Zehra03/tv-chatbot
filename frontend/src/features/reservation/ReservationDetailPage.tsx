import { useMemo, useState } from 'react'
import { apiErrorMessage } from '@/lib/apiErrorMessage'
import { Link, useLocation, useParams } from 'react-router-dom'
import {
  ArrowLeft,
  BedDouble,
  CheckCircle2,
  FileDown,
  Globe,
  Hotel,
  MapPin,
  Plane,
  Users,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { DropdownSelect } from '@/components/ui/dropdown-select'
import { StarRating } from '@/components/ui/star-rating'
import { Spinner } from '@/components/ui/spinner'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { useReservation } from '@/features/reservation/useReservation'
import { useCancelReservation } from '@/features/reservation/useCancelReservation'
import {
  RESERVATION_PRODUCT_TYPE_LABELS,
  RESERVATION_STATUS_LABELS,
  reservationStatusVariant,
} from '@/features/reservation/status'
import type { CancellationOption, ReservationDetail } from '@/types'
import { formatDate, formatDateTime, formatPrice } from '@/utils/format'

/** Etiket/değer satırı — detay ızgarasında tekrar eden desen. */
function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-medium">{children}</dd>
    </div>
  )
}

/** Küçük bilgi çipi — ikon + değer (doluluk/uyruk gibi tekil olgular). */
function InfoChip({ icon: Icon, children }: { icon: typeof Hotel; children: React.ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-card px-2.5 py-1 text-xs font-medium text-foreground">
      <Icon className="h-3.5 w-3.5 text-muted-foreground" aria-hidden />
      {children}
    </span>
  )
}

/** Pansiyon kodu → okunur etiket (bilinmeyen kod ham gösterilir; veri uydurulmaz). */
const BOARD_TYPE_LABELS: Record<string, string> = {
  RO: 'Sadece oda',
  BB: 'Oda + kahvaltı',
  HB: 'Yarım pansiyon',
  FB: 'Tam pansiyon',
  AI: 'Her şey dahil',
  UAI: 'Ultra her şey dahil',
}
function boardLabel(code?: string | null): string | null {
  if (!code) return null
  return BOARD_TYPE_LABELS[code.toUpperCase()] ?? code
}

/** İki tarih arası gece sayısı (checkOut − checkIn); en az 1. */
function nightsBetween(checkIn: string, checkOut: string): number {
  const ms = new Date(checkOut).getTime() - new Date(checkIn).getTime()
  return Number.isFinite(ms) ? Math.max(1, Math.round(ms / 86_400_000)) : 1
}

/** Otel/uçuş snapshot'ı için ortak bölüm kabı — tutarlı görsel dil. */
function ProductSection({
  icon: Icon,
  title,
  children,
}: {
  icon: typeof Hotel
  title: string
  children: React.ReactNode
}) {
  return (
    <section className="space-y-3">
      <h2 className="flex items-center gap-2 font-semibold">
        <Icon className="h-4 w-4 text-primary" aria-hidden /> {title}
      </h2>
      <div className="space-y-4 rounded-xl border border-border bg-muted/40 p-4">{children}</div>
    </section>
  )
}

/** Rezerve edilmiş otel snapshot'ı — zengin detay: sınıf, konaklama süresi, doluluk. */
function HotelBlock({ hotel }: { hotel: NonNullable<ReservationDetail['hotel']> }) {
  const nights = nightsBetween(hotel.checkIn, hotel.checkOut)
  const board = boardLabel(hotel.boardType)
  return (
    <ProductSection icon={Hotel} title="Otel">
      {/* Başlık: ad + yıldız sınıfı + bölge + pansiyon */}
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-base font-semibold text-foreground">{hotel.hotelName}</p>
          <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
            {hotel.stars ? <StarRating count={hotel.stars} /> : null}
            {hotel.region && (
              <span className="inline-flex items-center gap-1">
                <MapPin className="h-3.5 w-3.5" aria-hidden />
                {hotel.region}
              </span>
            )}
          </div>
        </div>
        {board && (
          <Badge variant="secondary" className="shrink-0" title={hotel.boardType ?? undefined}>
            {board}
          </Badge>
        )}
      </div>

      {/* Konaklama şeridi: giriş → gece → çıkış */}
      <div className="flex items-stretch gap-3 rounded-lg border border-border bg-card p-3">
        <div className="min-w-0">
          <p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
            Giriş
          </p>
          <p className="mt-0.5 text-sm font-semibold text-foreground">{formatDate(hotel.checkIn)}</p>
        </div>
        <div className="flex flex-1 flex-col items-center justify-center gap-1 px-2">
          <span className="whitespace-nowrap rounded-full bg-primary/10 px-2 py-0.5 text-[11px] font-semibold text-primary">
            {nights} gece
          </span>
          <span className="h-px w-full bg-border" aria-hidden />
        </div>
        <div className="min-w-0 text-right">
          <p className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
            Çıkış
          </p>
          <p className="mt-0.5 text-sm font-semibold text-foreground">{formatDate(hotel.checkOut)}</p>
        </div>
      </div>

      {/* Doluluk + uyruk + otel fiyatı */}
      <div className="flex flex-wrap items-center gap-2">
        <InfoChip icon={BedDouble}>{hotel.rooms} oda</InfoChip>
        <InfoChip icon={Users}>
          {hotel.adults} yetişkin{hotel.children ? ` · ${hotel.children} çocuk` : ''}
        </InfoChip>
        {hotel.nationality && <InfoChip icon={Globe}>{hotel.nationality}</InfoChip>}
        <span className="ml-auto text-sm">
          <span className="text-muted-foreground">Otel:</span>{' '}
          <span className="font-semibold text-foreground">
            {formatPrice(hotel.price, hotel.currency)}
          </span>
        </span>
      </div>
    </ProductSection>
  )
}

/** Rezerve edilmiş uçuş snapshot'ı. */
function FlightBlock({ flight }: { flight: NonNullable<ReservationDetail['flight']> }) {
  return (
    <ProductSection icon={Plane} title="Uçuş">
      <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
        <Field label="Rota">
          {flight.origin} → {flight.destination}
          {flight.airline ? ` · ${flight.airline}` : ''}
        </Field>
        <Field label="Kalkış">{formatDateTime(flight.departTime)}</Field>
        {flight.arriveTime && <Field label="Varış">{formatDateTime(flight.arriveTime)}</Field>}
        {flight.returnDepartTime && (
          <Field label="Dönüş">{formatDateTime(flight.returnDepartTime)}</Field>
        )}
        <Field label="Aktarma / bagaj">
          {flight.stops === 0 || flight.stops == null ? 'Direkt' : `${flight.stops} aktarma`}
          {flight.baggage ? ` · Bagaj: ${flight.baggage}` : ''}
        </Field>
        <Field label="Yolcu">{flight.passengerCount}</Field>
      </dl>
    </ProductSection>
  )
}

/** İptal seçeneği etiketi — sebep adı + (varsa) ceza tutarı. */
function optionLabel(o: CancellationOption): string {
  const penalty = o.price?.amount ? ` · ceza ${formatPrice(o.price.amount, o.price.currency)}` : ''
  return `${o.reasonName ?? o.reasonId}${penalty}`
}

/**
 * İptal bölümü — yalnız iptal edilebilir seçenek varsa ve durum uygunsa (onaylı/beklemede) görünür.
 * Kullanıcı bir sebep seçer, açık onay kutusunu işaretler ve iptal eder (destructive → çift kontrol).
 * `reason` = seçilen `cancellationOptions[].reasonId`; backend TourVisio'ya iletir.
 */
function CancelSection({ reservation }: { reservation: ReservationDetail }) {
  const cancel = useCancelReservation(reservation.id)
  const options = useMemo(
    () => (reservation.cancellationOptions ?? []).filter((o) => o.cancelable !== false),
    [reservation.cancellationOptions],
  )
  const [reasonId, setReasonId] = useState(options[0]?.reasonId ?? '')
  const [confirmed, setConfirmed] = useState(false)

  const cancellable = reservation.status === 'confirmed' || reservation.status === 'pending'
  if (!cancellable || options.length === 0) return null

  const selected = options.find((o) => o.reasonId === reasonId)

  return (
    <div className="space-y-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4">
      <h2 className="font-semibold text-foreground">Rezervasyonu iptal et</h2>
      <div className="grid gap-1.5">
        <label htmlFor="cancel-reason" className="text-sm text-muted-foreground">
          İptal sebebi
        </label>
        <DropdownSelect
          id="cancel-reason"
          value={reasonId}
          options={options.map((o) => ({ value: o.reasonId, label: optionLabel(o) }))}
          onChange={setReasonId}
        />
      </div>
      {selected?.price?.amount ? (
        <p className="text-sm text-muted-foreground">
          İptal cezası:{' '}
          <span className="font-semibold text-foreground">
            {formatPrice(selected.price.amount, selected.price.currency)}
          </span>
        </p>
      ) : null}

      <label className="flex items-start gap-3 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-foreground">
        <input
          type="checkbox"
          checked={confirmed}
          onChange={(e) => setConfirmed(e.target.checked)}
          className="mt-0.5 h-5 w-5 rounded border-border accent-destructive"
        />
        Bu rezervasyonu iptal etmek istediğimi onaylıyorum.
      </label>

      {cancel.isError && (
        <p role="alert" className="text-sm text-destructive-emphasis">
          {apiErrorMessage(cancel.error)}
        </p>
      )}

      <Button
        variant="destructive"
        disabled={!confirmed || !reasonId || cancel.isPending}
        onClick={() => cancel.mutate({ reason: reasonId })}
      >
        {cancel.isPending ? (
          <>
            <Spinner size={16} decorative className="text-foreground" />
            İptal ediliyor…
          </>
        ) : (
          'Rezervasyonu iptal et'
        )}
      </Button>
    </div>
  )
}

/**
 * /reservations/:id — seçilen rezervasyonun tam dökümü (docs/frontend-architecture.md §3):
 * başlık + durum, ürün/tutar bilgileri, otel/uçuş snapshot'ı, misafir listesi ve (uygunsa)
 * iptal bölümü (canlı TourVisio iptal seçenekleriyle).
 */
export function ReservationDetailPage() {
  const { id } = useParams()
  const location = useLocation()
  const { data, isError, isFetching, error, refetch } = useReservation(id)

  // Kesin onaydan hemen sonra rezervasyon formu buraya `state.justBooked` ile yönlendirir;
  // tek seferlik "alındı" bandını gösteririz (sayfa yenilenince navigasyon state'i düştüğünden kaybolur).
  const justBooked = (location.state as { justBooked?: boolean } | null)?.justBooked === true

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex items-center justify-between gap-3">
        <Button
          asChild
          variant="ghost"
          size="sm"
          className="-ml-2 text-muted-foreground hover:bg-muted hover:text-foreground"
        >
          <Link to="/reservations">
            <ArrowLeft className="h-4 w-4" />
            Rezervasyonlarım
          </Link>
        </Button>

        {/* Yazdırma voucher'ı ayrı bir rota (Layout'suz, kâğıt düzeni); açılınca
            kendini yazdırır. Durumdan bağımsız gösterilir — iptal/başarısız kayıt
            da arşivlenebilmeli, voucher durumu rozetiyle basar. */}
        {data && (
          <Button
            asChild
            size="sm"
            variant="outline"
            className="border-border bg-muted text-muted-foreground transition-colors hover:border-primary hover:bg-muted hover:text-foreground"
          >
            <Link to={`/reservations/${data.id}/print`}>
              <FileDown className="h-4 w-4" />
              PDF olarak indir
            </Link>
          </Button>
        )}
      </div>

      {justBooked && (
        <div
          role="status"
          className="flex items-start gap-3 rounded-xl border border-primary/30 bg-primary/10 p-4 text-foreground"
        >
          <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-primary" aria-hidden />
          <div>
            <p className="font-semibold">Rezervasyonunuz alındı</p>
            <p className="text-sm text-muted-foreground">
              Rezervasyonunuz onaylandı. Detaylar aşağıda; dilediğinizde Rezervasyonlarım’dan tekrar
              ulaşabilirsiniz.
            </p>
          </div>
        </div>
      )}

      {isFetching && !data && <LoadingState label="Yükleniyor…" />}

      {isError && !isFetching && (
        <ErrorState message={apiErrorMessage(error)} onRetry={() => refetch()} />
      )}

      {data && (
        <Card className="glass-card border-border bg-card text-foreground">
          <CardHeader className="flex-row items-center justify-between space-y-0 gap-3">
            <CardTitle className="min-w-0 break-all font-mono">{data.reservationNumber}</CardTitle>
            <Badge className="shrink-0" variant={reservationStatusVariant(data.status)}>
              {RESERVATION_STATUS_LABELS[data.status]}
            </Badge>
          </CardHeader>
          <CardContent className="space-y-6">
            <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
              <Field label="Ürün tipi">{RESERVATION_PRODUCT_TYPE_LABELS[data.productType]}</Field>
              <Field label="Rezervasyon tarihi">{formatDate(data.reservationDate)}</Field>
              <Field label="Toplam tutar">
                <span className="font-bold">{formatPrice(data.totalAmount, data.currency)}</span>
              </Field>
              {data.externalReservationNumber && (
                <Field label="Sağlayıcı referansı">
                  <span className="font-mono">{data.externalReservationNumber}</span>
                </Field>
              )}
            </dl>

            {data.hotel && <HotelBlock hotel={data.hotel} />}
            {data.flight && <FlightBlock flight={data.flight} />}

            <div>
              <h2 className="mb-2 font-semibold">Misafirler</h2>
              {data.passengers && data.passengers.length > 0 ? (
                <ul className="space-y-2">
                  {data.passengers.map((p, i) => (
                    <li
                      key={`${p.firstName}-${p.lastName}-${i}`}
                      className="rounded-lg border border-border bg-muted p-3 text-sm"
                    >
                      <p className="font-medium">
                        {p.firstName} {p.lastName}
                        <span className="ml-2 font-normal text-muted-foreground">
                          {p.passengerType === 'adult' ? 'Yetişkin' : 'Çocuk'}
                          {p.age != null ? ` · ${p.age} yaş` : ''}
                          {p.nationality ? ` · ${p.nationality}` : ''}
                        </span>
                      </p>
                      {(p.email || p.phone) && (
                        <p className="mt-1 break-words text-muted-foreground">
                          {[p.email, p.phone].filter(Boolean).join(' · ')}
                        </p>
                      )}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-sm text-muted-foreground">Misafir bilgisi bulunmuyor.</p>
              )}
            </div>

            <CancelSection reservation={data} />
          </CardContent>
        </Card>
      )}
    </div>
  )
}
