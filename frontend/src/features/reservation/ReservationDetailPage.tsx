import { useMemo, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { ArrowLeft, CheckCircle2, FileDown, Hotel, Plane } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { DropdownSelect } from '@/components/ui/dropdown-select'
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
      <dt className="text-brand-ice/70">{label}</dt>
      <dd className="font-medium">{children}</dd>
    </div>
  )
}

/** Rezerve edilmiş otel snapshot'ı. */
function HotelBlock({ hotel }: { hotel: NonNullable<ReservationDetail['hotel']> }) {
  return (
    <div>
      <h2 className="mb-2 flex items-center gap-2 font-semibold">
        <Hotel className="h-4 w-4 text-brand-teal" aria-hidden /> Otel
      </h2>
      <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
        <Field label="Otel">
          {hotel.hotelName}
          {hotel.stars ? ` · ${hotel.stars}★` : ''}
        </Field>
        {hotel.region && <Field label="Bölge">{hotel.region}</Field>}
        {hotel.boardType && <Field label="Pansiyon">{hotel.boardType}</Field>}
        <Field label="Giriş / çıkış">
          {formatDate(hotel.checkIn)} — {formatDate(hotel.checkOut)}
        </Field>
        <Field label="Oda / kişi">
          {hotel.rooms} oda · {hotel.adults} yetişkin
          {hotel.children ? ` · ${hotel.children} çocuk` : ''}
        </Field>
      </dl>
    </div>
  )
}

/** Rezerve edilmiş uçuş snapshot'ı. */
function FlightBlock({ flight }: { flight: NonNullable<ReservationDetail['flight']> }) {
  return (
    <div>
      <h2 className="mb-2 flex items-center gap-2 font-semibold">
        <Plane className="h-4 w-4 text-brand-teal" aria-hidden /> Uçuş
      </h2>
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
    </div>
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
      <h2 className="font-semibold text-white">Rezervasyonu iptal et</h2>
      <div className="grid gap-1.5">
        <label htmlFor="cancel-reason" className="text-sm text-brand-ice/70">
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
        <p className="text-sm text-brand-ice/70">
          İptal cezası:{' '}
          <span className="font-semibold text-white">
            {formatPrice(selected.price.amount, selected.price.currency)}
          </span>
        </p>
      ) : null}

      <label className="flex items-start gap-3 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-white">
        <input
          type="checkbox"
          checked={confirmed}
          onChange={(e) => setConfirmed(e.target.checked)}
          className="mt-0.5 h-5 w-5 rounded border-white/30 accent-destructive"
        />
        Bu rezervasyonu iptal etmek istediğimi onaylıyorum.
      </label>

      {cancel.isError && (
        <p role="alert" className="text-sm text-red-400">
          {cancel.error.message}
        </p>
      )}

      <Button
        variant="destructive"
        disabled={!confirmed || !reasonId || cancel.isPending}
        onClick={() => cancel.mutate({ reason: reasonId })}
      >
        {cancel.isPending ? (
          <>
            <Spinner size={16} decorative className="text-white" />
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
          className="-ml-2 text-brand-ice/80 hover:bg-white/10 hover:text-white"
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
            className="border-white/15 bg-white/5 text-brand-ice transition-colors hover:border-brand-teal hover:bg-white/10 hover:text-white"
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
          className="flex items-start gap-3 rounded-xl border border-brand-teal/30 bg-brand-teal/10 p-4 text-white"
        >
          <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-brand-teal" aria-hidden />
          <div>
            <p className="font-semibold">Rezervasyonunuz alındı</p>
            <p className="text-sm text-brand-ice/70">
              Rezervasyonunuz onaylandı. Detaylar aşağıda; dilediğinizde Rezervasyonlarım’dan tekrar
              ulaşabilirsiniz.
            </p>
          </div>
        </div>
      )}

      {isFetching && !data && <LoadingState label="Yükleniyor…" />}

      {isError && !isFetching && (
        <ErrorState message={error.message} onRetry={() => refetch()} />
      )}

      {data && (
        <Card className="glass-card border-white/15 bg-white/10 text-white">
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
                      className="rounded-lg border border-white/10 bg-white/5 p-3 text-sm"
                    >
                      <p className="font-medium">
                        {p.firstName} {p.lastName}
                        <span className="ml-2 font-normal text-brand-ice/70">
                          {p.passengerType === 'adult' ? 'Yetişkin' : 'Çocuk'}
                          {p.age != null ? ` · ${p.age} yaş` : ''}
                          {p.nationality ? ` · ${p.nationality}` : ''}
                        </span>
                      </p>
                      {(p.email || p.phone) && (
                        <p className="mt-1 break-words text-brand-ice/70">
                          {[p.email, p.phone].filter(Boolean).join(' · ')}
                        </p>
                      )}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-sm text-brand-ice/70">Misafir bilgisi bulunmuyor.</p>
              )}
            </div>

            <CancelSection reservation={data} />
          </CardContent>
        </Card>
      )}
    </div>
  )
}
