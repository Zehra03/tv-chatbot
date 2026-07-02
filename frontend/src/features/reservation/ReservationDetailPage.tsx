import { Link, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
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
  const { data, isLoading, isError, error, refetch } = useReservation(id)

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <Button asChild variant="ghost" size="sm" className="-ml-2">
        <Link to="/reservations">
          <ArrowLeft className="h-4 w-4" />
          Rezervasyonlarım
        </Link>
      </Button>

      {isLoading && (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Spinner size={16} />
          Yükleniyor…
        </div>
      )}

      {isError && (
        <p role="alert" className="flex items-center gap-3 text-sm text-destructive">
          {error.message}
          <Button type="button" variant="outline" size="sm" onClick={() => refetch()}>
            Tekrar dene
          </Button>
        </p>
      )}

      {data && (
        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <CardTitle className="font-mono">{data.reservationNumber}</CardTitle>
            <Badge variant={reservationStatusVariant(data.status)}>
              {RESERVATION_STATUS_LABELS[data.status]}
            </Badge>
          </CardHeader>
          <CardContent className="space-y-6">
            <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-muted-foreground">Ürün tipi</dt>
                <dd className="font-medium">{data.productType === 'hotel' ? 'Otel' : 'Uçuş'}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Rezervasyon tarihi</dt>
                <dd className="font-medium">{formatDate(data.reservationDate)}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Toplam tutar</dt>
                <dd className="font-bold">{formatPrice(data.totalAmount, data.currency)}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Oluşturulma</dt>
                <dd className="font-medium">{formatDateTime(data.createdAt)}</dd>
              </div>
              {data.updatedAt && (
                <div>
                  <dt className="text-muted-foreground">Güncellenme</dt>
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
                      className="rounded-lg border p-3 text-sm"
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
                        <p className="mt-1 text-muted-foreground">
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
          </CardContent>
        </Card>
      )}
    </div>
  )
}
