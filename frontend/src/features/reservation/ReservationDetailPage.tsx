import { Link, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { TiltedCard } from '@/components/TiltedCard'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { useReservation } from '@/features/reservation/useReservation'
import {
  RESERVATION_STATUS_LABELS,
  reservationStatusVariant,
} from '@/features/reservation/status'
import { formatDate, formatDateTime, formatPrice } from '@/utils/format'

/**
 * /reservations/:id — seçilen rezervasyonun tam dökümü
 * (docs/frontend-architecture.md §3): başlık + durum, ürün/tutar bilgileri,
 * tarihler ve misafir listesi (iletişim dahil).
 */
export function ReservationDetailPage() {
  const { id } = useParams()
  const { data, isError, isFetching, error, refetch } = useReservation(id)

  return (
    <div className="mx-auto max-w-2xl space-y-6">
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

      {isFetching && !data && <LoadingState label="Yükleniyor…" />}

      {isError && !isFetching && (
        <ErrorState message={error.message} onRetry={() => refetch()} />
      )}

      {data && (
        <TiltedCard rotateAmplitude={5} scaleOnHover={1.01}>
        <Card className="glass-card border-white/15 bg-white/10 text-white">
          <CardHeader className="flex-row items-center justify-between space-y-0 gap-3">
            <CardTitle className="min-w-0 break-all font-mono">{data.reservationNumber}</CardTitle>
            <Badge className="shrink-0" variant={reservationStatusVariant(data.status)}>
              {RESERVATION_STATUS_LABELS[data.status]}
            </Badge>
          </CardHeader>
          <CardContent className="space-y-6">
            <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-brand-ice/70">Ürün tipi</dt>
                <dd className="font-medium">{data.productType === 'hotel' ? 'Otel' : 'Uçuş'}</dd>
              </div>
              <div>
                <dt className="text-brand-ice/70">Rezervasyon tarihi</dt>
                <dd className="font-medium">{formatDate(data.reservationDate)}</dd>
              </div>
              <div>
                <dt className="text-brand-ice/70">Toplam tutar</dt>
                <dd className="font-bold">{formatPrice(data.totalAmount, data.currency)}</dd>
              </div>
              <div>
                <dt className="text-brand-ice/70">Oluşturulma</dt>
                <dd className="font-medium">{formatDateTime(data.createdAt)}</dd>
              </div>
              {data.updatedAt && (
                <div>
                  <dt className="text-brand-ice/70">Güncellenme</dt>
                  <dd className="font-medium">{formatDateTime(data.updatedAt)}</dd>
                </div>
              )}
            </dl>

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
          </CardContent>
        </Card>
        </TiltedCard>
      )}
    </div>
  )
}
