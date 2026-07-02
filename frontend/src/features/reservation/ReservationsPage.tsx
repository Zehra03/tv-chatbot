import { Link, useNavigate } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { useReservations } from '@/features/reservation/useReservations'
import {
  RESERVATION_STATUS_LABELS,
  reservationStatusVariant,
} from '@/features/reservation/status'
import { formatDate, formatPrice } from '@/utils/format'

/**
 * /reservations — rezervasyon listesi (docs/frontend-architecture.md §3):
 * no, tip, tarih, misafir, toplam, durum sütunları + detay butonu.
 * Veri React Query'den gelir; oluşturma sonrası invalidation ile tazelenir.
 */
export function ReservationsPage() {
  const { data, isLoading, isError, error, refetch } = useReservations()
  const navigate = useNavigate()

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Rezervasyonlarım</h1>
        <p className="text-sm text-muted-foreground">Geçmiş ve bekleyen rezervasyonlarınız.</p>
      </div>

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

      {data && data.length === 0 && (
        <p className="text-sm text-muted-foreground">
          Henüz rezervasyonunuz yok —{' '}
          <Link to="/chat" className="text-primary underline-offset-2 hover:underline">
            sohbetten arama yaparak
          </Link>{' '}
          başlayabilirsiniz.
        </p>
      )}

      {data && data.length > 0 && (
        <div className="overflow-x-auto rounded-lg border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-muted/50 text-left">
                <th className="p-3 font-semibold">No</th>
                <th className="p-3 font-semibold">Tip</th>
                <th className="p-3 font-semibold">Tarih</th>
                <th className="p-3 font-semibold">Misafir</th>
                <th className="p-3 font-semibold">Toplam</th>
                <th className="p-3 font-semibold">Durum</th>
                <th className="p-3" />
              </tr>
            </thead>
            <tbody>
              {data.map((r) => (
                <tr key={r.id} className="border-b last:border-0">
                  <td className="p-3 font-mono font-medium">{r.reservationNumber}</td>
                  <td className="p-3">{r.productType === 'hotel' ? 'Otel' : 'Uçuş'}</td>
                  <td className="p-3">{formatDate(r.reservationDate)}</td>
                  <td className="p-3">{r.leadGuestName ?? '—'}</td>
                  <td className="p-3">{formatPrice(r.totalAmount, r.currency)}</td>
                  <td className="p-3">
                    <Badge variant={reservationStatusVariant(r.status)}>
                      {RESERVATION_STATUS_LABELS[r.status]}
                    </Badge>
                  </td>
                  <td className="p-3 text-right">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => navigate(`/reservations/${r.id}`)}
                    >
                      Detay
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
