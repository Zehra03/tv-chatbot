import { Link } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
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
  const { data, isError, isFetching, error, refetch } = useReservations()

  return (
    // Kardeş sayfalar (detay/form/profil) gibi ortalı — tablo uçtan uca yayılmaz.
    <div className="mx-auto max-w-5xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Rezervasyonlarım</h1>
        <p className="text-sm text-muted-foreground">Geçmiş ve bekleyen rezervasyonlarınız.</p>
      </div>

      {isFetching && !data && <LoadingState label="Yükleniyor…" />}

      {isError && !isFetching && (
        <ErrorState message={error.message} onRetry={() => refetch()} />
      )}

      {data && data.length === 0 && (
        <EmptyState>
          Henüz rezervasyonunuz yok —{' '}
          <Link to="/chat" className="text-primary underline-offset-2 hover:underline">
            sohbetten arama yaparak
          </Link>{' '}
          başlayabilirsiniz.
        </EmptyState>
      )}

      {data && data.length > 0 && (
        <div className="overflow-x-auto rounded-lg border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-muted/50 text-left">
                <th scope="col" className="p-3 font-semibold">No</th>
                <th scope="col" className="p-3 font-semibold">Tip</th>
                <th scope="col" className="p-3 font-semibold">Tarih</th>
                <th scope="col" className="p-3 font-semibold">Misafir</th>
                <th scope="col" className="p-3 font-semibold">Toplam</th>
                <th scope="col" className="p-3 font-semibold">Durum</th>
                {/* relative: sr-only (absolute) hücre içinde kalsın, dokümanı genişletmesin. */}
                <th scope="col" className="relative p-3">
                  <span className="sr-only">İşlemler</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {data.map((r) => (
                <tr
                  key={r.id}
                  className="border-b transition-colors even:bg-muted/20 last:border-0 hover:bg-muted/40"
                >
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
                    <Button asChild size="sm" variant="outline">
                      <Link to={`/reservations/${r.id}`}>Detay</Link>
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
